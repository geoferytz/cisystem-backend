package com.cosmetics.inventory.graphql;

import com.cosmetics.inventory.inventory.InventoryItemEntity;

public record InventoryItemDto(
		Long id,
		Long productId,
		String sku,
		String productName,
		String unitOfMeasure,
		Long batchId,
		String batchNumber,
		String expiryDate,
		String location,
		int qtyOnHand
) {
	public static InventoryItemDto from(InventoryItemEntity inv) {
		var batch = inv.getBatch();
		var product = batch.getProduct();
		return new InventoryItemDto(
				inv.getId(),
				product.getId(),
				product.getSku(),
				product.getName(),
				product.getUnitOfMeasure(),
				batch.getId(),
				batch.getBatchNumber(),
				batch.getExpiryDate().toString(),
				inv.getLocation(),
				inv.getQtyOnHand()
		);
	}
}
