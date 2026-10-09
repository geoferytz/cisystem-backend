package com.cosmetics.inventory.product;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductUnitRepository extends JpaRepository<ProductUnitEntity, Long> {
	List<ProductUnitEntity> findByProductIdOrderByIdAsc(Long productId);
}
