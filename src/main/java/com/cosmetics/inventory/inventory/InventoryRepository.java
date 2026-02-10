package com.cosmetics.inventory.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InventoryRepository extends JpaRepository<InventoryItemEntity, Long> {
	Optional<InventoryItemEntity> findByBatchIdAndLocation(Long batchId, String location);
}
