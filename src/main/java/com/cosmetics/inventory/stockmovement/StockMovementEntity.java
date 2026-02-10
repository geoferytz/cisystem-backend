package com.cosmetics.inventory.stockmovement;

import com.cosmetics.inventory.product.ProductBatchEntity;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "stock_movements")
public class StockMovementEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private StockMovementType type;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "batch_id", nullable = false)
	private ProductBatchEntity batch;

	@Column(nullable = false)
	private int quantity;

	@Column(nullable = false)
	private Instant createdAt = Instant.now();

	@Column(length = 320)
	private String createdBy;

	@Column(length = 500)
	private String note;

	public Long getId() {
		return id;
	}

	public StockMovementType getType() {
		return type;
	}

	public void setType(StockMovementType type) {
		this.type = type;
	}

	public ProductBatchEntity getBatch() {
		return batch;
	}

	public void setBatch(ProductBatchEntity batch) {
		this.batch = batch;
	}

	public int getQuantity() {
		return quantity;
	}

	public void setQuantity(int quantity) {
		this.quantity = quantity;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public String getCreatedBy() {
		return createdBy;
	}

	public void setCreatedBy(String createdBy) {
		this.createdBy = createdBy;
	}

	public String getNote() {
		return note;
	}

	public void setNote(String note) {
		this.note = note;
	}
}
