package com.cosmetics.inventory.sales;

import com.cosmetics.inventory.customer.CustomerEntity;
import com.cosmetics.inventory.customer.CustomerRepository;
import com.cosmetics.inventory.inventory.InventoryItemEntity;
import com.cosmetics.inventory.inventory.InventoryRepository;
import com.cosmetics.inventory.product.ProductBatchEntity;
import com.cosmetics.inventory.product.ProductBatchRepository;
import com.cosmetics.inventory.product.ProductEntity;
import com.cosmetics.inventory.product.ProductRepository;
import com.cosmetics.inventory.stockmovement.StockMovementEntity;
import com.cosmetics.inventory.stockmovement.StockMovementRepository;
import com.cosmetics.inventory.stockmovement.StockMovementType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class SalesService {
	public static final String WALK_IN_CUSTOMER_NAME = "My Customer";
	private final SalesOrderRepository salesOrderRepository;
	private final ProductRepository productRepository;
	private final ProductBatchRepository batchRepository;
	private final InventoryRepository inventoryRepository;
	private final StockMovementRepository stockMovementRepository;
	private final CustomerRepository customerRepository;

	public SalesService(
			SalesOrderRepository salesOrderRepository,
			ProductRepository productRepository,
			ProductBatchRepository batchRepository,
			InventoryRepository inventoryRepository,
			StockMovementRepository stockMovementRepository,
			CustomerRepository customerRepository
	) {
		this.salesOrderRepository = salesOrderRepository;
		this.productRepository = productRepository;
		this.batchRepository = batchRepository;
		this.inventoryRepository = inventoryRepository;
		this.stockMovementRepository = stockMovementRepository;
		this.customerRepository = customerRepository;
	}

	@Transactional
	public SalesOrderEntity createSale(CreateSaleCommand cmd, Authentication authentication) {
		SalesOrderEntity so = new SalesOrderEntity();
		CustomerEntity cust = applyCustomer(so, cmd.customerId());
		String ref = cmd.referenceNumber();
		so.setReferenceNumber(ref != null && !ref.isBlank() ? ref.trim() : nextReference());
		so.setSoldBy(authentication != null ? String.valueOf(authentication.getPrincipal()) : null);
		BigDecimal subtotal = subtotal(cmd.lines());
		so.setDiscount(validateDiscount(cmd.discount(), subtotal));
		BigDecimal payable = subtotal.subtract(so.getDiscount());
		applyPayment(so, cmd, payable);
		requireRegisteredCustomerForDebt(cust, cmd.amountReceived(), payable);
		applySaleLines(so, cmd.lines(), so.getReferenceNumber(), authentication);
		return salesOrderRepository.save(so);
	}

	@Transactional
	public SalesOrderEntity updateSale(UpdateSaleCommand cmd, Authentication authentication) {
		if (cmd == null || cmd.id() <= 0) {
			throw new IllegalArgumentException("Sale id is required");
		}
		SalesOrderEntity so = salesOrderRepository.findById(cmd.id()).orElseThrow();
		restoreInventoryForSale(so, authentication);

		CustomerEntity cust = applyCustomer(so, cmd.customerId());
		String ref = cmd.referenceNumber();
		if (ref != null && !ref.isBlank()) {
			so.setReferenceNumber(ref.trim());
		}
		BigDecimal subtotal = subtotal(cmd.lines());
		so.setDiscount(validateDiscount(cmd.discount(), subtotal));
		BigDecimal payable = subtotal.subtract(so.getDiscount());
		applyPayment(so, cmd, payable);
		requireRegisteredCustomerForDebt(cust, cmd.amountReceived(), payable);

		so.getLines().clear();
		applySaleLines(so, cmd.lines(), so.getReferenceNumber(), authentication);
		return salesOrderRepository.save(so);
	}

	private CustomerEntity applyCustomer(SalesOrderEntity so, Long customerId) {
		CustomerEntity cust = (customerId == null || customerId <= 0)
				? walkInCustomer()
				: customerRepository.findById(customerId)
						.orElseThrow(() -> new IllegalArgumentException("Customer not found"));
		so.setCustomerRef(cust);
		so.setCustomer(cust.getName());
		return cust;
	}

	private CustomerEntity walkInCustomer() {
		return customerRepository.findFirstByNameIgnoreCase(WALK_IN_CUSTOMER_NAME)
				.orElseGet(() -> {
					CustomerEntity c = new CustomerEntity();
					c.setName(WALK_IN_CUSTOMER_NAME);
					return customerRepository.save(c);
				});
	}

	private static void requireRegisteredCustomerForDebt(CustomerEntity cust, java.math.BigDecimal received, java.math.BigDecimal payable) {
		if (received != null && received.compareTo(payable) < 0 && WALK_IN_CUSTOMER_NAME.equalsIgnoreCase(cust.getName())) {
			throw new IllegalArgumentException("Select a registered customer to sell on debt");
		}
	}

	private String nextReference() {
		java.time.LocalDate today = java.time.Instant.now().atZone(java.time.ZoneOffset.UTC).toLocalDate();
		String prefix = today.format(java.time.format.DateTimeFormatter.ofPattern("ddMMyy"));
		java.time.Instant dayStart = today.atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
		long seq = salesOrderRepository.countBySoldAtGreaterThanEqual(dayStart);
		String ref;
		do {
			ref = prefix + "-" + String.format("%04d", ++seq);
		} while (salesOrderRepository.existsByReferenceNumber(ref));
		return ref;
	}

	private void applyPayment(SalesOrderEntity so, Object cmd, BigDecimal payable) {
		String method;
		BigDecimal received;
		if (cmd instanceof CreateSaleCommand c) {
			method = c.paymentMethod();
			received = c.amountReceived();
		} else if (cmd instanceof UpdateSaleCommand u) {
			method = u.paymentMethod();
			received = u.amountReceived();
		} else {
			return;
		}
		if (method != null) {
			method = method.trim();
			if (method.length() > 40) {
				throw new IllegalArgumentException("Invalid payment method");
			}
		}
		so.setPaymentMethod(method == null || method.isBlank() ? null : method.trim());
		if (received != null && received.signum() < 0) {
			throw new IllegalArgumentException("Amount received cannot be negative");
		}
		so.setAmountReceived(received);
	}

	private BigDecimal subtotal(List<CreateSaleLineCommand> lines) {
		if (lines == null) {
			return BigDecimal.ZERO;
		}
		return lines.stream()
				.filter(l -> l != null && l.quantity() > 0)
				.map(l -> BigDecimal.valueOf(l.unitPrice()).multiply(BigDecimal.valueOf(l.quantity())))
				.reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	private BigDecimal validateDiscount(BigDecimal discount, BigDecimal subtotal) {
		BigDecimal d = discount == null ? BigDecimal.ZERO : discount;
		if (d.signum() < 0) {
			throw new IllegalArgumentException("Discount cannot be negative");
		}
		if (d.compareTo(subtotal) > 0) {
			throw new IllegalArgumentException("Discount cannot exceed the sale total");
		}
		return d;
	}

    @Transactional
    public boolean deleteSale(long id, Authentication authentication) {
        SalesOrderEntity so = salesOrderRepository.findById(id).orElseThrow();
        restoreInventoryForSale(so, authentication);
        salesOrderRepository.delete(so);
        return true;
    }

    private void restoreInventoryForSale(SalesOrderEntity so, Authentication authentication) {
        if (so == null) return;
        for (SalesOrderLineEntity line : so.getLines()) {
            String location = (line.getLocation() != null && !line.getLocation().isBlank()) ? line.getLocation().trim() : "MAIN";
            for (SalesDeductionEntity d : line.getDeductions()) {
                ProductBatchEntity batch = d.getBatch();
                if (batch == null) continue;
                int qty = d.getQuantity();
                if (qty <= 0) continue;

                InventoryItemEntity inv = inventoryRepository.findForUpdate(batch.getId(), location)
                        .orElseGet(() -> {
                            InventoryItemEntity i = new InventoryItemEntity();
                            i.setBatch(batch);
                            i.setLocation(location);
                            i.setQtyOnHand(0);
                            return i;
                        });
                inv.setQtyOnHand(inv.getQtyOnHand() + qty);
                inventoryRepository.save(inv);

                StockMovementEntity mv = new StockMovementEntity();
                mv.setType(StockMovementType.RETURN);
                mv.setBatch(batch);
                mv.setQuantity(qty);
                mv.setCreatedBy(authentication != null ? String.valueOf(authentication.getPrincipal()) : null);
                mv.setNote(so.getReferenceNumber() != null ? ("Sale rollback ref: " + so.getReferenceNumber()) : "Sale rollback");
                stockMovementRepository.save(mv);
            }
        }
    }

    private void applySaleLines(SalesOrderEntity so, List<CreateSaleLineCommand> lines, String ref, Authentication authentication) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("At least one line is required");
        }

        for (CreateSaleLineCommand line : lines) {
            if (line == null) continue;
            if (line.quantity() <= 0) {
                throw new IllegalArgumentException("Quantity must be > 0");
            }
            ProductEntity product = productRepository.findById(line.productId()).orElseThrow();
            String location = (line.location() != null && !line.location().isBlank()) ? line.location().trim() : "MAIN";

            SalesOrderLineEntity sol = new SalesOrderLineEntity();
            sol.setProduct(product);
            sol.setQuantity(line.quantity());
            sol.setUnitPrice(BigDecimal.valueOf(line.unitPrice()));
            sol.setLocation(location);

            int remaining = line.quantity();
            LocalDate today = LocalDate.now();

            List<ProductBatchEntity> batches = batchRepository.findByProductIdOrderByExpiryDateAscCreatedAtAsc(product.getId());
            for (ProductBatchEntity batch : batches) {
                if (remaining <= 0) break;
                if (batch.getExpiryDate().isBefore(today)) {
                    continue; // expired batch restriction
                }

                InventoryItemEntity inv = inventoryRepository.findForUpdate(batch.getId(), location).orElse(null);
                if (inv == null || inv.getQtyOnHand() <= 0) {
                    continue;
                }

                int take = Math.min(inv.getQtyOnHand(), remaining);
                inv.setQtyOnHand(inv.getQtyOnHand() - take);
                inventoryRepository.save(inv);

                StockMovementEntity mv = new StockMovementEntity();
                mv.setType(StockMovementType.OUT);
                mv.setBatch(batch);
                mv.setQuantity(take);
                mv.setCreatedBy(authentication != null ? String.valueOf(authentication.getPrincipal()) : null);
                mv.setNote(ref != null ? ("Sale ref: " + ref) : "Sale");
                stockMovementRepository.save(mv);

                SalesDeductionEntity d = new SalesDeductionEntity();
                d.setBatch(batch);
                d.setQuantity(take);
                sol.addDeduction(d);

                remaining -= take;
            }

            if (remaining > 0) {
                throw new IllegalArgumentException("Insufficient non-expired stock for product " + product.getSku() + " at location " + location);
            }

            so.addLine(sol);
        }
    }

    public record CreateSaleCommand(String customer, Long customerId, String referenceNumber, BigDecimal discount, String paymentMethod, BigDecimal amountReceived, List<CreateSaleLineCommand> lines) {
    }

    public record UpdateSaleCommand(long id, String customer, Long customerId, String referenceNumber, BigDecimal discount, String paymentMethod, BigDecimal amountReceived, List<CreateSaleLineCommand> lines) {
    }

    public record CreateSaleLineCommand(long productId, int quantity, double unitPrice, String location) {
    }
}
