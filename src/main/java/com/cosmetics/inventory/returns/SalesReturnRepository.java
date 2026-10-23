package com.cosmetics.inventory.returns;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SalesReturnRepository extends JpaRepository<SalesReturnEntity, Long> {
	boolean existsByReference(String reference);

	long countByReturnedAtGreaterThanEqual(Instant fromInclusive);

	@Query("select distinct r from SalesReturnEntity r left join fetch r.lines order by r.returnedAt desc, r.id desc")
	List<SalesReturnEntity> findAllWithLines();

	@Query("select distinct r from SalesReturnEntity r left join fetch r.lines where r.returnedAt >= :from and r.returnedAt < :to order by r.returnedAt desc, r.id desc")
	List<SalesReturnEntity> findWithLinesBetween(@Param("from") Instant from, @Param("to") Instant to);

	@Query("select distinct r from SalesReturnEntity r left join fetch r.lines where r.id = :id")
	Optional<SalesReturnEntity> findWithLinesById(@Param("id") Long id);

	@Query("select distinct r from SalesReturnEntity r left join fetch r.lines where r.originalSalesOrder.id = :orderId")
	List<SalesReturnEntity> findWithLinesByOriginalOrderId(@Param("orderId") Long orderId);
}
