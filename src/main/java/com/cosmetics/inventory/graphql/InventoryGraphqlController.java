package com.cosmetics.inventory.graphql;

import com.cosmetics.inventory.inventory.InventoryRepository;
import com.cosmetics.inventory.inventory.InventoryItemEntity;
import com.cosmetics.inventory.product.ProductBatchRepository;
import com.cosmetics.inventory.stockmovement.StockMovementEntity;
import com.cosmetics.inventory.stockmovement.StockMovementRepository;
import com.cosmetics.inventory.stockmovement.StockMovementType;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import com.cosmetics.inventory.branch.BranchScope;
import com.cosmetics.inventory.user.PermissionGuard;
import com.cosmetics.inventory.user.PermissionModule;
import com.cosmetics.inventory.user.PermissionsService;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Controller
public class InventoryGraphqlController {
	private final InventoryRepository inventoryRepository;
	private final ProductBatchRepository batchRepository;
	private final StockMovementRepository stockMovementRepository;
	private final PermissionGuard permissionGuard;
	private final BranchScope branchScope;

	public InventoryGraphqlController(InventoryRepository inventoryRepository, ProductBatchRepository batchRepository, StockMovementRepository stockMovementRepository, PermissionGuard permissionGuard, BranchScope branchScope) {
		this.inventoryRepository = inventoryRepository;
		this.batchRepository = batchRepository;
		this.stockMovementRepository = stockMovementRepository;
		this.permissionGuard = permissionGuard;
		this.branchScope = branchScope;
	}

	@QueryMapping
	@PreAuthorize("isAuthenticated()")
	@Transactional(readOnly = true)
	public List<InventoryItemDto> inventory(@Argument InventoryFilter filter, Authentication authentication) {
		permissionGuard.require(authentication, PermissionModule.INVENTORY, PermissionsService.PermissionAction.VIEW);
		boolean includeZero = filter != null && Boolean.TRUE.equals(filter.includeZero());
		String query = filter != null ? filter.query() : null;
		Long productId = filter != null ? filter.productId() : null;
		String branch = branchScope.scopeFilter(authentication, filter != null ? filter.branch() : null);

		return inventoryRepository.findAll().stream()
				.filter(i -> includeZero || i.getQtyOnHand() != 0)
				.filter(i -> branch == null || i.getLocation().equalsIgnoreCase(branch))
				.filter(i -> productId == null || i.getBatch().getProduct().getId().equals(productId))
				.filter(i -> {
					if (query == null || query.isBlank()) return true;
					String q = query.toLowerCase();
					var p = i.getBatch().getProduct();
					return p.getSku().toLowerCase().contains(q)
							|| p.getName().toLowerCase().contains(q)
							|| i.getBatch().getBatchNumber().toLowerCase().contains(q);
				})
				.map(InventoryItemDto::from)
				.toList();
	}

	@MutationMapping
	@PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
	@Transactional
	public List<InventoryItemDto> writeOffExpired(@Argument String branch, Authentication authentication) {
		permissionGuard.require(authentication, PermissionModule.INVENTORY, PermissionsService.PermissionAction.EDIT);
		String scoped = branchScope.scopeFilter(authentication, branch);
		LocalDate today = LocalDate.now();
		List<InventoryItemDto> cleared = new ArrayList<>();
		for (InventoryItemEntity item : inventoryRepository.findAll()) {
			if (item.getQtyOnHand() <= 0) continue;
			if (scoped != null && !item.getLocation().equalsIgnoreCase(scoped)) continue;
			if (item.getBatch().getExpiryDate().isAfter(today)) continue;
			int qty = item.getQtyOnHand();
			InventoryItemEntity inv = inventoryRepository.findForUpdate(item.getBatch().getId(), item.getLocation()).orElse(item);
			inv.setQtyOnHand(0);
			InventoryItemEntity saved = inventoryRepository.save(inv);
			StockMovementEntity mv = new StockMovementEntity();
			mv.setType(StockMovementType.EXPIRED);
			mv.setBatch(saved.getBatch());
			mv.setQuantity(qty);
			mv.setCreatedBy(authentication != null ? String.valueOf(authentication.getPrincipal()) : null);
			mv.setNote("Expired stock written off @" + saved.getLocation());
			stockMovementRepository.save(mv);
			cleared.add(InventoryItemDto.from(saved));
		}
		return cleared;
	}

	@MutationMapping
	@PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
	@Transactional
	public InventoryItemDto adjustInventory(@Argument AdjustInventoryInput input, Authentication authentication) {
		permissionGuard.require(authentication, PermissionModule.INVENTORY, PermissionsService.PermissionAction.EDIT);
		String location = branchScope.resolveLocation(authentication, input.location());
		var batch = batchRepository.findById(input.batchId()).orElseThrow();

		InventoryItemEntity inv = inventoryRepository.findByBatchIdAndLocation(batch.getId(), location).orElseGet(() -> {
			InventoryItemEntity i = new InventoryItemEntity();
			i.setBatch(batch);
			i.setLocation(location);
			i.setQtyOnHand(0);
			return i;
		});

		int newQty = inv.getQtyOnHand() + input.delta();
		if (newQty < 0) {
			throw new IllegalArgumentException("Adjustment would result in negative quantity");
		}

		inv.setQtyOnHand(newQty);
		InventoryItemEntity saved = inventoryRepository.save(inv);

		StockMovementEntity mv = new StockMovementEntity();
		mv.setType(StockMovementType.ADJUSTMENT);
		mv.setBatch(batch);
		mv.setQuantity(input.delta());
		mv.setCreatedBy(authentication != null ? String.valueOf(authentication.getPrincipal()) : null);
		String note = (input.note() != null && !input.note().isBlank()) ? input.note().trim() : null;
		mv.setNote(note != null ? ("Adj @" + location + ": " + note) : ("Adj @" + location));
		stockMovementRepository.save(mv);

		return InventoryItemDto.from(saved);
	}

	public record InventoryFilter(String query, Long productId, Boolean includeZero, String branch) {
	}

	public record AdjustInventoryInput(Long batchId, String location, int delta, String note) {
	}
}
