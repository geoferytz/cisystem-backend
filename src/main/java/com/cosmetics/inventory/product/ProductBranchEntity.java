package com.cosmetics.inventory.product;

import jakarta.persistence.*;

/**
 * Associates a product with a branch: the product is "registered" to that
 * branch so it can be stocked/sold there, independent of current stock.
 */
@Entity
@Table(name = "product_branches", uniqueConstraints = {
		@UniqueConstraint(name = "uq_product_branch", columnNames = {"product_id", "branch_code"})
})
public class ProductBranchEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "product_id", nullable = false)
	private ProductEntity product;

	@Column(name = "branch_code", nullable = false, length = 40)
	private String branchCode;

	public Long getId() {
		return id;
	}

	public ProductEntity getProduct() {
		return product;
	}

	public void setProduct(ProductEntity product) {
		this.product = product;
	}

	public String getBranchCode() {
		return branchCode;
	}

	public void setBranchCode(String branchCode) {
		this.branchCode = branchCode;
	}
}
