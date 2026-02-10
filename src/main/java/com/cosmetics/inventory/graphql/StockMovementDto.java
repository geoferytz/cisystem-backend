package com.cosmetics.inventory.graphql;

import com.cosmetics.inventory.stockmovement.StockMovementEntity;

public record StockMovementDto(
		Long id,
		String type,
		int quantity,
		String createdAt,
		String createdBy,
		String note,
		Long batchId,
		String batchNumber,
		String expiryDate,
		Long productId,
		String sku,
		String productName
) {
	public static StockMovementDto from(StockMovementEntity mv) {
		var batch = mv.getBatch();
		var product = batch.getProduct();
		return new StockMovementDto(
				mv.getId(),
				mv.getType().name(),
				mv.getQuantity(),
				mv.getCreatedAt().toString(),
				mv.getCreatedBy(),
				mv.getNote(),
				batch.getId(),
				batch.getBatchNumber(),
				batch.getExpiryDate().toString(),
				product.getId(),
				product.getSku(),
				product.getName()
		);
	}
}
