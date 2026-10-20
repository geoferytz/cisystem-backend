package com.cosmetics.inventory.graphql;

import com.cosmetics.inventory.customer.CustomerEntity;

public record CustomerDto(
		Long id,
		String name,
		String phone,
		String email,
		String address,
		String createdAt,
		String createdBy
) {
	public static CustomerDto from(CustomerEntity c) {
		return new CustomerDto(
				c.getId(),
				c.getName(),
				c.getPhone(),
				c.getEmail(),
				c.getAddress(),
				c.getCreatedAt().toString(),
				c.getCreatedBy()
		);
	}
}
