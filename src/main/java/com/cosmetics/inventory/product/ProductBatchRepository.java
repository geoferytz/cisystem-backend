package com.cosmetics.inventory.product;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductBatchRepository extends JpaRepository<ProductBatchEntity, Long> {
	List<ProductBatchEntity> findByProductIdOrderByExpiryDateAscCreatedAtAsc(Long productId);
	Optional<ProductBatchEntity> findByProductIdAndBatchNumberIgnoreCase(Long productId, String batchNumber);
}
