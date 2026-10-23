package com.cosmetics.inventory.returns;

import com.cosmetics.inventory.customer.CustomerEntity;
import com.cosmetics.inventory.sales.SalesOrderEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sales_returns")
public class SalesReturnEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 40)
	private String reference;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "original_sales_order_id", nullable = false)
	private SalesOrderEntity originalSalesOrder;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "exchange_sales_order_id")
	private SalesOrderEntity exchangeSalesOrder;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "customer_id")
	private CustomerEntity customer;

	@Column(nullable = false)
	private Instant returnedAt = Instant.now();

	@Column(length = 320)
	private String returnedBy;

	@Column(length = 1000)
	private String reason;

	@Column(nullable = false, length = 60)
	private String branch = "MAIN";

	/** Value of returned goods at original sale price. */
	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal returnValue = BigDecimal.ZERO;

	/** Cost of goods written off (DAMAGED/EXPIRED). */
	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal lossAmount = BigDecimal.ZERO;

	/** CASH / MPESA / ... / DEBT_CREDIT (applied to customer's debt) / NONE. */
	@Column(length = 40)
	private String refundMethod;

	/** Amount refunded to the customer in cash/mobile (not applied to debt). */
	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal refundAmount = BigDecimal.ZERO;

	/** Portion of the refund that was applied against the customer's outstanding debt. */
	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal debtCreditAmount = BigDecimal.ZERO;

	/** Exchange only: + customer paid extra, − customer was refunded the difference. */
	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal settlementAmount = BigDecimal.ZERO;

	@OneToMany(mappedBy = "salesReturn", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<SalesReturnLineEntity> lines = new ArrayList<>();

	public Long getId() { return id; }
	public String getReference() { return reference; }
	public void setReference(String reference) { this.reference = reference; }
	public SalesOrderEntity getOriginalSalesOrder() { return originalSalesOrder; }
	public void setOriginalSalesOrder(SalesOrderEntity originalSalesOrder) { this.originalSalesOrder = originalSalesOrder; }
	public SalesOrderEntity getExchangeSalesOrder() { return exchangeSalesOrder; }
	public void setExchangeSalesOrder(SalesOrderEntity exchangeSalesOrder) { this.exchangeSalesOrder = exchangeSalesOrder; }
	public CustomerEntity getCustomer() { return customer; }
	public void setCustomer(CustomerEntity customer) { this.customer = customer; }
	public Instant getReturnedAt() { return returnedAt; }
	public void setReturnedAt(Instant returnedAt) { this.returnedAt = returnedAt; }
	public String getReturnedBy() { return returnedBy; }
	public void setReturnedBy(String returnedBy) { this.returnedBy = returnedBy; }
	public String getReason() { return reason; }
	public void setReason(String reason) { this.reason = reason; }
	public String getBranch() { return branch; }
	public void setBranch(String branch) { this.branch = branch; }
	public BigDecimal getReturnValue() { return returnValue; }
	public void setReturnValue(BigDecimal returnValue) { this.returnValue = returnValue; }
	public BigDecimal getLossAmount() { return lossAmount; }
	public void setLossAmount(BigDecimal lossAmount) { this.lossAmount = lossAmount; }
	public String getRefundMethod() { return refundMethod; }
	public void setRefundMethod(String refundMethod) { this.refundMethod = refundMethod; }
	public BigDecimal getRefundAmount() { return refundAmount; }
	public void setRefundAmount(BigDecimal refundAmount) { this.refundAmount = refundAmount; }
	public BigDecimal getDebtCreditAmount() { return debtCreditAmount; }
	public void setDebtCreditAmount(BigDecimal debtCreditAmount) { this.debtCreditAmount = debtCreditAmount; }
	public BigDecimal getSettlementAmount() { return settlementAmount; }
	public void setSettlementAmount(BigDecimal settlementAmount) { this.settlementAmount = settlementAmount; }
	public List<SalesReturnLineEntity> getLines() { return lines; }

	public void addLine(SalesReturnLineEntity line) {
		line.setSalesReturn(this);
		lines.add(line);
	}
}
