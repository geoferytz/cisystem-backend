package com.cosmetics.inventory.graphql;

import com.cosmetics.inventory.sales.SalesOrderEntity;

import java.util.List;

public record SalesOrderDto(
		Long id,
		String customer,
		String referenceNumber,
		String soldAt,
		String soldBy,
		List<SalesOrderLineDto> lines
) {
	public static SalesOrderDto from(SalesOrderEntity so) {
		return new SalesOrderDto(
				so.getId(),
				so.getCustomer(),
				so.getReferenceNumber(),
				so.getSoldAt().toString(),
				so.getSoldBy(),
				so.getLines().stream().map(SalesOrderLineDto::from).toList()
		);
	}
}
