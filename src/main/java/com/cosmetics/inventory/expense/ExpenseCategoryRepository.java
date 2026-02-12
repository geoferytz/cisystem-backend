package com.cosmetics.inventory.expense;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategoryEntity, Long> {
	Optional<ExpenseCategoryEntity> findByNameIgnoreCase(String name);

	List<ExpenseCategoryEntity> findTop200ByNameContainingIgnoreCaseOrderByNameAsc(String name);

	List<ExpenseCategoryEntity> findTop200ByActiveOrderByNameAsc(boolean active);
}
