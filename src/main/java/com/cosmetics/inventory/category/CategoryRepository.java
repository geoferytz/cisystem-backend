package com.cosmetics.inventory.category;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<CategoryEntity, Long> {
	Optional<CategoryEntity> findByNameIgnoreCase(String name);

	List<CategoryEntity> findTop200ByNameContainingIgnoreCaseOrderByNameAsc(String name);

	List<CategoryEntity> findTop200ByActiveOrderByNameAsc(boolean active);
}
