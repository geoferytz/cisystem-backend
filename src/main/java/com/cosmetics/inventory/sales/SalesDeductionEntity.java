package com.cosmetics.inventory.sales;

import com.cosmetics.inventory.product.ProductBatchEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "sales_deductions")
public class SalesDeductionEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "sales_order_line_id", nullable = false)
	private SalesOrderLineEntity salesOrderLine;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "batch_id", nullable = false)
	private ProductBatchEntity batch;

	@Column(nullable = false)
	private int quantity;

	public Long getId() {
		return id;
	}

	public SalesOrderLineEntity getSalesOrderLine() {
		return salesOrderLine;
	}

	public void setSalesOrderLine(SalesOrderLineEntity salesOrderLine) {
		this.salesOrderLine = salesOrderLine;
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
}
