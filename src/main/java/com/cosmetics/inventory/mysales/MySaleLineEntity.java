package com.cosmetics.inventory.mysales;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "my_sale_lines")
public class MySaleLineEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "my_sale_id", nullable = false)
	private MySaleEntity mySale;

	@Column(nullable = false, length = 240)
	private String productName;

	@Column(nullable = false)
	private int quantity;

	@Column(nullable = false, precision = 19, scale = 4)
	private BigDecimal unitPrice;

	public Long getId() {
		return id;
	}

	public MySaleEntity getMySale() {
		return mySale;
	}

	public void setMySale(MySaleEntity mySale) {
		this.mySale = mySale;
	}

	public String getProductName() {
		return productName;
	}

	public void setProductName(String productName) {
		this.productName = productName;
	}

	public int getQuantity() {
		return quantity;
	}

	public void setQuantity(int quantity) {
		this.quantity = quantity;
	}

	public BigDecimal getUnitPrice() {
		return unitPrice;
	}

	public void setUnitPrice(BigDecimal unitPrice) {
		this.unitPrice = unitPrice;
	}
}
