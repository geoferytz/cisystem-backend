package com.cosmetics.inventory.graphql;

import com.cosmetics.inventory.sales.SalesOrderEntity;

import java.util.List;

public record SalesOrderDto(
		Long id,
		String customer,
		Long customerId,
		String referenceNumber,
		String soldAt,
		String soldBy,
		java.math.BigDecimal discount,
		String paymentMethod,
		java.math.BigDecimal amountReceived,
		List<SalesOrderLineDto> lines,
		double returnedValue,
		boolean exchangeSale,
		String exchangeReference,
		List<SalesOrderLineDto> exchangeLines,
		String returnReference,
		java.math.BigDecimal exchangeReturnValue,
		java.math.BigDecimal exchangeSettlement,
		java.math.BigDecimal exchangeRefundAmount,
		java.math.BigDecimal exchangeDebtCredit,
		String exchangeRefundMethod
) {
	public static SalesOrderDto from(SalesOrderEntity so) {
		return from(so, java.util.Map.of(), java.math.BigDecimal.ZERO, false, null);
	}

	public static SalesOrderDto from(SalesOrderEntity so, java.util.Map<Long, Integer> returnedByLine, java.math.BigDecimal returnedValue,
			boolean exchangeSale, com.cosmetics.inventory.returns.SalesReturnEntity returnInfo) {
		var originalOrder = returnInfo != null ? returnInfo.getOriginalSalesOrder() : null;
		return new SalesOrderDto(
				so.getId(),
				so.getCustomer(),
				so.getCustomerRef() != null ? so.getCustomerRef().getId() : null,
				so.getReferenceNumber(),
				so.getSoldAt().toString(),
				so.getSoldBy(),
				so.getDiscount(),
				so.getPaymentMethod(),
				so.getAmountReceived(),
				so.getLines().stream().map(l -> SalesOrderLineDto.from(l, returnedByLine.getOrDefault(l.getId(), 0))).toList(),
				returnedValue.doubleValue(),
				exchangeSale,
				originalOrder != null ? originalOrder.getReferenceNumber() : null,
				originalOrder != null ? originalOrder.getLines().stream().map(SalesOrderLineDto::from).toList() : List.of(),
				returnInfo != null ? returnInfo.getReference() : null,
				returnInfo != null ? returnInfo.getReturnValue() : null,
				returnInfo != null ? returnInfo.getSettlementAmount() : null,
				returnInfo != null ? returnInfo.getRefundAmount() : null,
				returnInfo != null ? returnInfo.getDebtCreditAmount() : null,
				returnInfo != null ? returnInfo.getRefundMethod() : null
		);
	}
}
