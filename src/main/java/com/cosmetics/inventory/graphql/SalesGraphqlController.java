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

import com.cosmetics.inventory.branch.BranchScope;
import com.cosmetics.inventory.user.PermissionGuard;
import com.cosmetics.inventory.user.PermissionModule;
import com.cosmetics.inventory.user.PermissionsService;
import com.cosmetics.inventory.user.RoleName;
import com.cosmetics.inventory.user.UserRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

@Controller
public class SalesGraphqlController {
	private final SalesService salesService;
	private final SalesOrderRepository salesOrderRepository;
	private final PermissionGuard permissionGuard;
	private final BranchScope branchScope;
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	public SalesGraphqlController(SalesService salesService, SalesOrderRepository salesOrderRepository, PermissionGuard permissionGuard, BranchScope branchScope, UserRepository userRepository, PasswordEncoder passwordEncoder) {
		this.salesService = salesService;
		this.salesOrderRepository = salesOrderRepository;
		this.permissionGuard = permissionGuard;
		this.branchScope = branchScope;
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@QueryMapping
	@PreAuthorize("isAuthenticated()")
	@Transactional(readOnly = true)
	public List<SalesOrderDto> salesOrders(@Argument String branch, Authentication authentication) {
		permissionGuard.require(authentication, PermissionModule.SALES, PermissionsService.PermissionAction.VIEW);
		String scoped = branchScope.scopeFilter(authentication, branch);
		return salesOrderRepository.findAll().stream()
				.filter(o -> scoped == null || o.getLines().stream()
						.anyMatch(l -> l.getLocation() != null && l.getLocation().equalsIgnoreCase(scoped)))
				.map(SalesOrderDto::from).toList();
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
								branchScope.resolveLocation(authentication, l.location())
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
		var existing = salesOrderRepository.findById(input.id()).orElseThrow();
		for (var line : existing.getLines()) {
			branchScope.assertBranch(authentication, line.getLocation());
		}
		var so = salesService.updateSale(
				new SalesService.UpdateSaleCommand(
						input.id(),
						input.customer(),
						input.referenceNumber(),
						input.lines().stream().map(l -> new SalesService.CreateSaleLineCommand(
								l.productId(),
								l.quantity(),
								l.unitPrice(),
								branchScope.resolveLocation(authentication, l.location())
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
		var existing = salesOrderRepository.findById(input.id()).orElseThrow();
		for (var line : existing.getLines()) {
			branchScope.assertBranch(authentication, line.getLocation());
		}
		requireAdminApproval(authentication, input.adminCode());
		return salesService.deleteSale(input.id(), authentication);
	}

	private void requireAdminApproval(Authentication authentication, String adminCode) {
		boolean isAdmin = false;
		for (GrantedAuthority a : authentication.getAuthorities()) {
			if (a != null && "ROLE_ADMIN".equalsIgnoreCase(String.valueOf(a.getAuthority()))) {
				isAdmin = true;
				break;
			}
		}
		if (isAdmin) {
			return;
		}
		if (adminCode == null || adminCode.isBlank()) {
			throw new IllegalArgumentException("Admin approval code is required to delete a sale");
		}
		String code = adminCode.trim();
		boolean approved = userRepository.findAll().stream()
				.filter(u -> u.isActive() && u.getRoles().stream().anyMatch(r -> r.getName() == RoleName.ADMIN))
				.anyMatch(u -> passwordEncoder.matches(code, u.getPasswordHash()));
		if (!approved) {
			throw new IllegalArgumentException("Invalid admin approval code");
		}
	}

	public record CreateSaleInput(String customer, String referenceNumber, List<CreateSaleLineInput> lines) {
	}

	public record CreateSaleLineInput(long productId, int quantity, double unitPrice, String location) {
	}

	public record UpdateSaleInput(long id, String customer, String referenceNumber, List<CreateSaleLineInput> lines) {
	}

	public record DeleteSaleInput(long id, String adminCode) {
	}
}
