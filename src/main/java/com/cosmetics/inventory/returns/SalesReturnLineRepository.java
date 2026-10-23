package com.cosmetics.inventory.returns;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalesReturnLineRepository extends JpaRepository<SalesReturnLineEntity, Long> {
	@Query("select coalesce(sum(l.quantity), 0) from SalesReturnLineEntity l where l.salesOrderLine.id = :lineId")
	int sumReturnedQuantityForLine(@Param("lineId") Long lineId);

	@Query("select l.salesOrderLine.id, coalesce(sum(l.quantity), 0) from SalesReturnLineEntity l group by l.salesOrderLine.id")
	java.util.List<Object[]> sumReturnedQuantityByLine();
}
