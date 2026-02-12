package com.cosmetics.inventory.sales;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SalesDeductionRepository extends JpaRepository<SalesDeductionEntity, Long> {
	boolean existsByBatchId(Long batchId);
}
