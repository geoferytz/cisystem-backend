package com.cosmetics.inventory.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface InventoryRepository extends JpaRepository<InventoryItemEntity, Long> {
	Optional<InventoryItemEntity> findByBatchIdAndLocation(Long batchId, String location);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select i from InventoryItemEntity i where i.batch.id = ?1 and i.location = ?2")
	Optional<InventoryItemEntity> findForUpdate(Long batchId, String location);
}
