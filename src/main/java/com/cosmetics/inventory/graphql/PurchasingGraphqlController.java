package com.cosmetics.inventory.graphql;

import com.cosmetics.inventory.purchasing.PurchaseOrderRepository;
import com.cosmetics.inventory.purchasing.PurchasingService;
import com.cosmetics.inventory.purchasing.RepurchaseService;
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

import java.util.List;

@Controller
public class PurchasingGraphqlController {
	private final PurchasingService purchasingService;
	private final PurchaseOrderRepository purchaseOrderRepository;
	private final PermissionGuard permissionGuard;

	private final RepurchaseService repurchase;
	private final BranchScope branchScope;

	public PurchasingGraphqlController(PurchasingService purchasingService, PurchaseOrderRepository purchaseOrderRepository, PermissionGuard permissionGuard, RepurchaseService repurchase, BranchScope branchScope) {
		this.repurchase = repurchase;
		this.purchasingService = purchasingService;
		this.purchaseOrderRepository = purchaseOrderRepository;
		this.permissionGuard = permissionGuard;
		this.branchScope = branchScope;
	}

	@QueryMapping
	@PreAuthorize("isAuthenticated()")
	@Transactional(readOnly = true)
	public List<PurchaseOrderDto> purchaseOrders(Authentication authentication, @Argument String branch) {
		permissionGuard.require(authentication, PermissionModule.PURCHASING, PermissionsService.PermissionAction.VIEW);
		String scoped = branchScope.scopeFilter(authentication, branch);
		return purchaseOrderRepository.findAll().stream()
				.filter(po -> scoped == null || scoped.equalsIgnoreCase(po.getBranch()))
				.map(PurchaseOrderDto::from).toList();
	}

	@MutationMapping
	@PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
	public PurchaseOrderDto receivePurchase(@Argument ReceivePurchaseInput input, Authentication authentication) {
		permissionGuard.require(authentication, PermissionModule.PURCHASING, PermissionsService.PermissionAction.CREATE);
		var po = purchasingService.receivePurchase(
				new PurchasingService.ReceivePurchaseCommand(
						input.supplier(),
						input.invoiceNumber(),
						branchScope.resolveLocation(authentication, input.branch()),
						input.lines().stream().map(l -> new PurchasingService.ReceivePurchaseLineCommand(
								l.productId(),
								l.batchNumber(),
								l.expiryDate(),
								l.costPrice(),
								l.quantityReceived()
						)).toList()
				),
			authentication
		);
		return PurchaseOrderDto.from(po);
	}

	@MutationMapping
	@PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
	public PurchaseOrderDto updatePurchase(@Argument UpdatePurchaseInput input, Authentication authentication) {
		permissionGuard.require(authentication, PermissionModule.PURCHASING, PermissionsService.PermissionAction.EDIT);
		var po = purchasingService.updatePurchase(
				new PurchasingService.UpdatePurchaseCommand(
						input.id(),
						input.supplier(),
						input.invoiceNumber(),
						branchScope.resolveLocation(authentication, input.branch()),
						input.lines().stream().map(l -> new PurchasingService.ReceivePurchaseLineCommand(
								l.productId(),
								l.batchNumber(),
								l.expiryDate(),
								l.costPrice(),
								l.quantityReceived()
						)).toList()
				),
			authentication
		);
		return PurchaseOrderDto.from(po);
	}

	@MutationMapping
	@PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
	public boolean deletePurchase(@Argument DeletePurchaseInput input, Authentication authentication) {
		permissionGuard.require(authentication, PermissionModule.PURCHASING, PermissionsService.PermissionAction.DELETE);
		return purchasingService.deletePurchase(input.id(), authentication);
	}

	@QueryMapping
	@PreAuthorize("isAuthenticated()")
	public List<RepurchaseService.CatalogProduct> repurchaseProducts(Authentication authentication, @Argument String branch) {
		permissionGuard.require(authentication, PermissionModule.PURCHASING, PermissionsService.PermissionAction.VIEW);
		return repurchase.catalog(branchScope.resolveLocation(authentication, branch));
	}

	@QueryMapping
	@PreAuthorize("isAuthenticated()")
	public List<RepurchaseService.Order> pendingRepurchases(Authentication authentication, @Argument String branch) {
		permissionGuard.require(authentication, PermissionModule.PURCHASING, PermissionsService.PermissionAction.VIEW);
		return repurchase.pending(branchScope.scopeFilter(authentication, branch));
	}

	@MutationMapping
	@PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
	public RepurchaseService.Order submitRepurchase(@Argument RepurchaseService.SubmitCommand input, Authentication authentication) {
		permissionGuard.require(authentication, PermissionModule.PURCHASING, PermissionsService.PermissionAction.CREATE);
		return repurchase.submit(input, authentication);
	}

	@MutationMapping
	@PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
	public RepurchaseService.Order receiveRepurchase(@Argument ReceiveRepurchaseInput input, Authentication authentication) {
		permissionGuard.require(authentication, PermissionModule.PURCHASING, PermissionsService.PermissionAction.CREATE);
		return repurchase.receive(input.id(), input.lines(), authentication);
	}

	public record ReceiveRepurchaseInput(String id, List<RepurchaseService.ReceiptLine> lines) {}

	public record ReceivePurchaseInput(String supplier, String invoiceNumber, String branch, List<ReceivePurchaseLineInput> lines) {
	}

	public record ReceivePurchaseLineInput(long productId, String batchNumber, String expiryDate, double costPrice,
													 int quantityReceived) {
	}

	public record UpdatePurchaseInput(long id, String supplier, String invoiceNumber, String branch, List<ReceivePurchaseLineInput> lines) {
	}

	public record DeletePurchaseInput(long id) {
	}
}
