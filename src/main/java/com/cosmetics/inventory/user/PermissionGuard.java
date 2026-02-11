package com.cosmetics.inventory.user;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PermissionGuard {
	private final PermissionsService permissionsService;
	private final UserRepository userRepository;

	public PermissionGuard(PermissionsService permissionsService, UserRepository userRepository) {
		this.permissionsService = permissionsService;
		this.userRepository = userRepository;
	}

	@Transactional(readOnly = true)
	public void require(Authentication authentication, PermissionModule module, PermissionsService.PermissionAction action) {
		if (authentication == null || authentication.getPrincipal() == null) {
			throw new AccessDeniedException("Not authenticated");
		}
		if (hasRole(authentication, "ROLE_ADMIN")) {
			return;
		}
		String email = String.valueOf(authentication.getPrincipal());
		UserEntity user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
		boolean ok = permissionsService.hasPermission(user.getId(), module, action);
		if (!ok) {
			throw new AccessDeniedException("Forbidden");
		}
	}

	private boolean hasRole(Authentication authentication, String role) {
		for (GrantedAuthority a : authentication.getAuthorities()) {
			if (a != null && role.equalsIgnoreCase(String.valueOf(a.getAuthority()))) {
				return true;
			}
		}
		return false;
	}
}
