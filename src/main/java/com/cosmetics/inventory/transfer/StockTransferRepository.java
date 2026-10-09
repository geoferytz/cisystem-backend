package com.cosmetics.inventory.transfer;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StockTransferRepository extends JpaRepository<StockTransferEntity, Long> {
	List<StockTransferEntity> findTop500ByOrderByCreatedAtDesc();

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select t from StockTransferEntity t where t.id = :id")
	Optional<StockTransferEntity> lockById(@Param("id") long id);
}
