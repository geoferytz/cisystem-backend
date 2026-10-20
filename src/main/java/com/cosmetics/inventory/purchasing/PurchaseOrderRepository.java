package com.cosmetics.inventory.purchasing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrderEntity, Long> {
	List<PurchaseOrderEntity> findAllByOrderByIdDesc();
}
