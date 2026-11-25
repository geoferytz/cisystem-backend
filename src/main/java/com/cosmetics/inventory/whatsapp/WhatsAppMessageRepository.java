package com.cosmetics.inventory.whatsapp;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WhatsAppMessageRepository extends JpaRepository<WhatsAppMessageEntity, Long> {
	Optional<WhatsAppMessageEntity> findFirstByWamid(String wamid);

	Optional<WhatsAppMessageEntity> findTopBySalesOrderIdAndKindOrderByIdDesc(Long salesOrderId, WhatsAppMessageEntity.Kind kind);

	Optional<WhatsAppMessageEntity> findTopByCustomerIdAndKindOrderByIdDesc(Long customerId, WhatsAppMessageEntity.Kind kind);
}
