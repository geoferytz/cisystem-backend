package com.cosmetics.inventory.graphql;

import com.cosmetics.inventory.mysales.MySaleEntity;

import java.util.List;

public record MySaleDto(
		Long id,
		String createdAt,
		String createdBy,
		String customer,
		String referenceNumber,
		List<MySaleLineDto> lines
) {
	public static MySaleDto from(MySaleEntity s) {
		return new MySaleDto(
				s.getId(),
				s.getCreatedAt().toString(),
				s.getCreatedBy(),
				s.getCustomer(),
				s.getReferenceNumber(),
				s.getLines().stream().map(MySaleLineDto::from).toList()
		);
	}
}
