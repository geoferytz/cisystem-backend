package com.cosmetics.inventory.whatsapp;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.whatsapp")
public record WhatsAppProperties(
		boolean enabled,
		String phoneNumberId,
		String accessToken,
		String appSecret,
		String verifyToken,
		String apiVersion,
		String templateLanguage,
		String receiptTemplate,
		String debtTemplate,
		boolean autoReceipt,
		int reminderMinDays
) {
	/** True only when switched on AND the credentials needed to send are present. */
	public boolean ready() {
		return enabled && notBlank(phoneNumberId) && notBlank(accessToken);
	}

	private static boolean notBlank(String s) {
		return s != null && !s.isBlank();
	}
}
