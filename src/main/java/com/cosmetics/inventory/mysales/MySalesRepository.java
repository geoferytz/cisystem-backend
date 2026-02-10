package com.cosmetics.inventory.mysales;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MySalesRepository extends JpaRepository<MySaleEntity, Long> {
	List<MySaleEntity> findAllByOrderByCreatedAtDesc();
}
