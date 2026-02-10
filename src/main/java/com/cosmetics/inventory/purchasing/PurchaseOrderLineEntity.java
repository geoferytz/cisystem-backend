package com.cosmetics.inventory.purchasing;

import com.cosmetics.inventory.product.ProductBatchEntity;
import com.cosmetics.inventory.product.ProductEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "purchase_order_lines")
public class PurchaseOrderLineEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "purchase_order_id", nullable = false)
	private PurchaseOrderEntity purchaseOrder;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "product_id", nullable = false)
	private ProductEntity product;

	@OneToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "batch_id", nullable = false)
	private ProductBatchEntity batch;

	@Column(nullable = false, precision = 19, scale = 4)
	private BigDecimal costPrice;

	@Column(nullable = false)
	private int quantityReceived;

	public Long getId() {
		return id;
	}

	public PurchaseOrderEntity getPurchaseOrder() {
		return purchaseOrder;
	}

	public void setPurchaseOrder(PurchaseOrderEntity purchaseOrder) {
		this.purchaseOrder = purchaseOrder;
	}

	public ProductEntity getProduct() {
		return product;
	}

	public void setProduct(ProductEntity product) {
		this.product = product;
	}

	public ProductBatchEntity getBatch() {
		return batch;
	}

	public void setBatch(ProductBatchEntity batch) {
		this.batch = batch;
	}

	public BigDecimal getCostPrice() {
		return costPrice;
	}

	public void setCostPrice(BigDecimal costPrice) {
		this.costPrice = costPrice;
	}

	public int getQuantityReceived() {
		return quantityReceived;
	}

	public void setQuantityReceived(int quantityReceived) {
		this.quantityReceived = quantityReceived;
	}
}
