package com.cosmetics.inventory.transfer;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "stock_transfers")
public class StockTransferEntity {
	public enum Status { PENDING, DISPATCHED, RECEIVED, CANCELLED }

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 60)
	private String fromBranch;

	@Column(nullable = false, length = 60)
	private String toBranch;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Status status = Status.PENDING;

	@Column(length = 40)
	private String reference;

	@Column(length = 500)
	private String note;

	@Column(nullable = false)
	private Instant createdAt = Instant.now();

	@Column(length = 320)
	private String createdBy;

	private Instant dispatchedAt;

	@Column(length = 320)
	private String dispatchedBy;

	private Instant receivedAt;

	@Column(length = 320)
	private String receivedBy;

	@ElementCollection
	@CollectionTable(name = "stock_transfer_lines", joinColumns = @JoinColumn(name = "transfer_id"))
	@OrderColumn(name = "line_index")
	private List<Line> lines = new ArrayList<>();

	@Embeddable
	public static class Line {
		private Long productId;
		private Long batchId;
		@Column(length = 80)
		private String sku;
		@Column(length = 250)
		private String productName;
		@Column(length = 80)
		private String batchNumber;
		private int quantity;

		public Long getProductId() { return productId; }
		public void setProductId(Long productId) { this.productId = productId; }
		public Long getBatchId() { return batchId; }
		public void setBatchId(Long batchId) { this.batchId = batchId; }
		public String getSku() { return sku; }
		public void setSku(String sku) { this.sku = sku; }
		public String getProductName() { return productName; }
		public void setProductName(String productName) { this.productName = productName; }
		public String getBatchNumber() { return batchNumber; }
		public void setBatchNumber(String batchNumber) { this.batchNumber = batchNumber; }
		public int getQuantity() { return quantity; }
		public void setQuantity(int quantity) { this.quantity = quantity; }
	}

	public Long getId() { return id; }
	public String getFromBranch() { return fromBranch; }
	public void setFromBranch(String fromBranch) { this.fromBranch = fromBranch; }
	public String getToBranch() { return toBranch; }
	public void setToBranch(String toBranch) { this.toBranch = toBranch; }
	public Status getStatus() { return status; }
	public void setStatus(Status status) { this.status = status; }
	public String getReference() { return reference; }
	public void setReference(String reference) { this.reference = reference; }
	public String getNote() { return note; }
	public void setNote(String note) { this.note = note; }
	public Instant getCreatedAt() { return createdAt; }
	public String getCreatedBy() { return createdBy; }
	public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
	public Instant getDispatchedAt() { return dispatchedAt; }
	public void setDispatchedAt(Instant dispatchedAt) { this.dispatchedAt = dispatchedAt; }
	public String getDispatchedBy() { return dispatchedBy; }
	public void setDispatchedBy(String dispatchedBy) { this.dispatchedBy = dispatchedBy; }
	public Instant getReceivedAt() { return receivedAt; }
	public void setReceivedAt(Instant receivedAt) { this.receivedAt = receivedAt; }
	public String getReceivedBy() { return receivedBy; }
	public void setReceivedBy(String receivedBy) { this.receivedBy = receivedBy; }
	public List<Line> getLines() { return lines; }
}
