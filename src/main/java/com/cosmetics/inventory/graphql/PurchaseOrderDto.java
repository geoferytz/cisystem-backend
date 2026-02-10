package com.cosmetics.inventory.graphql;

import com.cosmetics.inventory.purchasing.PurchaseOrderEntity;

import java.util.List;

public record PurchaseOrderDto(
		Long id,
		String supplier,
		String invoiceNumber,
		String receivedAt,
		String receivedBy,
		List<PurchaseOrderLineDto> lines
) {
	public static PurchaseOrderDto from(PurchaseOrderEntity po) {
		return new PurchaseOrderDto(
				po.getId(),
				po.getSupplier(),
				po.getInvoiceNumber(),
				po.getReceivedAt().toString(),
				po.getReceivedBy(),
				po.getLines().stream().map(PurchaseOrderLineDto::from).toList()
		);
	}
}
