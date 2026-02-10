package com.cosmetics.inventory.graphql;

import com.cosmetics.inventory.mysales.MySaleLineEntity;

public record MySaleLineDto(
		Long id,
		String productName,
		int quantity,
		double unitPrice
) {
	public static MySaleLineDto from(MySaleLineEntity l) {
		return new MySaleLineDto(
				l.getId(),
				l.getProductName(),
				l.getQuantity(),
				l.getUnitPrice().doubleValue()
		);
	}
}
