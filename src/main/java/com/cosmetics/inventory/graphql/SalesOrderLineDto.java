package com.cosmetics.inventory.graphql;

import com.cosmetics.inventory.sales.SalesOrderLineEntity;

import java.util.List;

public record SalesOrderLineDto(
		Long id,
		Long productId,
		String sku,
		String productName,
		int quantity,
		String location,
		double unitPrice,
		List<SalesDeductionDto> deductions
) {
	public static SalesOrderLineDto from(SalesOrderLineEntity line) {
		var p = line.getProduct();
		return new SalesOrderLineDto(
				line.getId(),
				p.getId(),
				p.getSku(),
				p.getName(),
				line.getQuantity(),
				(line.getLocation() == null || line.getLocation().isBlank()) ? "MAIN" : line.getLocation(),
				line.getUnitPrice().doubleValue(),
				line.getDeductions().stream().map(SalesDeductionDto::from).toList()
		);
	}
}
