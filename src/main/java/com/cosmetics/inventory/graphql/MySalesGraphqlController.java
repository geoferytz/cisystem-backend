package com.cosmetics.inventory.graphql;

import com.cosmetics.inventory.mysales.MySalesRepository;
import com.cosmetics.inventory.mysales.MySalesService;
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
public class MySalesGraphqlController {
	private final MySalesService mySalesService;
	private final MySalesRepository mySalesRepository;
	private final PermissionGuard permissionGuard;

	public MySalesGraphqlController(MySalesService mySalesService, MySalesRepository mySalesRepository, PermissionGuard permissionGuard) {
		this.mySalesService = mySalesService;
		this.mySalesRepository = mySalesRepository;
		this.permissionGuard = permissionGuard;
	}

	@QueryMapping
	@PreAuthorize("isAuthenticated()")
	@Transactional(readOnly = true)
	public List<MySaleDto> mySales(Authentication authentication) {
		permissionGuard.require(authentication, PermissionModule.MY_SALES, PermissionsService.PermissionAction.VIEW);
		return mySalesService.findAll().stream().map(MySaleDto::from).toList();
	}

	@QueryMapping
	@PreAuthorize("isAuthenticated()")
	@Transactional(readOnly = true)
	public MySaleDto mySale(@Argument long id, Authentication authentication) {
		permissionGuard.require(authentication, PermissionModule.MY_SALES, PermissionsService.PermissionAction.VIEW);
		return mySalesRepository.findById(id).map(MySaleDto::from).orElse(null);
	}

	@MutationMapping
	@PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
	public MySaleDto createMySale(@Argument CreateMySaleInput input, Authentication authentication) {
		permissionGuard.require(authentication, PermissionModule.MY_SALES, PermissionsService.PermissionAction.CREATE);
		var sale = mySalesService.create(
				new MySalesService.CreateMySaleCommand(
						input.customer(),
						input.referenceNumber(),
						input.lines().stream().map(l -> new MySalesService.CreateMySaleLineCommand(
								l.productName(),
								l.quantity(),
								l.unitPrice()
						)).toList()
				),
			authentication
		);
		return MySaleDto.from(sale);
	}

	@MutationMapping
	@PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
	public MySaleDto updateMySale(@Argument UpdateMySaleInput input, Authentication authentication) {
		permissionGuard.require(authentication, PermissionModule.MY_SALES, PermissionsService.PermissionAction.EDIT);
		var sale = mySalesService.update(
				new MySalesService.UpdateMySaleCommand(
						input.id(),
						input.customer(),
						input.referenceNumber(),
						input.lines().stream().map(l -> new MySalesService.CreateMySaleLineCommand(
								l.productName(),
								l.quantity(),
								l.unitPrice()
						)).toList()
				)
		);
		return MySaleDto.from(sale);
	}

	@MutationMapping
	@PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
	public boolean deleteMySale(@Argument DeleteMySaleInput input, Authentication authentication) {
		permissionGuard.require(authentication, PermissionModule.MY_SALES, PermissionsService.PermissionAction.DELETE);
		return mySalesService.delete(input.id());
	}

	public record CreateMySaleInput(String customer, String referenceNumber, List<CreateMySaleLineInput> lines) {
	}

	public record UpdateMySaleInput(long id, String customer, String referenceNumber, List<CreateMySaleLineInput> lines) {
	}

	public record DeleteMySaleInput(long id) {
	}

	public record CreateMySaleLineInput(String productName, int quantity, double unitPrice) {
	}
}
