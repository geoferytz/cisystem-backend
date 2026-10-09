package com.cosmetics.inventory.graphql;

import com.cosmetics.inventory.transfer.StockTransferEntity;

import java.util.List;

public record StockTransferDto(
		Long id,
		String reference,
		String fromBranch,
		String toBranch,
		String status,
		String note,
		String createdAt,
		String createdBy,
		String dispatchedAt,
		String dispatchedBy,
		String receivedAt,
		String receivedBy,
		List<LineDto> lines
) {
	public record LineDto(Long productId, Long batchId, String sku, String productName, String batchNumber, int quantity) {}

	public static StockTransferDto from(StockTransferEntity t) {
		return new StockTransferDto(
				t.getId(),
				t.getReference(),
				t.getFromBranch(),
				t.getToBranch(),
				t.getStatus().name(),
				t.getNote(),
				t.getCreatedAt() != null ? t.getCreatedAt().toString() : null,
				t.getCreatedBy(),
				t.getDispatchedAt() != null ? t.getDispatchedAt().toString() : null,
				t.getDispatchedBy(),
				t.getReceivedAt() != null ? t.getReceivedAt().toString() : null,
				t.getReceivedBy(),
				t.getLines().stream().map(l -> new LineDto(
						l.getProductId(), l.getBatchId(), l.getSku(), l.getProductName(), l.getBatchNumber(), l.getQuantity()
				)).toList()
		);
	}
}
