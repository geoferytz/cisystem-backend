package com.cosmetics.inventory.stockmovement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovementEntity, Long> {
	List<StockMovementEntity> findAllByOrderByCreatedAtDesc();
}
