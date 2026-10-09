package com.cosmetics.inventory.graphql;

import com.cosmetics.inventory.user.UserEntity;

import java.util.List;

public record AdminUserDto(
		Long id,
		String name,
		String email,
		String plainPassword,
		boolean active,
		List<String> roles,
		Long branchId,
		String branchCode,
		String branchName
) {
	public static AdminUserDto from(UserEntity user) {
		var branch = user.getBranch();
		return new AdminUserDto(
				user.getId(),
				user.getName(),
				user.getEmail(),
				user.getPlainPassword(),
				user.isActive(),
				user.getRoles().stream().map(r -> r.getName().name()).toList(),
				branch != null ? branch.getId() : null,
				branch != null ? branch.getCode() : null,
				branch != null ? branch.getName() : null
		);
	}
}
