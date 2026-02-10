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

import java.util.List;

@Controller
public class InventoryGraphqlController {
	private final InventoryRepository inventoryRepository;
	private final ProductBatchRepository batchRepository;
	private final StockMovementRepository stockMovementRepository;

	public InventoryGraphqlController(InventoryRepository inventoryRepository, ProductBatchRepository batchRepository, StockMovementRepository stockMovementRepository) {
		this.inventoryRepository = inventoryRepository;
		this.batchRepository = batchRepository;
		this.stockMovementRepository = stockMovementRepository;
	}

	@QueryMapping
	@PreAuthorize("isAuthenticated()")
	@Transactional(readOnly = true)
	public List<InventoryItemDto> inventory(@Argument InventoryFilter filter) {
		boolean includeZero = filter != null && Boolean.TRUE.equals(filter.includeZero());
		String query = filter != null ? filter.query() : null;
		Long productId = filter != null ? filter.productId() : null;

		return inventoryRepository.findAll().stream()
				.filter(i -> includeZero || i.getQtyOnHand() != 0)
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
	public InventoryItemDto adjustInventory(@Argument AdjustInventoryInput input, Authentication authentication) {
		String location = (input.location() != null && !input.location().isBlank()) ? input.location().trim() : "MAIN";
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

	public record InventoryFilter(String query, Long productId, Boolean includeZero) {
	}

	public record AdjustInventoryInput(Long batchId, String location, int delta, String note) {
	}
}
