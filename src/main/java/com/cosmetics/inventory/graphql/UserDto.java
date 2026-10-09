package com.cosmetics.inventory.graphql;

import com.cosmetics.inventory.user.UserEntity;

import java.util.List;

public record UserDto(
		Long id,
		String name,
		String email,
		List<String> roles,
		Long branchId,
		String branchCode,
		String branchName
) {
	public static UserDto from(UserEntity user) {
		var branch = user.getBranch();
		return new UserDto(
				user.getId(),
				user.getName(),
				user.getEmail(),
				user.getRoles().stream().map(r -> r.getName().name()).toList(),
				branch != null ? branch.getId() : null,
				branch != null ? branch.getCode() : null,
				branch != null ? branch.getName() : null
		);
	}
}
