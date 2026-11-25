package com.cosmetics.inventory.whatsapp;

public record WhatsAppMessageDto(Long id, String kind, String status, String phone, String error, String createdAt, String updatedAt) {
	public static WhatsAppMessageDto from(WhatsAppMessageEntity m) {
		return new WhatsAppMessageDto(m.getId(), m.getKind().name(), m.getStatus().name(), m.getPhone(), m.getError(),
				m.getCreatedAt().toString(), m.getUpdatedAt() != null ? m.getUpdatedAt().toString() : null);
	}
}
