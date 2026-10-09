package com.cosmetics.inventory.sales;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface CounterCartRepository extends JpaRepository<CounterCartEntity, String> {
    List<CounterCartEntity> findByStatusOrderByUpdatedAtDesc(CounterCartEntity.Status status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CounterCartEntity c where c.id = ?1")
    Optional<CounterCartEntity> lockById(String id);
}
