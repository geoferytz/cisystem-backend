package com.cosmetics.inventory.graphql;

import com.cosmetics.inventory.product.ProductBatchEntity;
import com.cosmetics.inventory.product.ProductBatchRepository;
import com.cosmetics.inventory.product.ProductEntity;
import com.cosmetics.inventory.product.ProductRepository;
import com.cosmetics.inventory.product.ProductService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import com.cosmetics.inventory.user.PermissionGuard;
import com.cosmetics.inventory.user.PermissionModule;
import com.cosmetics.inventory.user.PermissionsService;

import java.util.List;

@Controller
public class ProductGraphqlController {
	private final ProductService productService;
	private final ProductRepository productRepository;
	private final ProductBatchRepository batchRepository;
	private final PermissionGuard permissionGuard;

	public ProductGraphqlController(ProductService productService, ProductRepository productRepository, ProductBatchRepository batchRepository, PermissionGuard permissionGuard) {
		this.productService = productService;
		this.productRepository = productRepository;
		this.batchRepository = batchRepository;
		this.permissionGuard = permissionGuard;
	}

	@QueryMapping
	@PreAuthorize("isAuthenticated()")
	public List<ProductEntity> products(@Argument ProductFilter filter, Authentication authentication) {
		permissionGuard.require(authentication, PermissionModule.PRODUCTS, PermissionsService.PermissionAction.VIEW);
		String query = filter != null ? filter.query() : null;
		Boolean active = filter != null ? filter.active() : null;
		return productService.findProducts(query, active);
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
				input.sellingPrice()
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
				input.sellingPrice()
		));
	}

	@MutationMapping
	@PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
	public ProductEntity setProductStatus(@Argument SetProductStatusInput input, Authentication authentication) {
		permissionGuard.require(authentication, PermissionModule.PRODUCTS, PermissionsService.PermissionAction.EDIT);
		return productService.setProductStatus(input.id(), input.active());
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
				input.location()
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

	@SchemaMapping(typeName = "ProductBatch", field = "costPrice")
	@PreAuthorize("isAuthenticated()")
	public double costPrice(ProductBatchEntity batch) {
		return batch.getCostPrice().doubleValue();
	}

	public record ProductFilter(String query, Boolean active) {
	}

	public record CreateProductInput(String sku, String barcode, String name, String brand, String category, String variant, String unitOfMeasure, Double buyingPrice, Double sellingPrice) {
	}

	public record UpdateProductInput(long id, String sku, String barcode, String name, String brand, String category, String variant, String unitOfMeasure, Double buyingPrice, Double sellingPrice) {
	}

	public record SetProductStatusInput(long id, boolean active) {
	}

	public record CreateBatchInput(long productId, String batchNumber, String expiryDate, double costPrice, int quantityReceived, String location) {
	}

	public record UpdateBatchNumberInput(long batchId, String batchNumber) {
	}
}
