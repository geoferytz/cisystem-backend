package com.cosmetics.inventory.graphql;

import com.cosmetics.inventory.stockmovement.StockMovementRepository;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import com.cosmetics.inventory.user.PermissionGuard;
import com.cosmetics.inventory.user.PermissionModule;
import com.cosmetics.inventory.user.PermissionsService;

import java.util.List;

@Controller
public class StockMovementGraphqlController {
	private final StockMovementRepository stockMovementRepository;
	private final PermissionGuard permissionGuard;

	public StockMovementGraphqlController(StockMovementRepository stockMovementRepository, PermissionGuard permissionGuard) {
		this.stockMovementRepository = stockMovementRepository;
		this.permissionGuard = permissionGuard;
	}

	@QueryMapping
	@PreAuthorize("isAuthenticated()")
	@Transactional(readOnly = true)
	public List<StockMovementDto> stockMovements(@Argument StockMovementFilter filter, Authentication authentication) {
		permissionGuard.require(authentication, PermissionModule.STOCK_MOVEMENTS, PermissionsService.PermissionAction.VIEW);
		String type = filter != null ? filter.type() : null;
		Long productId = filter != null ? filter.productId() : null;
		Long batchId = filter != null ? filter.batchId() : null;

		return stockMovementRepository.findAll().stream()
				.filter(mv -> type == null || type.isBlank() || mv.getType().name().equalsIgnoreCase(type.trim()))
				.filter(mv -> productId == null || mv.getBatch().getProduct().getId().equals(productId))
				.filter(mv -> batchId == null || mv.getBatch().getId().equals(batchId))
				.map(StockMovementDto::from)
				.toList();
	}

	public record StockMovementFilter(String type, Long productId, Long batchId) {
	}
}
