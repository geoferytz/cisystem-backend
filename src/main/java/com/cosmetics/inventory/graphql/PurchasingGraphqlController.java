package com.cosmetics.inventory.graphql;

import com.cosmetics.inventory.purchasing.PurchaseOrderRepository;
import com.cosmetics.inventory.purchasing.PurchasingService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Controller
public class PurchasingGraphqlController {
	private final PurchasingService purchasingService;
	private final PurchaseOrderRepository purchaseOrderRepository;

	public PurchasingGraphqlController(PurchasingService purchasingService, PurchaseOrderRepository purchaseOrderRepository) {
		this.purchasingService = purchasingService;
		this.purchaseOrderRepository = purchaseOrderRepository;
	}

	@QueryMapping
	@PreAuthorize("isAuthenticated()")
	@Transactional(readOnly = true)
	public List<PurchaseOrderDto> purchaseOrders() {
		return purchaseOrderRepository.findAll().stream().map(PurchaseOrderDto::from).toList();
	}

	@MutationMapping
	@PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
	public PurchaseOrderDto receivePurchase(@Argument ReceivePurchaseInput input, Authentication authentication) {
		var po = purchasingService.receivePurchase(
				new PurchasingService.ReceivePurchaseCommand(
						input.supplier(),
						input.invoiceNumber(),
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

	public record ReceivePurchaseInput(String supplier, String invoiceNumber, List<ReceivePurchaseLineInput> lines) {
	}

	public record ReceivePurchaseLineInput(long productId, String batchNumber, String expiryDate, double costPrice,
													 int quantityReceived) {
	}
}
