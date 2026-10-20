package com.cosmetics.inventory.graphql;

import com.cosmetics.inventory.customer.CustomerPaymentEntity;

public record CustomerPaymentDto(String id, String customerId, String salesOrderId, double amount, String paidAt) {
	public static CustomerPaymentDto from(CustomerPaymentEntity p) {
		return new CustomerPaymentDto(
				String.valueOf(p.getId()),
				String.valueOf(p.getCustomer().getId()),
				String.valueOf(p.getSalesOrder().getId()),
				p.getAmount().doubleValue(),
				p.getPaidAt().toString());
	}
}
