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

import java.util.List;

@Controller
public class SalesGraphqlController {
	private final SalesService salesService;
	private final SalesOrderRepository salesOrderRepository;

	public SalesGraphqlController(SalesService salesService, SalesOrderRepository salesOrderRepository) {
		this.salesService = salesService;
		this.salesOrderRepository = salesOrderRepository;
	}

	@QueryMapping
	@PreAuthorize("isAuthenticated()")
	@Transactional(readOnly = true)
	public List<SalesOrderDto> salesOrders() {
		return salesOrderRepository.findAll().stream().map(SalesOrderDto::from).toList();
	}

	@MutationMapping
	@PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
	public SalesOrderDto createSale(@Argument CreateSaleInput input, Authentication authentication) {
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

	public record CreateSaleInput(String customer, String referenceNumber, List<CreateSaleLineInput> lines) {
	}

	public record CreateSaleLineInput(long productId, int quantity, double unitPrice, String location) {
	}
}
