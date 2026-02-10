package com.cosmetics.inventory.product;

import com.cosmetics.inventory.inventory.InventoryItemEntity;
import com.cosmetics.inventory.inventory.InventoryRepository;
import com.cosmetics.inventory.stockmovement.StockMovementEntity;
import com.cosmetics.inventory.stockmovement.StockMovementRepository;
import com.cosmetics.inventory.stockmovement.StockMovementType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class ProductService {
	private final ProductRepository productRepository;
	private final ProductBatchRepository batchRepository;
	private final InventoryRepository inventoryRepository;
	private final StockMovementRepository movementRepository;

	public ProductService(ProductRepository productRepository, ProductBatchRepository batchRepository, InventoryRepository inventoryRepository, StockMovementRepository movementRepository) {
		this.productRepository = productRepository;
		this.batchRepository = batchRepository;
		this.inventoryRepository = inventoryRepository;
		this.movementRepository = movementRepository;
	}

	@Transactional(readOnly = true)
	public List<ProductEntity> findProducts(String query, Boolean active) {
		if (query != null && !query.isBlank()) {
			String q = query.trim();
			return productRepository.findTop200ByNameContainingIgnoreCaseOrSkuContainingIgnoreCaseOrBarcodeContainingIgnoreCaseOrderByNameAsc(q, q, q);
		}
		if (active != null) {
			return productRepository.findTop200ByActiveOrderByNameAsc(active);
		}
		return productRepository.findAll();
	}

	@Transactional
	public ProductEntity createProduct(CreateProductCommand cmd) {
		productRepository.findBySkuIgnoreCase(cmd.sku()).ifPresent(p -> {
			throw new IllegalArgumentException("SKU already exists");
		});

		ProductEntity p = new ProductEntity();
		p.setSku(cmd.sku().trim());
		p.setBarcode(cmd.barcode());
		p.setName(cmd.name().trim());
		p.setBrand(cmd.brand());
		p.setCategory(cmd.category());
		p.setVariant(cmd.variant());
		p.setUnitOfMeasure(cmd.unitOfMeasure());
		p.setBuyingPrice(cmd.buyingPrice() != null ? BigDecimal.valueOf(cmd.buyingPrice()) : null);
		p.setSellingPrice(cmd.sellingPrice() != null ? BigDecimal.valueOf(cmd.sellingPrice()) : null);

		return productRepository.save(p);
	}

	@Transactional
	public ProductEntity updateProduct(UpdateProductCommand cmd) {
		ProductEntity p = productRepository.findById(cmd.id()).orElseThrow();
		if (cmd.sku() != null && !cmd.sku().isBlank() && !cmd.sku().equalsIgnoreCase(p.getSku())) {
			productRepository.findBySkuIgnoreCase(cmd.sku()).ifPresent(existing -> {
				throw new IllegalArgumentException("SKU already exists");
			});
			p.setSku(cmd.sku().trim());
		}
		if (cmd.barcode() != null) p.setBarcode(cmd.barcode());
		if (cmd.name() != null && !cmd.name().isBlank()) p.setName(cmd.name().trim());
		if (cmd.brand() != null) p.setBrand(cmd.brand());
		if (cmd.category() != null) p.setCategory(cmd.category());
		if (cmd.variant() != null) p.setVariant(cmd.variant());
		if (cmd.unitOfMeasure() != null) p.setUnitOfMeasure(cmd.unitOfMeasure());
		if (cmd.buyingPrice() != null) p.setBuyingPrice(BigDecimal.valueOf(cmd.buyingPrice()));
		if (cmd.sellingPrice() != null) p.setSellingPrice(BigDecimal.valueOf(cmd.sellingPrice()));
		return productRepository.save(p);
	}

	@Transactional
	public ProductEntity setProductStatus(long id, boolean active) {
		ProductEntity p = productRepository.findById(id).orElseThrow();
		p.setActive(active);
		return productRepository.save(p);
	}

	@Transactional
	public ProductBatchEntity createBatch(CreateBatchCommand cmd, Authentication authentication) {
		ProductEntity p = productRepository.findById(cmd.productId()).orElseThrow();

		ProductBatchEntity batch = new ProductBatchEntity();
		batch.setProduct(p);
		batch.setBatchNumber(cmd.batchNumber().trim());
		batch.setExpiryDate(LocalDate.parse(cmd.expiryDate()));
		batch.setCostPrice(BigDecimal.valueOf(cmd.costPrice()));
		batch.setQuantityReceived(cmd.quantityReceived());
		ProductBatchEntity persistedBatch = batchRepository.save(batch);

		String location = (cmd.location() != null && !cmd.location().isBlank()) ? cmd.location().trim() : "MAIN";

		InventoryItemEntity inv = inventoryRepository.findByBatchIdAndLocation(persistedBatch.getId(), location).orElseGet(() -> {
			InventoryItemEntity i = new InventoryItemEntity();
			i.setBatch(persistedBatch);
			i.setLocation(location);
			return i;
		});
		inv.setQtyOnHand(inv.getQtyOnHand() + cmd.quantityReceived());
		inventoryRepository.save(inv);

		if (cmd.quantityReceived() > 0) {
			StockMovementEntity mv = new StockMovementEntity();
			mv.setType(StockMovementType.IN);
			mv.setBatch(persistedBatch);
			mv.setQuantity(cmd.quantityReceived());
			mv.setCreatedBy(authentication != null ? String.valueOf(authentication.getPrincipal()) : null);
			movementRepository.save(mv);
		}

		return persistedBatch;
	}

	@Transactional
	public ProductBatchEntity updateBatchNumber(long batchId, String batchNumber) {
		if (batchNumber == null || batchNumber.isBlank()) {
			throw new IllegalArgumentException("Batch number is required");
		}
		ProductBatchEntity batch = batchRepository.findById(batchId).orElseThrow();
		String trimmed = batchNumber.trim();
		if (trimmed.equalsIgnoreCase(batch.getBatchNumber())) {
			return batch;
		}
		Long productId = batch.getProduct().getId();
		batchRepository.findByProductIdAndBatchNumberIgnoreCase(productId, trimmed).ifPresent(existing -> {
			if (!existing.getId().equals(batch.getId())) {
				throw new IllegalArgumentException("Batch number already exists for this product");
			}
		});
		batch.setBatchNumber(trimmed);
		return batchRepository.save(batch);
	}

	public record CreateProductCommand(String sku, String barcode, String name, String brand, String category, String variant, String unitOfMeasure, Double buyingPrice, Double sellingPrice) {
	}

	public record UpdateProductCommand(long id, String sku, String barcode, String name, String brand, String category, String variant, String unitOfMeasure, Double buyingPrice, Double sellingPrice) {
	}

	public record CreateBatchCommand(long productId, String batchNumber, String expiryDate, double costPrice, int quantityReceived, String location) {
	}
}
