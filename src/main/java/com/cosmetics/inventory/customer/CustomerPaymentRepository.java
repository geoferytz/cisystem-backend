package com.cosmetics.inventory.customer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerPaymentRepository extends JpaRepository<CustomerPaymentEntity, Long> {
	List<CustomerPaymentEntity> findTop50ByCustomer_IdOrderByPaidAtDescIdDesc(Long customerId);
}
