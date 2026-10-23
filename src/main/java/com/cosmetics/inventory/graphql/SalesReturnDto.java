package com.cosmetics.inventory.graphql;

import com.cosmetics.inventory.returns.ReturnCondition;
import com.cosmetics.inventory.returns.SalesReturnEntity;
import com.cosmetics.inventory.returns.SalesReturnLineEntity;

import java.math.BigDecimal;
import java.util.List;

public record SalesReturnDto(
		Long id,
		String reference,
		Long originalSalesOrderId,
		String originalReference,
		Long exchangeSalesOrderId,
		String exchangeReference,
		Long customerId,
		String customer,
		String returnedAt,
		String returnedBy,
		String reason,
		String branch,
		BigDecimal returnValue,
		BigDecimal lossAmount,
		String refundMethod,
		BigDecimal refundAmount,
		BigDecimal debtCreditAmount,
		BigDecimal settlementAmount,
		boolean isExchange,
		List<Line> lines
) {
	public record Line(Long id, Long salesOrderLineId, Long productId, String sku, String productName, Long batchId, String batchNumber,
			int quantity, BigDecimal unitPrice, ReturnCondition condition, BigDecimal lossAmount) {
		static Line from(SalesReturnLineEntity l) {
			return new Line(l.getId(), l.getSalesOrderLine().getId(), l.getProduct().getId(), l.getProduct().getSku(), l.getProduct().getName(),
					l.getBatch() != null ? l.getBatch().getId() : null, l.getBatch() != null ? l.getBatch().getBatchNumber() : null,
					l.getQuantity(), l.getUnitPrice(), l.getCondition(), l.getLossAmount());
		}
	}

	public static SalesReturnDto from(SalesReturnEntity r) {
		var orig = r.getOriginalSalesOrder();
		var exch = r.getExchangeSalesOrder();
		return new SalesReturnDto(
				r.getId(), r.getReference(),
				orig.getId(), orig.getReferenceNumber(),
				exch != null ? exch.getId() : null, exch != null ? exch.getReferenceNumber() : null,
				r.getCustomer() != null ? r.getCustomer().getId() : null, orig.getCustomer(),
				r.getReturnedAt().toString(), r.getReturnedBy(), r.getReason(), r.getBranch(),
				r.getReturnValue(), r.getLossAmount(), r.getRefundMethod(), r.getRefundAmount(), r.getDebtCreditAmount(), r.getSettlementAmount(),
				exch != null,
				r.getLines().stream().map(Line::from).toList()
		);
	}
}
