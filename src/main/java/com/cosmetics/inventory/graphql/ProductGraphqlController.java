package com.cosmetics.inventory.graphql;

import com.cosmetics.inventory.product.ProductBatchEntity;
import com.cosmetics.inventory.product.ProductBatchRepository;
import com.cosmetics.inventory.product.ProductEntity;
import com.cosmetics.inventory.product.ProductRepository;
import com.cosmetics.inventory.product.ProductService;
import com.cosmetics.inventory.product.ProductUnitEntity;
import com.cosmetics.inventory.product.ProductUnitRepository;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import com.cosmetics.inventory.branch.BranchScope;
import com.cosmetics.inventory.user.PermissionGuard;
import com.cosmetics.inventory.user.PermissionModule;
import com.cosmetics.inventory.user.PermissionsService;

import java.util.List;

@Controller
public class ProductGraphqlController {
	private final ProductService productService;
	private final ProductRepository productRepository;
	private final ProductBatchRepository batchRepository;
	private final ProductUnitRepository unitRepository;
	private final PermissionGuard permissionGuard;
	private final BranchScope branchScope;
	private final com.cosmetics.inventory.inventory.InventoryRepository inventoryRepository;
	private final com.cosmetics.inventory.product.ProductBranchRepository productBranchRepository;

	public ProductGraphqlController(ProductService productService, ProductRepository productRepository, ProductBatchRepository batchRepository, ProductUnitRepository unitRepository, PermissionGuard permissionGuard, BranchScope branchScope,
			com.cosmetics.inventory.inventory.InventoryRepository inventoryRepository, com.cosmetics.inventory.product.ProductBranchRepository productBranchRepository) {
		this.productService = productService;
		this.productRepository = productRepository;
		this.batchRepository = batchRepository;
		this.unitRepository = unitRepository;
		this.permissionGuard = permissionGuard;
		this.branchScope = branchScope;
		this.inventoryRepository = inventoryRepository;
		this.productBranchRepository = productBranchRepository;
	}

	@QueryMapping
	@PreAuthorize("isAuthenticated()")
	@org.springframework.transaction.annotation.Transactional(readOnly = true)
	public List<ProductEntity> products(@Argument ProductFilter filter, Authentication authentication) {
		permissionGuard.require(authentication, PermissionModule.PRODUCTS, PermissionsService.PermissionAction.VIEW);
		String query = filter != null ? filter.query() : null;
		Boolean active = filter != null ? filter.active() : null;
		List<ProductEntity> list = productService.findProducts(query, active);
		// Branch selected: only products that have stock/batches recorded under that branch.
		String scoped = branchScope.scopeFilter(authentication, filter != null ? filter.branch() : null);
		if (scoped != null) {
			java.util.Set<Long> ids = new java.util.HashSet<>(productBranchRepository.findProductIdsByBranch(scoped));
			for (var item : inventoryRepository.findAll()) {
				if (item.getLocation() != null && item.getLocation().equalsIgnoreCase(scoped) && item.getBatch() != null) {
					ids.add(item.getBatch().getProduct().getId());
				}
			}
			list = list.stream().filter(p -> ids.contains(p.getId())).toList();
		}
		return list;
	}

	@QueryMapping
	@PreAuthorize("isAuthenticated()")
	public ProductEntity product(@Argument long id, Authentication authentication) {
		permissionGuard.require(authentication, PermissionModule.PRODUCTS, PermissionsService.PermissionAction.VIEW);
		return productRepository.findById(id).orElse(null);
	}

	@MutationMapping
	@PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
	public ProductEntity createProduct(@Argument CreateProductInput input, Authentication authentication) {
		permissionGuard.require(authentication, PermissionModule.PRODUCTS, PermissionsService.PermissionAction.CREATE);
		return productService.createProduct(new ProductService.CreateProductCommand(
				input.sku(),
				input.barcode(),
				input.name(),
				input.brand(),
				input.category(),
				input.variant(),
				input.unitOfMeasure(),
				input.buyingPrice(),
				input.sellingPrice(),
				input.active(),
				toUnitInputs(input.units())
		));
	}

	@MutationMapping
	@PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
	public ProductEntity updateProduct(@Argument UpdateProductInput input, Authentication authentication) {
		permissionGuard.require(authentication, PermissionModule.PRODUCTS, PermissionsService.PermissionAction.EDIT);
		return productService.updateProduct(new ProductService.UpdateProductCommand(
				input.id(),
				input.sku(),
				input.barcode(),
				input.name(),
				input.brand(),
				input.category(),
				input.variant(),
				input.unitOfMeasure(),
				input.buyingPrice(),
				input.sellingPrice(),
				input.units() != null ? toUnitInputs(input.units()) : null
		));
	}

	@MutationMapping
	@PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
	public ProductEntity setProductStatus(@Argument SetProductStatusInput input, Authentication authentication) {
		permissionGuard.require(authentication, PermissionModule.PRODUCTS, PermissionsService.PermissionAction.EDIT);
		return productService.setProductStatus(input.id(), input.active());
	}

	/**
	 * Bulk-register existing products to a branch: marks them as belonging to
	 * that branch so they show up there before any stock arrives.
	 * Products already present (via inventory or registration) are skipped.
	 */
	@MutationMapping
	@PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
	@org.springframework.transaction.annotation.Transactional
	public int registerProductsToBranch(@Argument RegisterProductsToBranchInput input, Authentication authentication) {
		permissionGuard.require(authentication, PermissionModule.PRODUCTS, PermissionsService.PermissionAction.CREATE);
		String branch = branchScope.resolveLocation(authentication, input.branch());
		int added = 0;
		java.util.Set<Long> inBranch = new java.util.HashSet<>(productBranchRepository.findProductIdsByBranch(branch));
		for (var item : inventoryRepository.findAll()) {
			if (item.getLocation() != null && item.getLocation().equalsIgnoreCase(branch) && item.getBatch() != null) {
				inBranch.add(item.getBatch().getProduct().getId());
			}
		}
		for (Long pid : input.productIds()) {
			if (pid == null || inBranch.contains(pid)) continue;
			var product = productRepository.findById(pid).orElse(null);
			if (product == null) continue;
			var pb = new com.cosmetics.inventory.product.ProductBranchEntity();
			pb.setProduct(product);
			pb.setBranchCode(branch);
			productBranchRepository.save(pb);
			inBranch.add(pid);
			added++;
		}
		return added;
	}

	@MutationMapping
	@PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
	public ProductBatchEntity createBatch(@Argument CreateBatchInput input, Authentication authentication) {
		permissionGuard.require(authentication, PermissionModule.PRODUCTS, PermissionsService.PermissionAction.CREATE);
		return productService.createBatch(new ProductService.CreateBatchCommand(
				input.productId(),
				input.batchNumber(),
				input.expiryDate(),
				input.costPrice(),
				input.quantityReceived(),
				branchScope.resolveLocation(authentication, input.location())
		), authentication);
	}

	@MutationMapping
	@PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
	public ProductBatchEntity updateBatchNumber(@Argument UpdateBatchNumberInput input, Authentication authentication) {
		permissionGuard.require(authentication, PermissionModule.PRODUCTS, PermissionsService.PermissionAction.EDIT);
		return productService.updateBatchNumber(input.batchId(), input.batchNumber());
	}

	@SchemaMapping(typeName = "Product", field = "batches")
	@PreAuthorize("isAuthenticated()")
	public List<ProductBatchEntity> batches(ProductEntity product, Authentication authentication) {
		permissionGuard.require(authentication, PermissionModule.PRODUCTS, PermissionsService.PermissionAction.VIEW);
		return batchRepository.findByProductIdOrderByExpiryDateAscCreatedAtAsc(product.getId());
	}

	@SchemaMapping(typeName = "ProductBatch", field = "productId")
	@PreAuthorize("isAuthenticated()")
	public Long productId(ProductBatchEntity batch) {
		return batch.getProduct().getId();
	}

	@SchemaMapping(typeName = "ProductBatch", field = "expiryDate")
	@PreAuthorize("isAuthenticated()")
	public String expiryDate(ProductBatchEntity batch) {
		return batch.getExpiryDate().toString();
	}

	@SchemaMapping(typeName = "ProductBatch", field = "createdAt")
	@PreAuthorize("isAuthenticated()")
	public String createdAt(ProductBatchEntity batch) {
		return batch.getCreatedAt().toString();
	}

	@SchemaMapping(typeName = "Product", field = "units")
	@PreAuthorize("isAuthenticated()")
	public List<ProductUnitEntity> units(ProductEntity product, Authentication authentication) {
		permissionGuard.require(authentication, PermissionModule.PRODUCTS, PermissionsService.PermissionAction.VIEW);
		return unitRepository.findByProductIdOrderByIdAsc(product.getId());
	}

	@SchemaMapping(typeName = "ProductUnit", field = "price")
	@PreAuthorize("isAuthenticated()")
	public Double unitPrice(ProductUnitEntity unit) {
		return unit.getPrice() != null ? unit.getPrice().doubleValue() : null;
	}

	@SchemaMapping(typeName = "ProductUnit", field = "buyingPrice")
	@PreAuthorize("isAuthenticated()")
	public Double unitBuyingPrice(ProductUnitEntity unit) {
		return unit.getBuyingPrice() != null ? unit.getBuyingPrice().doubleValue() : null;
	}

	private static List<ProductService.UnitInput> toUnitInputs(List<ProductUnitInput> units) {
		if (units == null) return null;
		return units.stream()
				.map(u -> new ProductService.UnitInput(u.name(), u.price(), u.buyingPrice(), u.quantity()))
				.toList();
	}

	@SchemaMapping(typeName = "ProductBatch", field = "costPrice")
	@PreAuthorize("isAuthenticated()")
	public double costPrice(ProductBatchEntity batch) {
		return batch.getCostPrice().doubleValue();
	}

	public record ProductFilter(String query, Boolean active, String branch) {
	}

	public record RegisterProductsToBranchInput(String branch, List<Long> productIds) {
	}

	public record ProductUnitInput(String name, Double price, Double buyingPrice, Integer quantity) {
	}

	public record CreateProductInput(String sku, String barcode, String name, String brand, String category, String variant, String unitOfMeasure, Double buyingPrice, Double sellingPrice, Boolean active, List<ProductUnitInput> units) {
	}

	public record UpdateProductInput(long id, String sku, String barcode, String name, String brand, String category, String variant, String unitOfMeasure, Double buyingPrice, Double sellingPrice, List<ProductUnitInput> units) {
	}

	public record SetProductStatusInput(long id, boolean active) {
	}

	public record CreateBatchInput(long productId, String batchNumber, String expiryDate, double costPrice, int quantityReceived, String location) {
	}

	public record UpdateBatchNumberInput(long batchId, String batchNumber) {
	}
}
