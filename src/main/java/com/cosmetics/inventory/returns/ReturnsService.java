package com.cosmetics.inventory.returns;

import com.cosmetics.inventory.customer.CustomerEntity;
import com.cosmetics.inventory.customer.CustomerPaymentEntity;
import com.cosmetics.inventory.customer.CustomerPaymentRepository;
import com.cosmetics.inventory.expense.ExpenseCategoryEntity;
import com.cosmetics.inventory.expense.ExpenseCategoryRepository;
import com.cosmetics.inventory.expense.ExpenseEntity;
import com.cosmetics.inventory.expense.ExpensePaymentMethod;
import com.cosmetics.inventory.expense.ExpenseRepository;
import com.cosmetics.inventory.inventory.InventoryItemEntity;
import com.cosmetics.inventory.inventory.InventoryRepository;
import com.cosmetics.inventory.product.ProductBatchEntity;
import com.cosmetics.inventory.sales.SalesDeductionEntity;
import com.cosmetics.inventory.sales.SalesOrderEntity;
import com.cosmetics.inventory.sales.SalesOrderLineEntity;
import com.cosmetics.inventory.sales.SalesOrderRepository;
import com.cosmetics.inventory.sales.SalesService;
import com.cosmetics.inventory.stockmovement.StockMovementEntity;
import com.cosmetics.inventory.stockmovement.StockMovementRepository;
import com.cosmetics.inventory.stockmovement.StockMovementType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class ReturnsService {
	public static final int RETURN_WINDOW_DAYS = 7;
	public static final String LOSS_CATEGORY_NAME = "Sales Returns Loss";
	public static final String REFUND_DEBT_CREDIT = "DEBT_CREDIT";
	public static final String REFUND_NONE = "NONE";

	private final SalesReturnRepository returnRepository;
	private final SalesReturnLineRepository returnLineRepository;
	private final SalesOrderRepository salesOrderRepository;
	private final InventoryRepository inventoryRepository;
	private final StockMovementRepository stockMovementRepository;
	private final ExpenseRepository expenseRepository;
	private final ExpenseCategoryRepository expenseCategoryRepository;
	private final CustomerPaymentRepository customerPaymentRepository;
	private final SalesService salesService;

	public ReturnsService(SalesReturnRepository returnRepository, SalesReturnLineRepository returnLineRepository,
			SalesOrderRepository salesOrderRepository, InventoryRepository inventoryRepository,
			StockMovementRepository stockMovementRepository, ExpenseRepository expenseRepository,
			ExpenseCategoryRepository expenseCategoryRepository, CustomerPaymentRepository customerPaymentRepository,
			SalesService salesService) {
		this.returnRepository = returnRepository;
		this.returnLineRepository = returnLineRepository;
		this.salesOrderRepository = salesOrderRepository;
		this.inventoryRepository = inventoryRepository;
		this.stockMovementRepository = stockMovementRepository;
		this.expenseRepository = expenseRepository;
		this.expenseCategoryRepository = expenseCategoryRepository;
		this.customerPaymentRepository = customerPaymentRepository;
		this.salesService = salesService;
	}

	// ---------------------------------------------------------------- queries

	/** Per-line remaining returnable quantity for an order. */
	@Transactional(readOnly = true)
	public List<ReturnableLine> returnableLines(long salesOrderId) {
		SalesOrderEntity so = salesOrderRepository.findWithLinesById(salesOrderId).orElseThrow();
		BigDecimal ratio = discountRatio(so);
		List<ReturnableLine> out = new ArrayList<>();
		for (SalesOrderLineEntity line : so.getLines()) {
			int returned = returnLineRepository.sumReturnedQuantityForLine(line.getId());
			BigDecimal effective = line.getUnitPrice().multiply(ratio).setScale(2, RoundingMode.HALF_UP);
			out.add(new ReturnableLine(line.getId(), line.getProduct().getId(), line.getProduct().getSku(), line.getProduct().getName(),
					line.getQuantity(), returned, Math.max(0, line.getQuantity() - returned), line.getUnitPrice(), effective, line.getLocation()));
		}
		return out;
	}

	@Transactional(readOnly = true)
	public boolean withinReturnWindow(SalesOrderEntity so) {
		return Duration.between(so.getSoldAt(), Instant.now()).toDays() <= RETURN_WINDOW_DAYS;
	}

	// ------------------------------------------------------------- mutations

	@Transactional
	public SalesReturnEntity createReturn(CreateReturnCommand cmd, Authentication auth, boolean allowOverride) {
		SalesOrderEntity so = loadOrder(cmd.salesOrderId(), allowOverride);
		SalesReturnEntity ret = buildReturn(so, cmd.lines(), cmd.reason(), auth);
		settleRefund(ret, so, cmd.refundMethod(), cmd.debtCreditAmount(), auth);
		ret.setSettlementAmount(ret.getRefundAmount().negate());
		return returnRepository.save(ret);
	}

	@Transactional
	public SalesReturnEntity createExchange(CreateExchangeCommand cmd, Authentication auth, boolean allowOverride) {
		SalesOrderEntity so = loadOrder(cmd.salesOrderId(), allowOverride);
		if (cmd.newLines() == null || cmd.newLines().isEmpty()) {
			throw new IllegalArgumentException("Select at least one product to exchange for");
		}
		SalesReturnEntity ret = buildReturn(so, cmd.returnLines(), cmd.reason(), auth);

		BigDecimal newTotal = cmd.newLines().stream()
				.map(l -> BigDecimal.valueOf(l.unitPrice()).multiply(BigDecimal.valueOf(l.quantity())))
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal difference = newTotal.subtract(ret.getReturnValue()); // + customer pays, − we refund

		// The new sale: customer "pays" with the return credit plus any extra they hand over.
		BigDecimal extraPaid = cmd.amountReceived() == null ? difference.max(BigDecimal.ZERO) : cmd.amountReceived();
		if (extraPaid.signum() < 0) throw new IllegalArgumentException("Amount received cannot be negative");
		BigDecimal received = ret.getReturnValue().add(extraPaid).min(newTotal);

		Long customerId = so.getCustomerRef() != null ? so.getCustomerRef().getId() : null;
		if (received.compareTo(newTotal) < 0 && (customerId == null || SalesService.WALK_IN_CUSTOMER_NAME.equalsIgnoreCase(so.getCustomer()))) {
			throw new IllegalArgumentException("Select a registered customer to sell on debt");
		}
		String ref = "EXC-" + ret.getReference();
		SalesOrderEntity newSale = salesService.createSale(new SalesService.CreateSaleCommand(
				so.getCustomer(), customerId, ref, BigDecimal.ZERO, cmd.paymentMethod(), received, cmd.newLines()), auth);
		ret.setExchangeSalesOrder(newSale);
		ret.setSettlementAmount(difference);

		if (difference.signum() < 0) {
			// Customer gets money back for the difference.
			settleRefund(ret, so, cmd.refundMethod(), cmd.debtCreditAmount(), auth, difference.negate());
		} else {
			ret.setRefundMethod(REFUND_NONE);
		}
		return returnRepository.save(ret);
	}

	// ---------------------------------------------------------------- internals

	private SalesOrderEntity loadOrder(long id, boolean allowOverride) {
		SalesOrderEntity so = salesOrderRepository.findWithLinesById(id)
				.orElseThrow(() -> new IllegalArgumentException("Sale not found"));
		if (!allowOverride && !withinReturnWindow(so)) {
			throw new IllegalArgumentException("Return window of " + RETURN_WINDOW_DAYS + " days has passed for this sale");
		}
		return so;
	}

	private SalesReturnEntity buildReturn(SalesOrderEntity so, List<ReturnLineCommand> lines, String reason, Authentication auth) {
		if (lines == null || lines.isEmpty()) {
			throw new IllegalArgumentException("Select at least one item to return");
		}
		String by = auth != null ? String.valueOf(auth.getPrincipal()) : null;
		Map<Long, SalesOrderLineEntity> byId = new java.util.HashMap<>();
		for (SalesOrderLineEntity l : so.getLines()) byId.put(l.getId(), l);
		BigDecimal ratio = discountRatio(so);

		SalesReturnEntity ret = new SalesReturnEntity();
		ret.setReference(nextReference());
		ret.setOriginalSalesOrder(so);
		ret.setCustomer(so.getCustomerRef());
		ret.setReturnedBy(by);
		ret.setReason(reason != null && !reason.isBlank() ? reason.trim() : null);
		ret.setBranch(so.getLines().isEmpty() ? "MAIN" : Objects.requireNonNullElse(so.getLines().get(0).getLocation(), "MAIN"));

		BigDecimal value = BigDecimal.ZERO;
		BigDecimal loss = BigDecimal.ZERO;
		for (ReturnLineCommand rl : lines) {
			if (rl == null || rl.quantity() <= 0) continue;
			SalesOrderLineEntity line = byId.get(rl.salesOrderLineId());
			if (line == null) throw new IllegalArgumentException("Line does not belong to this sale");
			int already = returnLineRepository.sumReturnedQuantityForLine(line.getId());
			int remainingReturnable = line.getQuantity() - already;
			if (rl.quantity() > remainingReturnable) {
				throw new IllegalArgumentException("Only " + remainingReturnable + " of " + line.getProduct().getName() + " can still be returned");
			}
			ReturnCondition cond = rl.condition() == null ? ReturnCondition.RESALABLE : rl.condition();
			BigDecimal effectivePrice = line.getUnitPrice().multiply(ratio).setScale(2, RoundingMode.HALF_UP);
			String location = line.getLocation() != null && !line.getLocation().isBlank() ? line.getLocation() : "MAIN";

			// Walk the original deductions so stock goes back to the exact batches it came from.
			int toReturn = rl.quantity();
			List<SalesDeductionEntity> deductions = new ArrayList<>(line.getDeductions());
			deductions.sort((a, b) -> Long.compare(b.getId(), a.getId())); // newest-taken first
			for (SalesDeductionEntity d : deductions) {
				if (toReturn <= 0) break;
				ProductBatchEntity batch = d.getBatch();
				int qty = Math.min(toReturn, d.getQuantity());
				if (qty <= 0) continue;

				SalesReturnLineEntity srl = new SalesReturnLineEntity();
				srl.setSalesOrderLine(line);
				srl.setProduct(line.getProduct());
				srl.setBatch(batch);
				srl.setQuantity(qty);
				srl.setUnitPrice(effectivePrice);
				srl.setCondition(cond);

				StockMovementEntity mv = new StockMovementEntity();
				mv.setBatch(batch);
				mv.setQuantity(qty);
				mv.setCreatedBy(by);
				if (cond == ReturnCondition.RESALABLE) {
					InventoryItemEntity inv = inventoryRepository.findForUpdate(batch.getId(), location).orElseGet(() -> {
						InventoryItemEntity i = new InventoryItemEntity();
						i.setBatch(batch);
						i.setLocation(location);
						i.setQtyOnHand(0);
						return i;
					});
					inv.setQtyOnHand(inv.getQtyOnHand() + qty);
					inventoryRepository.save(inv);
					mv.setType(StockMovementType.RETURN);
					mv.setNote("Customer return " + ret.getReference() + " (sale " + so.getReferenceNumber() + ")");
				} else {
					BigDecimal cost = (batch.getCostPrice() != null ? batch.getCostPrice() : BigDecimal.ZERO).multiply(BigDecimal.valueOf(qty));
					srl.setLossAmount(cost);
					loss = loss.add(cost);
					mv.setType(StockMovementType.LOSS);
					mv.setNote("Return " + ret.getReference() + " — " + cond.name().toLowerCase() + ", not restocked");
				}
				stockMovementRepository.save(mv);
				ret.addLine(srl);
				value = value.add(effectivePrice.multiply(BigDecimal.valueOf(qty)));
				toReturn -= qty;
			}
			if (toReturn > 0) {
				throw new IllegalStateException("Could not map returned quantity to original batches for " + line.getProduct().getName());
			}
		}
		if (ret.getLines().isEmpty()) {
			throw new IllegalArgumentException("Select at least one item to return");
		}
		ret.setReturnValue(value.setScale(2, RoundingMode.HALF_UP));
		ret.setLossAmount(loss.setScale(2, RoundingMode.HALF_UP));
		if (loss.signum() > 0) recordLossExpense(ret, by);
		return ret;
	}

	private void settleRefund(SalesReturnEntity ret, SalesOrderEntity so, String refundMethod, BigDecimal debtCredit, Authentication auth) {
		settleRefund(ret, so, refundMethod, debtCredit, auth, ret.getReturnValue());
	}

	/**
	 * Splits {@code total} between a debt credit (reduces the customer's outstanding balance — applied FIFO exactly
	 * like a customer payment) and a cash/mobile refund. The caller chose the split in the UI.
	 */
	private void settleRefund(SalesReturnEntity ret, SalesOrderEntity so, String refundMethod, BigDecimal debtCredit, Authentication auth, BigDecimal total) {
		BigDecimal credit = debtCredit == null ? BigDecimal.ZERO : debtCredit.setScale(2, RoundingMode.HALF_UP);
		if (credit.signum() < 0 || credit.compareTo(total) > 0) {
			throw new IllegalArgumentException("Debt credit cannot be negative or exceed the refund value");
		}
		CustomerEntity cust = so.getCustomerRef();
		if (credit.signum() > 0) {
			if (cust == null) throw new IllegalArgumentException("Cannot credit debt: sale has no registered customer");
			BigDecimal applied = applyDebtCredit(cust, credit, ret, auth);
			if (applied.compareTo(credit) < 0) {
				throw new IllegalArgumentException("Customer debt is only " + applied.toPlainString() + "; reduce the debt credit amount");
			}
		}
		BigDecimal cash = total.subtract(credit);
		ret.setDebtCreditAmount(credit);
		ret.setRefundAmount(cash);
		if (cash.signum() > 0) {
			String m = refundMethod == null || refundMethod.isBlank() ? "CASH" : refundMethod.trim();
			if (m.length() > 40) throw new IllegalArgumentException("Invalid refund method");
			ret.setRefundMethod(m);
		} else {
			ret.setRefundMethod(credit.signum() > 0 ? REFUND_DEBT_CREDIT : REFUND_NONE);
		}
	}

	/** FIFO: same allocation rule as recordCustomerPayment. Returns the amount actually applied. */
	private BigDecimal applyDebtCredit(CustomerEntity cust, BigDecimal amount, SalesReturnEntity ret, Authentication auth) {
		BigDecimal remaining = amount;
		BigDecimal applied = BigDecimal.ZERO;
		String by = auth != null ? String.valueOf(auth.getPrincipal()) : null;
		for (SalesOrderEntity order : salesOrderRepository.findWithLinesByCustomerId(cust.getId())) {
			if (remaining.signum() <= 0) break;
			BigDecimal net = order.getLines().stream()
					.map(l -> l.getUnitPrice().multiply(BigDecimal.valueOf(l.getQuantity())))
					.reduce(BigDecimal.ZERO, BigDecimal::add)
					.subtract(order.getDiscount() != null ? order.getDiscount() : BigDecimal.ZERO)
					.max(BigDecimal.ZERO);
			BigDecimal received = order.getAmountReceived() != null ? order.getAmountReceived() : net;
			BigDecimal outstanding = net.subtract(received).max(BigDecimal.ZERO);
			if (outstanding.signum() <= 0) continue;
			BigDecimal pay = remaining.min(outstanding);
			order.setAmountReceived(received.add(pay));
			salesOrderRepository.save(order);

			CustomerPaymentEntity p = new CustomerPaymentEntity();
			p.setCustomer(cust);
			p.setSalesOrder(order);
			p.setAmount(pay);
			p.setPaidAt(Instant.now());
			p.setCreatedBy(by != null ? by + " (return " + ret.getReference() + ")" : "return " + ret.getReference());
			customerPaymentRepository.save(p);

			remaining = remaining.subtract(pay);
			applied = applied.add(pay);
		}
		return applied;
	}

	private void recordLossExpense(SalesReturnEntity ret, String by) {
		ExpenseCategoryEntity cat = expenseCategoryRepository.findByNameIgnoreCase(LOSS_CATEGORY_NAME).orElseGet(() -> {
			ExpenseCategoryEntity c = new ExpenseCategoryEntity();
			c.setName(LOSS_CATEGORY_NAME);
			c.setActive(true);
			return expenseCategoryRepository.save(c);
		});
		ExpenseEntity e = new ExpenseEntity();
		e.setExpenseDate(LocalDate.now());
		e.setCategory(cat);
		e.setDescription("Damaged/expired goods returned — " + ret.getReference());
		e.setAmount(ret.getLossAmount());
		e.setPaymentMethod(ExpensePaymentMethod.CASH);
		e.setBranch(ret.getBranch());
		e.setCreatedBy(by);
		expenseRepository.save(e);
	}

	/** Order-level discount spread proportionally over its lines so refunds reflect what the customer actually paid. */
	private static BigDecimal discountRatio(SalesOrderEntity so) {
		BigDecimal gross = so.getLines().stream()
				.map(l -> l.getUnitPrice().multiply(BigDecimal.valueOf(l.getQuantity())))
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal discount = so.getDiscount() != null ? so.getDiscount() : BigDecimal.ZERO;
		if (gross.signum() <= 0 || discount.signum() <= 0) return BigDecimal.ONE;
		return gross.subtract(discount).divide(gross, 6, RoundingMode.HALF_UP);
	}

	private String nextReference() {
		LocalDate today = Instant.now().atZone(ZoneOffset.UTC).toLocalDate();
		String prefix = "RET-" + today.format(DateTimeFormatter.ofPattern("ddMMyy"));
		long seq = returnRepository.countByReturnedAtGreaterThanEqual(today.atStartOfDay(ZoneOffset.UTC).toInstant());
		String ref;
		do {
			ref = prefix + "-" + String.format("%04d", ++seq);
		} while (returnRepository.existsByReference(ref));
		return ref;
	}

	// ------------------------------------------------------------------ types

	public record ReturnableLine(Long lineId, Long productId, String sku, String productName, int soldQuantity, int returnedQuantity,
			int returnableQuantity, BigDecimal unitPrice, BigDecimal effectiveUnitPrice, String location) {}

	public record ReturnLineCommand(long salesOrderLineId, int quantity, ReturnCondition condition) {}

	public record CreateReturnCommand(long salesOrderId, List<ReturnLineCommand> lines, String reason, String refundMethod, BigDecimal debtCreditAmount) {}

	public record CreateExchangeCommand(long salesOrderId, List<ReturnLineCommand> returnLines, List<SalesService.CreateSaleLineCommand> newLines,
			String reason, String paymentMethod, BigDecimal amountReceived, String refundMethod, BigDecimal debtCreditAmount) {}
}
