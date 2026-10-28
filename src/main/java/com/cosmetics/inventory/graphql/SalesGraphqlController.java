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
	private final com.cosmetics.inventory.returns.SalesReturnRepository salesReturnRepository;
	private final com.cosmetics.inventory.returns.SalesReturnLineRepository salesReturnLineRepository;

	public SalesGraphqlController(SalesService salesService, SalesOrderRepository salesOrderRepository, PermissionGuard permissionGuard, BranchScope branchScope, UserRepository userRepository, PasswordEncoder passwordEncoder,
			com.cosmetics.inventory.returns.SalesReturnRepository salesReturnRepository, com.cosmetics.inventory.returns.SalesReturnLineRepository salesReturnLineRepository) {
		this.salesService = salesService;
		this.salesOrderRepository = salesOrderRepository;
		this.permissionGuard = permissionGuard;
		this.branchScope = branchScope;
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.salesReturnRepository = salesReturnRepository;
		this.salesReturnLineRepository = salesReturnLineRepository;
	}

	@QueryMapping
	@PreAuthorize("isAuthenticated()")
	@Transactional(readOnly = true)
	public List<SalesOrderDto> salesOrders(@Argument String branch, Authentication authentication) {
		permissionGuard.require(authentication, PermissionModule.SALES, PermissionsService.PermissionAction.VIEW);
		String scoped = branchScope.scopeFilter(authentication, branch);

		// Return info for status badges: qty returned per sale line, value returned per order, orders created by exchange.
		java.util.Map<Long, Integer> returnedByLine = new java.util.HashMap<>();
		for (Object[] row : salesReturnLineRepository.sumReturnedQuantityByLine()) {
			returnedByLine.put((Long) row[0], ((Number) row[1]).intValue());
		}
		java.util.Map<Long, java.math.BigDecimal> returnedValueByOrder = new java.util.HashMap<>();
		java.util.Set<Long> exchangeOrderIds = new java.util.HashSet<>();
		java.util.Map<Long, com.cosmetics.inventory.returns.SalesReturnEntity> returnByExchangeOrder = new java.util.HashMap<>();
		for (var r : salesReturnRepository.findAllWithLines()) {
			returnedValueByOrder.merge(r.getOriginalSalesOrder().getId(), r.getReturnValue(), java.math.BigDecimal::add);
			if (r.getExchangeSalesOrder() != null) {
				exchangeOrderIds.add(r.getExchangeSalesOrder().getId());
				returnByExchangeOrder.put(r.getExchangeSalesOrder().getId(), r);
			}
		}

		return salesOrderRepository.findAllByOrderBySoldAtDescIdDesc().stream()
				.filter(o -> scoped == null || o.getLines().stream()
						.anyMatch(l -> l.getLocation() != null && l.getLocation().equalsIgnoreCase(scoped)))
				.map(o -> SalesOrderDto.from(o, returnedByLine,
						returnedValueByOrder.getOrDefault(o.getId(), java.math.BigDecimal.ZERO), exchangeOrderIds.contains(o.getId()),
						returnByExchangeOrder.get(o.getId())))
				.toList();
	}

	@MutationMapping
	@PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
	public SalesOrderDto createSale(@Argument CreateSaleInput input, Authentication authentication) {
		permissionGuard.require(authentication, PermissionModule.SALES, PermissionsService.PermissionAction.CREATE);
		var so = salesService.createSale(
				new SalesService.CreateSaleCommand(
						input.customer(),
						input.customerId(),
						input.referenceNumber(),
						input.discount() == null ? null : java.math.BigDecimal.valueOf(input.discount()),
						input.paymentMethod(),
						input.amountReceived() == null ? null : java.math.BigDecimal.valueOf(input.amountReceived()),
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
		var existing = salesOrderRepository.findWithLinesById(input.id()).orElseThrow();
		for (var line : existing.getLines()) {
			branchScope.assertBranch(authentication, line.getLocation());
		}
		var so = salesService.updateSale(
				new SalesService.UpdateSaleCommand(
						input.id(),
						input.customer(),
						input.customerId(),
						input.referenceNumber(),
						input.discount() == null ? null : java.math.BigDecimal.valueOf(input.discount()),
						input.paymentMethod(),
						input.amountReceived() == null ? null : java.math.BigDecimal.valueOf(input.amountReceived()),
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
		var existing = salesOrderRepository.findWithLinesById(input.id()).orElseThrow();
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
				.filter(u -> u.isActive() && u.getRoles().stream().anyMatch(r -> "ADMIN".equalsIgnoreCase(r.getName())))
				.anyMatch(u -> passwordEncoder.matches(code, u.getPasswordHash()));
		if (!approved) {
			throw new IllegalArgumentException("Invalid admin approval code");
		}
	}

	public record CreateSaleInput(String customer, Long customerId, String referenceNumber, Double discount, String paymentMethod, Double amountReceived, List<CreateSaleLineInput> lines) {
	}

	public record CreateSaleLineInput(long productId, int quantity, double unitPrice, String location) {
	}

	public record UpdateSaleInput(long id, String customer, Long customerId, String referenceNumber, Double discount, String paymentMethod, Double amountReceived, List<CreateSaleLineInput> lines) {
	}

	public record DeleteSaleInput(long id, String adminCode) {
	}
}
