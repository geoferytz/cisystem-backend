package com.cosmetics.inventory.graphql;

import com.cosmetics.inventory.sales.SalesDeductionEntity;

public record SalesDeductionDto(
		Long id,
		Long batchId,
		String batchNumber,
		String expiryDate,
		int quantity
) {
	public static SalesDeductionDto from(SalesDeductionEntity d) {
		var b = d.getBatch();
		return new SalesDeductionDto(
				d.getId(),
				b.getId(),
				b.getBatchNumber(),
				b.getExpiryDate().toString(),
				d.getQuantity()
		);
	}
}
