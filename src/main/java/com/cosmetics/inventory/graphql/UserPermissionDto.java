package com.cosmetics.inventory.graphql;

import com.cosmetics.inventory.user.PermissionsService;
import com.cosmetics.inventory.user.RolePermissionEntity;
import com.cosmetics.inventory.user.UserPermissionEntity;

import java.util.List;

public record UserPermissionDto(
		String module,
		boolean canView,
		boolean canCreate,
		boolean canEdit,
		boolean canDelete,
		List<String> extras
) {
	public static UserPermissionDto from(UserPermissionEntity p) {
		return new UserPermissionDto(
				p.getModule().name(),
				p.isCanView(),
				p.isCanCreate(),
				p.isCanEdit(),
				p.isCanDelete(),
				p.isCanView() ? PermissionsService.extrasOf(p) : List.of()
		);
	}

	public static UserPermissionDto from(RolePermissionEntity p) {
		return new UserPermissionDto(
				p.getModule().name(),
				p.isCanView(),
				p.isCanCreate(),
				p.isCanEdit(),
				p.isCanDelete(),
				p.isCanView() ? PermissionsService.extrasOfRole(p) : List.of()
		);
	}
}
