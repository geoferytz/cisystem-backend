package com.cosmetics.inventory.purchasing;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface RepurchaseOrderRepository extends JpaRepository<RepurchaseOrderEntity, String> {
    List<RepurchaseOrderEntity> findByReceivedPurchaseIdIsNullOrderByCreatedAtDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from RepurchaseOrderEntity o where o.id = ?1")
    Optional<RepurchaseOrderEntity> lockById(String id);
}
