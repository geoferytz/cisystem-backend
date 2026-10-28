package com.cosmetics.inventory.graphql;

import com.cosmetics.inventory.branch.BranchScope;
import com.cosmetics.inventory.returns.ReturnCondition;
import com.cosmetics.inventory.returns.ReturnsService;
import com.cosmetics.inventory.returns.SalesReturnRepository;
import com.cosmetics.inventory.sales.SalesOrderEntity;
import com.cosmetics.inventory.sales.SalesOrderRepository;
import com.cosmetics.inventory.sales.SalesService;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Controller
public class ReturnsGraphqlController {
	private final ReturnsService returnsService;
	private final SalesReturnRepository returnRepository;
	private final SalesOrderRepository salesOrderRepository;
	private final CustomerGraphqlController customers;
	private final PermissionGuard permissionGuard;
	private final BranchScope branchScope;

	public ReturnsGraphqlController(ReturnsService returnsService, SalesReturnRepository returnRepository, SalesOrderRepository salesOrderRepository,
			CustomerGraphqlController customers, PermissionGuard permissionGuard, BranchScope branchScope) {
		this.returnsService = returnsService;
		this.returnRepository = returnRepository;
		this.salesOrderRepository = salesOrderRepository;
		this.customers = customers;
		this.permissionGuard = permissionGuard;
		this.branchScope = branchScope;
	}

	@QueryMapping
	@PreAuthorize("isAuthenticated()")
	@Transactional(readOnly = true)
	public List<SalesReturnDto> salesReturns(@Argument String branch, @Argument String from, @Argument String to, Authentication authentication) {
		permissionGuard.requireAny(authentication, PermissionsService.PermissionAction.VIEW, PermissionModule.RETURNS, PermissionModule.SALES);
		String scoped = branchScope.scopeFilter(authentication, branch);
		var list = (from != null && to != null)
				? returnRepository.findWithLinesBetween(dayStart(from), dayStart(to).plusSeconds(86400))
				: returnRepository.findAllWithLines();
		return list.stream()
				.filter(r -> scoped == null || r.getBranch().equalsIgnoreCase(scoped))
				.map(SalesReturnDto::from).toList();
	}

	@QueryMapping
	@PreAuthorize("isAuthenticated()")
	@Transactional(readOnly = true)
	public SalesReturnDto salesReturn(@Argument Long id, Authentication authentication) {
		permissionGuard.requireAny(authentication, PermissionsService.PermissionAction.VIEW, PermissionModule.RETURNS, PermissionModule.SALES);
		return returnRepository.findWithLinesById(id).map(SalesReturnDto::from).orElse(null);
	}

	@QueryMapping
	@PreAuthorize("isAuthenticated()")
	@Transactional(readOnly = true)
	public ReturnableSale returnableLines(@Argument Long salesOrderId, Authentication authentication) {
		permissionGuard.requireAny(authentication, PermissionsService.PermissionAction.VIEW, PermissionModule.RETURNS, PermissionModule.SALES, PermissionModule.COUNTER);
		SalesOrderEntity so = salesOrderRepository.findWithLinesById(salesOrderId).orElseThrow(() -> new IllegalArgumentException("Sale not found"));
		Long customerId = so.getCustomerRef() != null ? so.getCustomerRef().getId() : null;
		double debt = customerId != null ? customers.customerDebt(customerId, authentication) : 0.0;
		return new ReturnableSale(so.getId(), so.getReferenceNumber(), so.getCustomer(), customerId, so.getSoldAt().toString(),
				returnsService.withinReturnWindow(so), ReturnsService.RETURN_WINDOW_DAYS, debt, returnsService.returnableLines(salesOrderId));
	}

	@MutationMapping
	@PreAuthorize("isAuthenticated()")
	@Transactional
	public SalesReturnDto createSalesReturn(@Argument CreateSalesReturnInput input, Authentication authentication) {
		permissionGuard.requireAny(authentication, PermissionsService.PermissionAction.CREATE, PermissionModule.RETURNS, PermissionModule.SALES, PermissionModule.COUNTER);
		boolean override = Boolean.TRUE.equals(input.overrideWindow()) && canOverride(authentication);
		var ret = returnsService.createReturn(new ReturnsService.CreateReturnCommand(
				input.salesOrderId(), toLines(input.lines()), input.reason(), input.refundMethod(), dec(input.debtCreditAmount())), authentication, override);
		return SalesReturnDto.from(returnRepository.findWithLinesById(ret.getId()).orElse(ret));
	}

	@MutationMapping
	@PreAuthorize("isAuthenticated()")
	@Transactional
	public SalesReturnDto createExchange(@Argument CreateExchangeInput input, Authentication authentication) {
		permissionGuard.requireAny(authentication, PermissionsService.PermissionAction.CREATE, PermissionModule.RETURNS, PermissionModule.SALES, PermissionModule.COUNTER);
		permissionGuard.rejectIfDenied(authentication, PermissionModule.RETURNS, "EXCHANGE");
		boolean override = Boolean.TRUE.equals(input.overrideWindow()) && canOverride(authentication);
		List<SalesService.CreateSaleLineCommand> newLines = input.newLines() == null ? List.of() : input.newLines().stream()
				.map(l -> new SalesService.CreateSaleLineCommand(l.productId(), l.quantity(), l.unitPrice(), branchScope.resolveLocation(authentication, l.location())))
				.toList();
		var ret = returnsService.createExchange(new ReturnsService.CreateExchangeCommand(
				input.salesOrderId(), toLines(input.returnLines()), newLines, input.reason(), input.paymentMethod(),
				dec(input.amountReceived()), input.refundMethod(), dec(input.debtCreditAmount())), authentication, override);
		return SalesReturnDto.from(returnRepository.findWithLinesById(ret.getId()).orElse(ret));
	}

	private static List<ReturnsService.ReturnLineCommand> toLines(List<ReturnLineInput> lines) {
		return lines == null ? List.of() : lines.stream()
				.map(l -> new ReturnsService.ReturnLineCommand(l.salesOrderLineId(), l.quantity(), l.condition()))
				.toList();
	}

	private static BigDecimal dec(Double d) {
		return d == null ? null : BigDecimal.valueOf(d);
	}

	private static Instant dayStart(String isoDate) {
		return LocalDate.parse(isoDate).atStartOfDay(ZoneOffset.UTC).toInstant();
	}

	/** Admins, or users granted RETURNS → LATE_RETURN, may accept returns past the window. */
	private boolean canOverride(Authentication authentication) {
		if (isAdmin(authentication)) return true;
		try {
			permissionGuard.requireExtra(authentication, PermissionModule.RETURNS, "LATE_RETURN");
			return true;
		} catch (org.springframework.security.access.AccessDeniedException e) {
			return false;
		}
	}

	private static boolean isAdmin(Authentication authentication) {
		return authentication != null && authentication.getAuthorities().stream()
				.anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
	}

	public record ReturnableSale(Long salesOrderId, String referenceNumber, String customer, Long customerId, String soldAt,
			boolean withinWindow, int windowDays, double customerDebt, List<ReturnsService.ReturnableLine> lines) {}

	public record ReturnLineInput(long salesOrderLineId, int quantity, ReturnCondition condition) {}

	public record CreateSalesReturnInput(long salesOrderId, List<ReturnLineInput> lines, String reason, String refundMethod, Double debtCreditAmount, Boolean overrideWindow) {}

	public record CreateExchangeInput(long salesOrderId, List<ReturnLineInput> returnLines, List<SalesGraphqlController.CreateSaleLineInput> newLines,
			String reason, String paymentMethod, Double amountReceived, String refundMethod, Double debtCreditAmount, Boolean overrideWindow) {}
}
