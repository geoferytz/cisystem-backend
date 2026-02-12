package com.cosmetics.inventory.graphql;

import com.cosmetics.inventory.sales.SalesOrderRepository;
import com.cosmetics.inventory.sales.SalesService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
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
public class SalesGraphqlController {
	private final SalesService salesService;
	private final SalesOrderRepository salesOrderRepository;
	private final PermissionGuard permissionGuard;

	public SalesGraphqlController(SalesService salesService, SalesOrderRepository salesOrderRepository, PermissionGuard permissionGuard) {
		this.salesService = salesService;
		this.salesOrderRepository = salesOrderRepository;
		this.permissionGuard = permissionGuard;
	}

	@QueryMapping
	@PreAuthorize("isAuthenticated()")
	@Transactional(readOnly = true)
	public List<SalesOrderDto> salesOrders(Authentication authentication) {
		permissionGuard.require(authentication, PermissionModule.SALES, PermissionsService.PermissionAction.VIEW);
		return salesOrderRepository.findAll().stream().map(SalesOrderDto::from).toList();
	}

	@MutationMapping
	@PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
	public SalesOrderDto createSale(@Argument CreateSaleInput input, Authentication authentication) {
		permissionGuard.require(authentication, PermissionModule.SALES, PermissionsService.PermissionAction.CREATE);
		var so = salesService.createSale(
				new SalesService.CreateSaleCommand(
						input.customer(),
						input.referenceNumber(),
						input.lines().stream().map(l -> new SalesService.CreateSaleLineCommand(
								l.productId(),
								l.quantity(),
								l.unitPrice(),
								l.location()
						)).toList()
				),
			authentication
		);
		return SalesOrderDto.from(so);
	}

	@MutationMapping
	@PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
	public SalesOrderDto updateSale(@Argument UpdateSaleInput input, Authentication authentication) {
		permissionGuard.require(authentication, PermissionModule.SALES, PermissionsService.PermissionAction.EDIT);
		var so = salesService.updateSale(
				new SalesService.UpdateSaleCommand(
						input.id(),
						input.customer(),
						input.referenceNumber(),
						input.lines().stream().map(l -> new SalesService.CreateSaleLineCommand(
								l.productId(),
								l.quantity(),
								l.unitPrice(),
								l.location()
						)).toList()
				),
			authentication
		);
		return SalesOrderDto.from(so);
	}

	@MutationMapping
	@PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
	public boolean deleteSale(@Argument DeleteSaleInput input, Authentication authentication) {
		permissionGuard.require(authentication, PermissionModule.SALES, PermissionsService.PermissionAction.DELETE);
		return salesService.deleteSale(input.id(), authentication);
	}

	public record CreateSaleInput(String customer, String referenceNumber, List<CreateSaleLineInput> lines) {
	}

	public record CreateSaleLineInput(long productId, int quantity, double unitPrice, String location) {
	}

	public record UpdateSaleInput(long id, String customer, String referenceNumber, List<CreateSaleLineInput> lines) {
	}

	public record DeleteSaleInput(long id) {
	}
}
