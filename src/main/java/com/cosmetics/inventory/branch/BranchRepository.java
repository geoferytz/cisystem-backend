package com.cosmetics.inventory.branch;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BranchRepository extends JpaRepository<BranchEntity, Long> {
	Optional<BranchEntity> findByCodeIgnoreCase(String code);

	Optional<BranchEntity> findByNameIgnoreCase(String name);

	List<BranchEntity> findTop200ByNameContainingIgnoreCaseOrderByNameAsc(String name);

	List<BranchEntity> findTop200ByActiveOrderByNameAsc(boolean active);

	Optional<BranchEntity> findFirstByMainTrue();
}
