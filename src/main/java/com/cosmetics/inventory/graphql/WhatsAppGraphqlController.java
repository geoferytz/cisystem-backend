package com.cosmetics.inventory.graphql;

import com.cosmetics.inventory.user.PermissionGuard;
import com.cosmetics.inventory.user.PermissionModule;
import com.cosmetics.inventory.user.PermissionsService.PermissionAction;
import com.cosmetics.inventory.whatsapp.WhatsAppMessageDto;
import com.cosmetics.inventory.whatsapp.WhatsAppNotificationService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

@Controller
@PreAuthorize("isAuthenticated()")
public class WhatsAppGraphqlController {
	private final WhatsAppNotificationService whatsapp;
	private final PermissionGuard permissions;

	public WhatsAppGraphqlController(WhatsAppNotificationService whatsapp, PermissionGuard permissions) {
		this.whatsapp = whatsapp;
		this.permissions = permissions;
	}

	@QueryMapping
	public boolean whatsappEnabled() {
		return whatsapp.enabled();
	}

	@QueryMapping
	public WhatsAppMessageDto saleWhatsappReceipt(@Argument Long salesOrderId, Authentication authentication) {
		permissions.requireAny(authentication, PermissionAction.VIEW, PermissionModule.SALES, PermissionModule.COUNTER);
		return whatsapp.latestReceipt(salesOrderId);
	}

	@MutationMapping
	public WhatsAppMessageDto sendWhatsappReceipt(@Argument Long salesOrderId, @Argument String phone, Authentication authentication) {
		permissions.requireAny(authentication, PermissionAction.CREATE, PermissionModule.SALES, PermissionModule.COUNTER);
		return whatsapp.sendReceipt(salesOrderId, phone, authentication.getName());
	}

	@MutationMapping
	public WhatsAppMessageDto sendDebtReminder(@Argument Long customerId, Authentication authentication) {
		permissions.requireAny(authentication, PermissionAction.EDIT, PermissionModule.CUSTOMERS, PermissionModule.SALES);
		return whatsapp.sendDebtReminder(customerId, authentication.getName());
	}

	@MutationMapping
	public WhatsAppNotificationService.BulkResult sendDebtReminders(Authentication authentication) {
		permissions.requireAny(authentication, PermissionAction.EDIT, PermissionModule.CUSTOMERS, PermissionModule.SALES);
		return whatsapp.sendDebtReminders(authentication.getName());
	}
}
