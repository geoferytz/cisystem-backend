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
		List<SalesOrderLineDto> lines
) {
	public static SalesOrderDto from(SalesOrderEntity so) {
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
				so.getLines().stream().map(SalesOrderLineDto::from).toList()
		);
	}
}
