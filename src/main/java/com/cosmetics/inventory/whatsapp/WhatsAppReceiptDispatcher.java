package com.cosmetics.inventory.whatsapp;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Sends the receipt on a background thread so checkout never waits on (or fails because of) Meta.
 * Must be a separate bean from the caller for @Async to take effect.
 */
@Component
public class WhatsAppReceiptDispatcher {
	private final WhatsAppNotificationService notifications;

	public WhatsAppReceiptDispatcher(WhatsAppNotificationService notifications) {
		this.notifications = notifications;
	}

	@Async
	public void dispatch(Long salesOrderId, String by) {
		notifications.autoSendReceipt(salesOrderId, by);
	}
}
