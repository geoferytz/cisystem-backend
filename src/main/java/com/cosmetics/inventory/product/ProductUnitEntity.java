package com.cosmetics.inventory.product;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "product_units")
public class ProductUnitEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "product_id", nullable = false)
	private ProductEntity product;

	@Column(nullable = false, length = 80)
	private String name;

	@Column(precision = 19, scale = 4)
	private BigDecimal price;

	@Column(precision = 19, scale = 4)
	private BigDecimal buyingPrice;

	@Column(nullable = false)
	private int quantity = 1;

	public Long getId() {
		return id;
	}

	public ProductEntity getProduct() {
		return product;
	}

	public void setProduct(ProductEntity product) {
		this.product = product;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public BigDecimal getPrice() {
		return price;
	}

	public void setPrice(BigDecimal price) {
		this.price = price;
	}

	public BigDecimal getBuyingPrice() {
		return buyingPrice;
	}

	public void setBuyingPrice(BigDecimal buyingPrice) {
		this.buyingPrice = buyingPrice;
	}

	public int getQuantity() {
		return quantity;
	}

	public void setQuantity(int quantity) {
		this.quantity = quantity;
	}
}
