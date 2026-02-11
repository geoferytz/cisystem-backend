package com.cosmetics.inventory.graphql;

import com.cosmetics.inventory.user.PermissionModule;
import com.cosmetics.inventory.user.PermissionGuard;
import com.cosmetics.inventory.user.PermissionsService;
import com.cosmetics.inventory.user.UserEntity;
import com.cosmetics.inventory.user.UserRepository;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Controller
public class PermissionsGraphqlController {
	private final PermissionsService permissionsService;
	private final UserRepository userRepository;
	private final PermissionGuard permissionGuard;

	public PermissionsGraphqlController(PermissionsService permissionsService, UserRepository userRepository, PermissionGuard permissionGuard) {
		this.permissionsService = permissionsService;
		this.userRepository = userRepository;
		this.permissionGuard = permissionGuard;
	}

	@QueryMapping
	@PreAuthorize("isAuthenticated()")
	@Transactional(readOnly = true)
	public List<UserPermissionDto> userPermissions(Authentication authentication, @Argument long userId) {
		permissionGuard.require(authentication, PermissionModule.USERS_ROLES, PermissionsService.PermissionAction.VIEW);
		return permissionsService.getPermissionsForUser(userId).stream().map(UserPermissionDto::from).toList();
	}

	@QueryMapping
	@PreAuthorize("isAuthenticated()")
	@Transactional(readOnly = true)
	public List<UserPermissionDto> myPermissions(Authentication authentication) {
		UserEntity u = resolveCurrentUser(authentication);
		return permissionsService.getPermissionsForUser(u.getId()).stream().map(UserPermissionDto::from).toList();
	}

	@MutationMapping
	@PreAuthorize("isAuthenticated()")
	@Transactional
	public List<UserPermissionDto> setUserPermissions(Authentication authentication, @Argument SetUserPermissionsInput input) {
		permissionGuard.require(authentication, PermissionModule.USERS_ROLES, PermissionsService.PermissionAction.EDIT);
		List<PermissionsService.UpsertPermission> perms = input.permissions() == null ? List.of() : input.permissions().stream()
				.map(p -> new PermissionsService.UpsertPermission(
						PermissionModule.valueOf(p.module().trim().toUpperCase()),
						p.canView(),
						p.canCreate(),
						p.canEdit(),
						p.canDelete()
				)).toList();
		return permissionsService.setPermissionsForUser(input.userId(), perms).stream().map(UserPermissionDto::from).toList();
	}

	private UserEntity resolveCurrentUser(Authentication authentication) {
		if (authentication == null || authentication.getPrincipal() == null) {
			throw new IllegalArgumentException("Not authenticated");
		}
		String email = String.valueOf(authentication.getPrincipal());
		return userRepository.findByEmailIgnoreCase(email).orElseThrow();
	}

	public record PermissionInput(String module, Boolean canView, Boolean canCreate, Boolean canEdit, Boolean canDelete) {
	}

	public record SetUserPermissionsInput(long userId, List<PermissionInput> permissions) {
	}
}
