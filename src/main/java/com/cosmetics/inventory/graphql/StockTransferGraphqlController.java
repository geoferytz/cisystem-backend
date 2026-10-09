package com.cosmetics.inventory.graphql;

import com.cosmetics.inventory.transfer.StockTransferEntity;
import com.cosmetics.inventory.transfer.StockTransferService;
import com.cosmetics.inventory.user.PermissionGuard;
import com.cosmetics.inventory.user.PermissionModule;
import com.cosmetics.inventory.user.PermissionsService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Controller
public class StockTransferGraphqlController {
	private final StockTransferService transferService;
	private final PermissionGuard permissionGuard;

	public StockTransferGraphqlController(StockTransferService transferService, PermissionGuard permissionGuard) {
		this.transferService = transferService;
		this.permissionGuard = permissionGuard;
	}

	@QueryMapping
	@PreAuthorize("isAuthenticated()")
	@Transactional(readOnly = true)
	public List<StockTransferDto> stockTransfers(@Argument StockTransferFilter filter, Authentication authentication) {
		permissionGuard.require(authentication, PermissionModule.TRANSFERS, PermissionsService.PermissionAction.VIEW);
		String branch = filter != null ? filter.branch() : null;
		String status = filter != null ? filter.status() : null;
		String query = filter != null ? filter.query() : null;
		return transferService.list(authentication, branch, status, query).stream()
				.map(StockTransferDto::from)
				.toList();
	}

	@QueryMapping
	@PreAuthorize("isAuthenticated()")
	@Transactional(readOnly = true)
	public StockTransferDto stockTransfer(@Argument long id, Authentication authentication) {
		permissionGuard.require(authentication, PermissionModule.TRANSFERS, PermissionsService.PermissionAction.VIEW);
		return transferService.list(authentication, null, null, null).stream()
				.filter(t -> t.getId() == id)
				.findFirst()
				.map(StockTransferDto::from)
				.orElse(null);
	}

	@MutationMapping
	@PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
	public StockTransferDto createStockTransfer(@Argument CreateStockTransferInput input, Authentication authentication) {
		permissionGuard.require(authentication, PermissionModule.TRANSFERS, PermissionsService.PermissionAction.CREATE);
		var t = transferService.create(new StockTransferService.CreateCommand(
				input.fromBranch(),
				input.toBranch(),
				input.note(),
				input.lines().stream().map(l -> new StockTransferService.LineCommand(l.batchId(), l.quantity())).toList()
		), authentication);
		return StockTransferDto.from(t);
	}

	@MutationMapping
	@PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
	public StockTransferDto dispatchStockTransfer(@Argument StockTransferActionInput input, Authentication authentication) {
		permissionGuard.require(authentication, PermissionModule.TRANSFERS, PermissionsService.PermissionAction.EDIT);
		return StockTransferDto.from(transferService.dispatch(input.id(), authentication));
	}

	@MutationMapping
	@PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
	public StockTransferDto receiveStockTransfer(@Argument StockTransferActionInput input, Authentication authentication) {
		permissionGuard.require(authentication, PermissionModule.TRANSFERS, PermissionsService.PermissionAction.EDIT);
		return StockTransferDto.from(transferService.receive(input.id(), authentication));
	}

	@MutationMapping
	@PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
	public StockTransferDto cancelStockTransfer(@Argument StockTransferActionInput input, Authentication authentication) {
		permissionGuard.require(authentication, PermissionModule.TRANSFERS, PermissionsService.PermissionAction.DELETE);
		return StockTransferDto.from(transferService.cancel(input.id(), authentication));
	}

	public record StockTransferFilter(String branch, String status, String query) {}
	public record CreateStockTransferInput(String fromBranch, String toBranch, String note, List<LineInput> lines) {}
	public record LineInput(long batchId, int quantity) {}
	public record StockTransferActionInput(long id) {}
}
