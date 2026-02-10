package com.cosmetics.inventory.sales;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface SalesOrderRepository extends JpaRepository<SalesOrderEntity, Long> {
	List<SalesOrderEntity> findBySoldAtGreaterThanEqualAndSoldAtLessThan(Instant fromInclusive, Instant toExclusive);
}
