package com.cosmetics.inventory.graphql;

import com.cosmetics.inventory.user.UserEntity;

import java.util.List;

public record AdminUserDto(
		Long id,
		String name,
		String email,
		boolean active,
		List<String> roles
) {
	public static AdminUserDto from(UserEntity user) {
		return new AdminUserDto(
				user.getId(),
				user.getName(),
				user.getEmail(),
				user.isActive(),
				user.getRoles().stream().map(r -> r.getName().name()).toList()
		);
	}
}
