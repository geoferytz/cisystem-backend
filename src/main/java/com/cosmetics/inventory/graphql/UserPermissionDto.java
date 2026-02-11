package com.cosmetics.inventory.graphql;

import com.cosmetics.inventory.user.UserPermissionEntity;

public record UserPermissionDto(
		String module,
		boolean canView,
		boolean canCreate,
		boolean canEdit,
		boolean canDelete
) {
	public static UserPermissionDto from(UserPermissionEntity p) {
		return new UserPermissionDto(
				p.getModule().name(),
				p.isCanView(),
				p.isCanCreate(),
				p.isCanEdit(),
				p.isCanDelete()
		);
	}
}
