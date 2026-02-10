package com.cosmetics.inventory.graphql;

import com.cosmetics.inventory.user.UserEntity;

import java.util.List;

public record UserDto(
		Long id,
		String name,
		String email,
		List<String> roles
) {
	public static UserDto from(UserEntity user) {
		return new UserDto(
				user.getId(),
				user.getName(),
				user.getEmail(),
				user.getRoles().stream().map(r -> r.getName().name()).toList()
		);
	}
}
