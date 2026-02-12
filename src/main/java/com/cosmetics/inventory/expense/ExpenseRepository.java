package com.cosmetics.inventory.expense;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<ExpenseEntity, Long> {
	List<ExpenseEntity> findTop500ByOrderByExpenseDateDescIdDesc();

	List<ExpenseEntity> findTop500ByExpenseDateBetweenOrderByExpenseDateDescIdDesc(LocalDate from, LocalDate to);
}
