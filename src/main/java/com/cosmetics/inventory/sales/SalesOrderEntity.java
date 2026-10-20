package com.cosmetics.inventory.sales;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sales_orders")
public class SalesOrderEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(length = 200)
	private String customer;

	@Column(length = 120)
	private String referenceNumber;

	@Column(nullable = false)
	private Instant soldAt = Instant.now();

	@Column(length = 320)
	private String soldBy;

	@Column(precision = 19, scale = 2)
	private java.math.BigDecimal discount = java.math.BigDecimal.ZERO;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "customer_id")
	private com.cosmetics.inventory.customer.CustomerEntity customerRef;

	@Column(length = 40)
	private String paymentMethod;

	@Column(precision = 19, scale = 2)
	private java.math.BigDecimal amountReceived;

	@OneToMany(mappedBy = "salesOrder", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<SalesOrderLineEntity> lines = new ArrayList<>();

	public Long getId() {
		return id;
	}

	public String getCustomer() {
		return customer;
	}

	public void setCustomer(String customer) {
		this.customer = customer;
	}

	public String getReferenceNumber() {
		return referenceNumber;
	}

	public void setReferenceNumber(String referenceNumber) {
		this.referenceNumber = referenceNumber;
	}

	public Instant getSoldAt() {
		return soldAt;
	}

	public String getSoldBy() {
		return soldBy;
	}

	public void setSoldBy(String soldBy) {
		this.soldBy = soldBy;
	}

	public java.math.BigDecimal getDiscount() {
		return discount;
	}

	public void setDiscount(java.math.BigDecimal discount) {
		this.discount = discount;
	}

	public com.cosmetics.inventory.customer.CustomerEntity getCustomerRef() {
		return customerRef;
	}

	public void setCustomerRef(com.cosmetics.inventory.customer.CustomerEntity customerRef) {
		this.customerRef = customerRef;
	}

	public String getPaymentMethod() {
		return paymentMethod;
	}

	public void setPaymentMethod(String paymentMethod) {
		this.paymentMethod = paymentMethod;
	}

	public java.math.BigDecimal getAmountReceived() {
		return amountReceived;
	}

	public void setAmountReceived(java.math.BigDecimal amountReceived) {
		this.amountReceived = amountReceived;
	}

	public List<SalesOrderLineEntity> getLines() {
		return lines;
	}

	public void addLine(SalesOrderLineEntity line) {
		lines.add(line);
		line.setSalesOrder(this);
	}
}
