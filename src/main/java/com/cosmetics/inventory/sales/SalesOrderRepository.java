package com.cosmetics.inventory.sales;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SalesOrderRepository extends JpaRepository<SalesOrderEntity, Long> {
	List<SalesOrderEntity> findBySoldAtGreaterThanEqualAndSoldAtLessThan(Instant fromInclusive, Instant toExclusive);

	List<SalesOrderEntity> findAllByOrderBySoldAtDescIdDesc();

	long countBySoldAtGreaterThanEqual(Instant fromInclusive);

	boolean existsByReferenceNumber(String referenceNumber);

	@Query("select o from SalesOrderEntity o left join fetch o.lines where o.id = :id")
	Optional<SalesOrderEntity> findWithLinesById(@Param("id") Long id);

	@Query("select o from SalesOrderEntity o left join fetch o.lines where o.customerRef.id = :customerId order by o.soldAt asc, o.id asc")
	List<SalesOrderEntity> findWithLinesByCustomerId(@Param("customerId") Long customerId);
}
