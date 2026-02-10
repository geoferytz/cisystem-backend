package com.cosmetics.inventory.graphql;

import com.cosmetics.inventory.user.RoleEntity;
import com.cosmetics.inventory.user.RoleName;
import com.cosmetics.inventory.user.RoleRepository;
import com.cosmetics.inventory.user.UserEntity;
import com.cosmetics.inventory.user.UserRepository;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Controller
public class AdminUserGraphqlController {
	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	private final PasswordEncoder passwordEncoder;

	public AdminUserGraphqlController(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.roleRepository = roleRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@QueryMapping
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional(readOnly = true)
	public List<AdminUserDto> users() {
		return userRepository.findAll().stream().map(AdminUserDto::from).toList();
	}

	@QueryMapping
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional(readOnly = true)
	public List<String> roles() {
		return List.of(RoleName.values()).stream().map(Enum::name).toList();
	}

	@MutationMapping
	@PreAuthorize("hasRole('ADMIN')")
	public AdminUserDto createUser(@Argument CreateUserInput input) {
		userRepository.findByEmailIgnoreCase(input.email()).ifPresent(u -> {
			throw new IllegalArgumentException("Email already exists");
		});

		UserEntity user = new UserEntity();
		user.setName(input.name().trim());
		user.setEmail(input.email().trim());
		user.setPasswordHash(passwordEncoder.encode(input.password()));
		user.setActive(true);
		user.setRoles(resolveRoles(input.roles()));
		return AdminUserDto.from(userRepository.save(user));
	}

	@MutationMapping
	@PreAuthorize("hasRole('ADMIN')")
	public AdminUserDto setUserActive(@Argument SetUserActiveInput input) {
		UserEntity user = userRepository.findById(input.userId()).orElseThrow();
		user.setActive(input.active());
		return AdminUserDto.from(userRepository.save(user));
	}

	@MutationMapping
	@PreAuthorize("hasRole('ADMIN')")
	public AdminUserDto setUserRoles(@Argument SetUserRolesInput input) {
		UserEntity user = userRepository.findById(input.userId()).orElseThrow();
		user.setRoles(resolveRoles(input.roles()));
		return AdminUserDto.from(userRepository.save(user));
	}

	@MutationMapping
	@PreAuthorize("hasRole('ADMIN')")
	public AdminUserDto resetUserPassword(@Argument ResetUserPasswordInput input) {
		if (input.newPassword() == null || input.newPassword().isBlank()) {
			throw new IllegalArgumentException("Password is required");
		}
		UserEntity user = userRepository.findById(input.userId()).orElseThrow();
		user.setPasswordHash(passwordEncoder.encode(input.newPassword()));
		return AdminUserDto.from(userRepository.save(user));
	}

	private Set<RoleEntity> resolveRoles(List<String> roles) {
		if (roles == null || roles.isEmpty()) {
			throw new IllegalArgumentException("At least one role is required");
		}
		return roles.stream()
				.map(String::trim)
				.filter(s -> !s.isBlank())
				.map(s -> RoleName.valueOf(s.toUpperCase()))
				.map(roleName -> roleRepository.findByName(roleName).orElseThrow())
				.collect(java.util.stream.Collectors.toSet());
	}

	public record CreateUserInput(String name, String email, String password, List<String> roles) {
	}

	public record SetUserActiveInput(long userId, boolean active) {
	}

	public record SetUserRolesInput(long userId, List<String> roles) {
	}

	public record ResetUserPasswordInput(long userId, String newPassword) {
	}
}
