package com.cosmetics.inventory.graphql;

import com.cosmetics.inventory.purchasing.PurchaseOrderLineEntity;

public record PurchaseOrderLineDto(
		Long id,
		Long productId,
		String sku,
		String productName,
		Long batchId,
		String batchNumber,
		String expiryDate,
		double costPrice,
		int quantityReceived
) {
	public static PurchaseOrderLineDto from(PurchaseOrderLineEntity line) {
		var p = line.getProduct();
		var b = line.getBatch();
		return new PurchaseOrderLineDto(
				line.getId(),
				p.getId(),
				p.getSku(),
				p.getName(),
				b.getId(),
				b.getBatchNumber(),
				b.getExpiryDate().toString(),
				line.getCostPrice().doubleValue(),
				line.getQuantityReceived()
		);
	}
}
