package com.cosmetics.inventory.expense;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "expenses")
public class ExpenseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private LocalDate expenseDate = LocalDate.now();

	@ManyToOne(optional = false, fetch = FetchType.EAGER)
	@JoinColumn(name = "category_id", nullable = false)
	private ExpenseCategoryEntity category;

	@Column(length = 500)
	private String description;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal amount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ExpensePaymentMethod paymentMethod;

	@Column(nullable = false)
	private Instant createdAt = Instant.now();

	@Column(length = 320)
	private String createdBy;

	public Long getId() {
		return id;
	}

	public LocalDate getExpenseDate() {
		return expenseDate;
	}

	public void setExpenseDate(LocalDate expenseDate) {
		this.expenseDate = expenseDate;
	}

	public ExpenseCategoryEntity getCategory() {
		return category;
	}

	public void setCategory(ExpenseCategoryEntity category) {
		this.category = category;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}

	public ExpensePaymentMethod getPaymentMethod() {
		return paymentMethod;
	}

	public void setPaymentMethod(ExpensePaymentMethod paymentMethod) {
		this.paymentMethod = paymentMethod;
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
}
