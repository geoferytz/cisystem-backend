package com.cosmetics.inventory.inventory;

import com.cosmetics.inventory.product.ProductBatchEntity;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "inventory", uniqueConstraints = {
		@UniqueConstraint(name = "uq_inventory_batch_location", columnNames = {"batch_id", "location"})
})
public class InventoryItemEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "batch_id", nullable = false)
	private ProductBatchEntity batch;

	@Column(nullable = false, length = 120)
	private String location = "MAIN";

	@Column(name = "qty_on_hand", nullable = false)
	private int qtyOnHand;

	/** Set by a manual stock adjustment; cleared once the product is sold again at this location. */
	@Column(name = "adjusted_at")
	private Instant adjustedAt;

	public Instant getAdjustedAt() {
		return adjustedAt;
	}

	public void setAdjustedAt(Instant adjustedAt) {
		this.adjustedAt = adjustedAt;
	}

	public Long getId() {
		return id;
	}

	public ProductBatchEntity getBatch() {
		return batch;
	}

	public void setBatch(ProductBatchEntity batch) {
		this.batch = batch;
	}

	public String getLocation() {
		return location;
	}

	public void setLocation(String location) {
		this.location = location;
	}

	public int getQtyOnHand() {
		return qtyOnHand;
	}

	public void setQtyOnHand(int qtyOnHand) {
		this.qtyOnHand = qtyOnHand;
	}
}
