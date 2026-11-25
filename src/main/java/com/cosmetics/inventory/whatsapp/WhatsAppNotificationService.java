package com.cosmetics.inventory.whatsapp;

import com.cosmetics.inventory.customer.CustomerEntity;
import com.cosmetics.inventory.customer.CustomerRepository;
import com.cosmetics.inventory.sales.SalesOrderEntity;
import com.cosmetics.inventory.sales.SalesOrderRepository;
import com.cosmetics.inventory.whatsapp.WhatsAppMessageEntity.Kind;
import com.cosmetics.inventory.whatsapp.WhatsAppMessageEntity.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Sends sales receipts and debt reminders through approved WhatsApp templates.
 *
 * Template body parameters (must match the templates approved in Meta):
 *   receipt: {{1}} customer, {{2}} reference, {{3}} date, {{4}} total, {{5}} paid, {{6}} debt
 *   debt:    {{1}} customer, {{2}} total owed, {{3}} unpaid sales list
 */
@Service
public class WhatsAppNotificationService {
	private static final Logger log = LoggerFactory.getLogger(WhatsAppNotificationService.class);
	private static final ZoneId ZONE = ZoneId.of("Africa/Dar_es_Salaam");
	private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZONE);
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZONE);
	private static final String WALK_IN = "Mteja / Customer";

	private final WhatsAppProperties props;
	private final WhatsAppClient client;
	private final WhatsAppMessageRepository messages;
	private final SalesOrderRepository orders;
	private final CustomerRepository customers;
	private final TransactionTemplate tx;

	public WhatsAppNotificationService(WhatsAppProperties props, WhatsAppClient client, WhatsAppMessageRepository messages,
									   SalesOrderRepository orders, CustomerRepository customers, PlatformTransactionManager txManager) {
		this.props = props;
		this.client = client;
		this.messages = messages;
		this.orders = orders;
		this.customers = customers;
		this.tx = new TransactionTemplate(txManager);
	}

	public boolean enabled() {
		return props.ready();
	}

	/** Called after checkout: sends once per sale, only to registered customers. Never throws. */
	public void autoSendReceipt(Long salesOrderId, String by) {
		if (!props.ready() || !props.autoReceipt() || salesOrderId == null) return;
		try {
			tx.executeWithoutResult(s -> {
				if (messages.findTopBySalesOrderIdAndKindOrderByIdDesc(salesOrderId, Kind.RECEIPT).isPresent()) return;
				SalesOrderEntity order = orders.findWithLinesById(salesOrderId).orElse(null);
				if (order == null || order.getCustomerRef() == null) return;
				sendReceipt(order, order.getCustomerRef().getPhone(), by);
			});
		} catch (Exception e) {
			log.warn("WhatsApp auto receipt for sale {} failed: {}", salesOrderId, e.getMessage());
		}
	}

	/** Manual send/resend from the receipt dialog; `phone` overrides the customer's saved number. */
	public WhatsAppMessageDto sendReceipt(Long salesOrderId, String phone, String by) {
		requireEnabled();
		return tx.execute(s -> {
			SalesOrderEntity order = orders.findWithLinesById(salesOrderId)
					.orElseThrow(() -> new IllegalArgumentException("Sale not found"));
			String target = phone != null && !phone.isBlank() ? phone
					: order.getCustomerRef() != null ? order.getCustomerRef().getPhone() : null;
			return WhatsAppMessageDto.from(sendReceipt(order, target, by));
		});
	}

	public WhatsAppMessageDto latestReceipt(Long salesOrderId) {
		return messages.findTopBySalesOrderIdAndKindOrderByIdDesc(salesOrderId, Kind.RECEIPT).map(WhatsAppMessageDto::from).orElse(null);
	}

	public WhatsAppMessageDto sendDebtReminder(Long customerId, String by) {
		requireEnabled();
		return tx.execute(s -> {
			CustomerEntity customer = customers.findById(customerId)
					.orElseThrow(() -> new IllegalArgumentException("Customer not found"));
			List<SalesOrderEntity> unpaid = unpaidOrders(customerId);
			if (unpaid.isEmpty()) throw new IllegalArgumentException("This customer has no outstanding debt");
			return WhatsAppMessageDto.from(sendDebt(customer, unpaid, by));
		});
	}

	/** Reminds every debtor with a valid phone, skipping anyone reminded within reminderMinDays. */
	public BulkResult sendDebtReminders(String by) {
		requireEnabled();
		Instant cutoff = Instant.now().minus(Duration.ofDays(Math.max(0, props.reminderMinDays())));
		int sent = 0, failed = 0, skipped = 0;
		for (CustomerEntity c : customers.findAll()) {
			Status result = tx.execute(s -> {
				List<SalesOrderEntity> unpaid = unpaidOrders(c.getId());
				if (unpaid.isEmpty()) return null;
				if (WhatsAppSupport.normalizePhone(c.getPhone()) == null) return Status.SKIPPED;
				var last = messages.findTopByCustomerIdAndKindOrderByIdDesc(c.getId(), Kind.DEBT_REMINDER);
				if (last.isPresent() && last.get().succeeded() && last.get().getCreatedAt().isAfter(cutoff)) return Status.SKIPPED;
				return sendDebt(c, unpaid, by).getStatus();
			});
			if (result == null) continue;
			if (result == Status.SKIPPED) skipped++;
			else if (result == Status.FAILED) failed++;
			else sent++;
		}
		return new BulkResult(sent, failed, skipped);
	}

	private WhatsAppMessageEntity sendReceipt(SalesOrderEntity order, String phone, String by) {
		BigDecimal net = net(order);
		BigDecimal received = order.getAmountReceived() != null ? order.getAmountReceived() : net;
		String name = order.getCustomerRef() != null ? order.getCustomerRef().getName()
				: order.getCustomer() != null && !order.getCustomer().isBlank() ? order.getCustomer() : WALK_IN;
		List<String> params = List.of(
				WhatsAppSupport.templateParam(name, 60),
				WhatsAppSupport.templateParam(reference(order), 60),
				DATE_TIME.format(order.getSoldAt()),
				money(net),
				money(received),
				money(net.subtract(received).max(BigDecimal.ZERO)));
		Long customerId = order.getCustomerRef() != null ? order.getCustomerRef().getId() : null;
		return deliver(Kind.RECEIPT, customerId, order.getId(), phone, props.receiptTemplate(), params, by);
	}

	private WhatsAppMessageEntity sendDebt(CustomerEntity customer, List<SalesOrderEntity> unpaid, String by) {
		BigDecimal total = BigDecimal.ZERO;
		List<String> items = new ArrayList<>();
		for (SalesOrderEntity o : unpaid) {
			BigDecimal balance = debt(o);
			total = total.add(balance);
			items.add(reference(o) + " (" + DATE.format(o.getSoldAt()) + "): " + money(balance));
		}
		List<String> params = List.of(
				WhatsAppSupport.templateParam(customer.getName(), 60),
				money(total),
				WhatsAppSupport.templateParam(String.join("; ", items), 600));
		return deliver(Kind.DEBT_REMINDER, customer.getId(), null, customer.getPhone(), props.debtTemplate(), params, by);
	}

	/** Logs the attempt, calls the API and records the outcome. API errors become FAILED rows, not exceptions. */
	private WhatsAppMessageEntity deliver(Kind kind, Long customerId, Long salesOrderId, String rawPhone, String template,
										  List<String> params, String by) {
		WhatsAppMessageEntity m = new WhatsAppMessageEntity();
		m.setKind(kind);
		m.setCustomerId(customerId);
		m.setSalesOrderId(salesOrderId);
		m.setTemplate(template);
		m.setCreatedBy(by);
		String phone = WhatsAppSupport.normalizePhone(rawPhone);
		m.setPhone(phone);
		if (phone == null) {
			m.setStatus(Status.SKIPPED);
			m.setError("No valid WhatsApp number");
			return messages.save(m);
		}
		try {
			m.setWamid(client.sendTemplate(phone, template, params));
			m.setStatus(Status.SENT);
		} catch (Exception e) {
			log.warn("WhatsApp {} to {} failed: {}", kind, phone, e.getMessage());
			m.setStatus(Status.FAILED);
			m.setError(e.getMessage());
		}
		return messages.save(m);
	}

	/** Same debt rule as customerDebt / recordCustomerPayment: net - amountReceived per sale. */
	private List<SalesOrderEntity> unpaidOrders(Long customerId) {
		return orders.findWithLinesByCustomerId(customerId).stream().filter(o -> debt(o).signum() > 0).toList();
	}

	private static BigDecimal net(SalesOrderEntity o) {
		return o.getLines().stream()
				.map(l -> l.getUnitPrice().multiply(BigDecimal.valueOf(l.getQuantity())))
				.reduce(BigDecimal.ZERO, BigDecimal::add)
				.subtract(o.getDiscount() != null ? o.getDiscount() : BigDecimal.ZERO)
				.max(BigDecimal.ZERO);
	}

	private static BigDecimal debt(SalesOrderEntity o) {
		BigDecimal net = net(o);
		BigDecimal received = o.getAmountReceived() != null ? o.getAmountReceived() : net;
		return net.subtract(received).max(BigDecimal.ZERO);
	}

	private static String reference(SalesOrderEntity o) {
		return o.getReferenceNumber() != null && !o.getReferenceNumber().isBlank() ? o.getReferenceNumber() : "#" + o.getId();
	}

	private static String money(BigDecimal v) {
		return "TSH " + NumberFormat.getIntegerInstance(Locale.US).format(v.setScale(0, RoundingMode.HALF_UP));
	}

	private void requireEnabled() {
		if (!props.ready()) throw new IllegalArgumentException("WhatsApp API is not enabled on this server");
	}

	public record BulkResult(int sent, int failed, int skipped) {
	}
}
