package com.cosmetics.inventory.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductBranchRepository extends JpaRepository<ProductBranchEntity, Long> {

	@Query("select pb.product.id from ProductBranchEntity pb where lower(pb.branchCode) = lower(:branch)")
	List<Long> findProductIdsByBranch(@Param("branch") String branchCode);

	boolean existsByProductIdAndBranchCodeIgnoreCase(Long productId, String branchCode);
}
