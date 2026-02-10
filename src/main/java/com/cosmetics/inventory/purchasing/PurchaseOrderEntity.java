package com.cosmetics.inventory.purchasing;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "purchase_orders")
public class PurchaseOrderEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(length = 200)
	private String supplier;

	@Column(length = 120)
	private String invoiceNumber;

	@Column(nullable = false)
	private Instant receivedAt = Instant.now();

	@Column(length = 320)
	private String receivedBy;

	@OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<PurchaseOrderLineEntity> lines = new ArrayList<>();

	public Long getId() {
		return id;
	}

	public String getSupplier() {
		return supplier;
	}

	public void setSupplier(String supplier) {
		this.supplier = supplier;
	}

	public String getInvoiceNumber() {
		return invoiceNumber;
	}

	public void setInvoiceNumber(String invoiceNumber) {
		this.invoiceNumber = invoiceNumber;
	}

	public Instant getReceivedAt() {
		return receivedAt;
	}

	public String getReceivedBy() {
		return receivedBy;
	}

	public void setReceivedBy(String receivedBy) {
		this.receivedBy = receivedBy;
	}

	public List<PurchaseOrderLineEntity> getLines() {
		return lines;
	}

	public void addLine(PurchaseOrderLineEntity line) {
		lines.add(line);
		line.setPurchaseOrder(this);
	}
}
