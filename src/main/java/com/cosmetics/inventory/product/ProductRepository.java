package com.cosmetics.inventory.product;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<ProductEntity, Long> {
	Optional<ProductEntity> findBySkuIgnoreCase(String sku);
	Optional<ProductEntity> findTopByOrderByIdDesc();

	List<ProductEntity> findTop200ByNameContainingIgnoreCaseOrSkuContainingIgnoreCaseOrBarcodeContainingIgnoreCaseOrderByNameAsc(
			String name,
			String sku,
			String barcode
	);

	List<ProductEntity> findTop200ByActiveOrderByNameAsc(boolean active);

	List<ProductEntity> findAllByOrderByIdDesc();
}
