package com.cosmetics.inventory.returns;

import com.cosmetics.inventory.product.ProductBatchEntity;
import com.cosmetics.inventory.product.ProductEntity;
import com.cosmetics.inventory.sales.SalesOrderLineEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "sales_return_lines")
public class SalesReturnLineEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "sales_return_id", nullable = false)
	private SalesReturnEntity salesReturn;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "sales_order_line_id", nullable = false)
	private SalesOrderLineEntity salesOrderLine;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "product_id", nullable = false)
	private ProductEntity product;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "batch_id")
	private ProductBatchEntity batch;

	@Column(nullable = false)
	private int quantity;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal unitPrice;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ReturnCondition condition = ReturnCondition.RESALABLE;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal lossAmount = BigDecimal.ZERO;

	public Long getId() { return id; }
	public SalesReturnEntity getSalesReturn() { return salesReturn; }
	public void setSalesReturn(SalesReturnEntity salesReturn) { this.salesReturn = salesReturn; }
	public SalesOrderLineEntity getSalesOrderLine() { return salesOrderLine; }
	public void setSalesOrderLine(SalesOrderLineEntity salesOrderLine) { this.salesOrderLine = salesOrderLine; }
	public ProductEntity getProduct() { return product; }
	public void setProduct(ProductEntity product) { this.product = product; }
	public ProductBatchEntity getBatch() { return batch; }
	public void setBatch(ProductBatchEntity batch) { this.batch = batch; }
	public int getQuantity() { return quantity; }
	public void setQuantity(int quantity) { this.quantity = quantity; }
	public BigDecimal getUnitPrice() { return unitPrice; }
	public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
	public ReturnCondition getCondition() { return condition; }
	public void setCondition(ReturnCondition condition) { this.condition = condition; }
	public BigDecimal getLossAmount() { return lossAmount; }
	public void setLossAmount(BigDecimal lossAmount) { this.lossAmount = lossAmount; }
}
