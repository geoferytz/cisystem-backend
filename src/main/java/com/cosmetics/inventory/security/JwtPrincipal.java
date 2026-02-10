package com.cosmetics.inventory.security;

import java.util.List;

public record JwtPrincipal(
		String subject,
		List<String> roles
) {
}
