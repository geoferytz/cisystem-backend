package com.cosmetics.inventory.expense;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class ExpenseService {
	private final ExpenseRepository expenseRepository;
	private final ExpenseCategoryRepository categoryRepository;

	public ExpenseService(ExpenseRepository expenseRepository, ExpenseCategoryRepository categoryRepository) {
		this.expenseRepository = expenseRepository;
		this.categoryRepository = categoryRepository;
	}

	@Transactional(readOnly = true)
	public List<ExpenseEntity> findExpenses(LocalDate from, LocalDate to) {
		if (from != null && to != null) {
			return expenseRepository.findTop500ByExpenseDateBetweenOrderByExpenseDateDescIdDesc(from, to);
		}
		return expenseRepository.findTop500ByOrderByExpenseDateDescIdDesc();
	}

	@Transactional
	public ExpenseEntity create(CreateExpenseCommand cmd, String createdBy) {
		if (cmd.categoryId() == null) {
			throw new IllegalArgumentException("Category is required");
		}
		ExpenseCategoryEntity cat = categoryRepository.findById(cmd.categoryId()).orElseThrow();
		if (!cat.isActive()) {
			throw new IllegalArgumentException("Category is inactive");
		}
		if (cmd.amount() == null) {
			throw new IllegalArgumentException("Amount is required");
		}
		BigDecimal amt = BigDecimal.valueOf(cmd.amount());
		if (amt.compareTo(BigDecimal.ZERO) < 0) {
			throw new IllegalArgumentException("Amount must be 0 or more");
		}
		if (cmd.paymentMethod() == null) {
			throw new IllegalArgumentException("Payment method is required");
		}

		ExpenseEntity e = new ExpenseEntity();
		e.setExpenseDate(cmd.expenseDate() != null ? cmd.expenseDate() : LocalDate.now());
		e.setCategory(cat);
		e.setDescription(cmd.description());
		e.setAmount(amt);
		e.setPaymentMethod(cmd.paymentMethod());
		e.setCreatedBy(createdBy);
		return expenseRepository.save(e);
	}

	@Transactional
	public ExpenseEntity update(UpdateExpenseCommand cmd) {
		ExpenseEntity e = expenseRepository.findById(cmd.id()).orElseThrow();

		if (cmd.expenseDate() != null) {
			e.setExpenseDate(cmd.expenseDate());
		}

		if (cmd.categoryId() != null) {
			ExpenseCategoryEntity cat = categoryRepository.findById(cmd.categoryId()).orElseThrow();
			if (!cat.isActive()) {
				throw new IllegalArgumentException("Category is inactive");
			}
			e.setCategory(cat);
		}

		if (cmd.description() != null) {
			e.setDescription(cmd.description());
		}

		if (cmd.amount() != null) {
			BigDecimal amt = BigDecimal.valueOf(cmd.amount());
			if (amt.compareTo(BigDecimal.ZERO) < 0) {
				throw new IllegalArgumentException("Amount must be 0 or more");
			}
			e.setAmount(amt);
		}

		if (cmd.paymentMethod() != null) {
			e.setPaymentMethod(cmd.paymentMethod());
		}

		return expenseRepository.save(e);
	}

	@Transactional
	public boolean delete(long id) {
		if (!expenseRepository.existsById(id)) {
			return false;
		}
		expenseRepository.deleteById(id);
		return true;
	}

	public record CreateExpenseCommand(LocalDate expenseDate, Long categoryId, String description, Double amount, ExpensePaymentMethod paymentMethod) {
	}

	public record UpdateExpenseCommand(long id, LocalDate expenseDate, Long categoryId, String description, Double amount, ExpensePaymentMethod paymentMethod) {
	}
}
