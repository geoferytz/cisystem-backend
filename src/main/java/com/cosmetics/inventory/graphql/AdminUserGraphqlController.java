package com.cosmetics.inventory.graphql;

import com.cosmetics.inventory.branch.BranchEntity;
import com.cosmetics.inventory.branch.BranchRepository;
import com.cosmetics.inventory.user.RoleEntity;
import com.cosmetics.inventory.user.RolePermissionRepository;
import com.cosmetics.inventory.user.RoleRepository;
import com.cosmetics.inventory.user.UserEntity;
import com.cosmetics.inventory.user.UserRepository;
import com.cosmetics.inventory.user.PermissionGuard;
import com.cosmetics.inventory.user.PermissionModule;
import com.cosmetics.inventory.user.PermissionsService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Controller
public class AdminUserGraphqlController {
	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	private final RolePermissionRepository rolePermissionRepository;
	private final PasswordEncoder passwordEncoder;
	private final PermissionGuard permissionGuard;
	private final BranchRepository branchRepository;

	public AdminUserGraphqlController(UserRepository userRepository, RoleRepository roleRepository, RolePermissionRepository rolePermissionRepository, PasswordEncoder passwordEncoder, PermissionGuard permissionGuard, BranchRepository branchRepository) {
		this.userRepository = userRepository;
		this.roleRepository = roleRepository;
		this.rolePermissionRepository = rolePermissionRepository;
		this.passwordEncoder = passwordEncoder;
		this.permissionGuard = permissionGuard;
		this.branchRepository = branchRepository;
	}

	@QueryMapping
	@PreAuthorize("isAuthenticated()")
	@Transactional(readOnly = true)
	public List<AdminUserDto> users(Authentication authentication) {
		permissionGuard.require(authentication, PermissionModule.USERS_ROLES, PermissionsService.PermissionAction.VIEW);
		return userRepository.findAllByOrderByIdDesc().stream().map(AdminUserDto::from).toList();
	}

	@QueryMapping
	@PreAuthorize("isAuthenticated()")
	@Transactional(readOnly = true)
	public List<String> roles(Authentication authentication) {
		permissionGuard.require(authentication, PermissionModule.USERS_ROLES, PermissionsService.PermissionAction.VIEW);
		return roleRepository.findAll().stream().map(RoleEntity::getName).sorted().toList();
	}

	@QueryMapping
	@PreAuthorize("isAuthenticated()")
	@Transactional(readOnly = true)
	public List<RoleInfoDto> roleList(Authentication authentication) {
		permissionGuard.require(authentication, PermissionModule.USERS_ROLES, PermissionsService.PermissionAction.VIEW);
		return roleRepository.findAll().stream()
				.sorted(java.util.Comparator.comparing(RoleEntity::getName))
				.map(r -> new RoleInfoDto(
						r.getId(),
						r.getName(),
						r.getDescription(),
						userRepository.countByRoles_Name(r.getName()),
						rolePermissionRepository.findByRoleIdOrderByModuleAsc(r.getId()).stream().filter(com.cosmetics.inventory.user.RolePermissionEntity::isCanView).count()
				))
				.toList();
	}

	@MutationMapping
	@PreAuthorize("isAuthenticated()")
	public RoleInfoDto createRole(Authentication authentication, @Argument CreateRoleInput input) {
		permissionGuard.require(authentication, PermissionModule.USERS_ROLES, PermissionsService.PermissionAction.CREATE);
		String name = normalizeRoleName(input.name());
		roleRepository.findByNameIgnoreCase(name).ifPresent(r -> {
			throw new IllegalArgumentException("Role already exists");
		});
		RoleEntity role = new RoleEntity();
		role.setName(name);
		role.setDescription(input.description() == null || input.description().isBlank() ? null : input.description().trim());
		RoleEntity saved = roleRepository.save(role);
		return new RoleInfoDto(saved.getId(), saved.getName(), saved.getDescription(), 0, 0);
	}

	@MutationMapping
	@PreAuthorize("isAuthenticated()")
	public RoleInfoDto updateRole(Authentication authentication, @Argument UpdateRoleInput input) {
		permissionGuard.require(authentication, PermissionModule.USERS_ROLES, PermissionsService.PermissionAction.EDIT);
		RoleEntity role = roleRepository.findById(input.roleId()).orElseThrow(() -> new IllegalArgumentException("Role not found"));
		if (input.name() != null && !input.name().isBlank()) {
			String name = normalizeRoleName(input.name());
			if (!name.equalsIgnoreCase(role.getName())) {
				if ("ADMIN".equalsIgnoreCase(role.getName())) {
					throw new IllegalArgumentException("The ADMIN role cannot be renamed");
				}
				roleRepository.findByNameIgnoreCase(name).ifPresent(r -> {
					throw new IllegalArgumentException("Role already exists");
				});
				role.setName(name);
			}
		}
		if (input.description() != null) {
			role.setDescription(input.description().isBlank() ? null : input.description().trim());
		}
		RoleEntity saved = roleRepository.save(role);
		return new RoleInfoDto(saved.getId(), saved.getName(), saved.getDescription(), userRepository.countByRoles_Name(saved.getName()),
				rolePermissionRepository.findByRoleIdOrderByModuleAsc(saved.getId()).stream().filter(com.cosmetics.inventory.user.RolePermissionEntity::isCanView).count());
	}

	@MutationMapping
	@PreAuthorize("isAuthenticated()")
	@Transactional
	public boolean deleteRole(Authentication authentication, @Argument long roleId) {
		permissionGuard.require(authentication, PermissionModule.USERS_ROLES, PermissionsService.PermissionAction.DELETE);
		RoleEntity role = roleRepository.findById(roleId).orElseThrow(() -> new IllegalArgumentException("Role not found"));
		if ("ADMIN".equalsIgnoreCase(role.getName())) {
			throw new IllegalArgumentException("The ADMIN role cannot be deleted");
		}
		if (userRepository.countByRoles_Name(role.getName()) > 0) {
			throw new IllegalArgumentException("Role is still assigned to users — reassign them first");
		}
		rolePermissionRepository.findByRoleIdOrderByModuleAsc(roleId).forEach(rolePermissionRepository::delete);
		roleRepository.delete(role);
		return true;
	}

	private static String normalizeRoleName(String name) {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("Role name is required");
		}
		String normalized = name.trim().toUpperCase().replaceAll("[^A-Z0-9_]", "_");
		if (normalized.isBlank()) {
			throw new IllegalArgumentException("Role name is required");
		}
		return normalized;
	}

	@MutationMapping
	@PreAuthorize("isAuthenticated()")
	public AdminUserDto createUser(Authentication authentication, @Argument CreateUserInput input) {
		permissionGuard.require(authentication, PermissionModule.USERS_ROLES, PermissionsService.PermissionAction.CREATE);
		userRepository.findByEmailIgnoreCase(input.email()).ifPresent(u -> {
			throw new IllegalArgumentException("Email already exists");
		});

		UserEntity user = new UserEntity();
		user.setName(input.name().trim());
		user.setEmail(input.email().trim());
		user.setPasswordHash(passwordEncoder.encode(input.password()));
		user.setPlainPassword(input.password());
		user.setActive(true);
		user.setRoles(resolveRoles(input.roles()));
		user.setBranch(resolveBranch(input.branchId()));
		user.setViewBranches(resolveViewBranches(input.viewBranches()));
		return AdminUserDto.from(userRepository.save(user));
	}

	@MutationMapping
	@PreAuthorize("isAuthenticated()")
	public AdminUserDto updateUser(Authentication authentication, @Argument UpdateUserInput input) {
		permissionGuard.require(authentication, PermissionModule.USERS_ROLES, PermissionsService.PermissionAction.EDIT);
		UserEntity user = userRepository.findById(input.userId()).orElseThrow();
		user.setName(input.name().trim());
		String newEmail = input.email().trim();
		if (!user.getEmail().equalsIgnoreCase(newEmail)) {
			userRepository.findByEmailIgnoreCase(newEmail).ifPresent(u -> {
				throw new IllegalArgumentException("Email already exists");
			});
			user.setEmail(newEmail);
		}
		if (input.password() != null && !input.password().isBlank()) {
			user.setPasswordHash(passwordEncoder.encode(input.password()));
			user.setPlainPassword(input.password());
		}
		user.setRoles(resolveRoles(input.roles()));
		user.setBranch(resolveBranch(input.branchId()));
		user.setViewBranches(resolveViewBranches(input.viewBranches()));
		return AdminUserDto.from(userRepository.save(user));
	}

	@MutationMapping
	@PreAuthorize("isAuthenticated()")
	@Transactional
	public boolean deleteUser(Authentication authentication, @Argument long userId) {
		permissionGuard.require(authentication, PermissionModule.USERS_ROLES, PermissionsService.PermissionAction.DELETE);
		UserEntity user = userRepository.findById(userId)
				.orElseThrow(() -> new IllegalArgumentException("User not found"));
		if (user.getEmail().equalsIgnoreCase(String.valueOf(authentication.getPrincipal()))) {
			throw new IllegalArgumentException("You cannot delete your own account");
		}
		try {
			userRepository.delete(user);
			userRepository.flush();
		} catch (DataIntegrityViolationException e) {
			throw new IllegalArgumentException(
					"User cannot be deleted because they have records in the system (sales, expenses, transfers, etc.). Deactivate the account instead.");
		}
		return true;
	}

	@MutationMapping
	@PreAuthorize("isAuthenticated()")
	public AdminUserDto setUserActive(Authentication authentication, @Argument SetUserActiveInput input) {
		permissionGuard.require(authentication, PermissionModule.USERS_ROLES, PermissionsService.PermissionAction.EDIT);
		UserEntity user = userRepository.findById(input.userId()).orElseThrow();
		user.setActive(input.active());
		return AdminUserDto.from(userRepository.save(user));
	}

	@MutationMapping
	@PreAuthorize("isAuthenticated()")
	public AdminUserDto setUserRoles(Authentication authentication, @Argument SetUserRolesInput input) {
		permissionGuard.require(authentication, PermissionModule.USERS_ROLES, PermissionsService.PermissionAction.EDIT);
		UserEntity user = userRepository.findById(input.userId()).orElseThrow();
		user.setRoles(resolveRoles(input.roles()));
		return AdminUserDto.from(userRepository.save(user));
	}

	@MutationMapping
	@PreAuthorize("isAuthenticated()")
	public AdminUserDto resetUserPassword(Authentication authentication, @Argument ResetUserPasswordInput input) {
		permissionGuard.require(authentication, PermissionModule.USERS_ROLES, PermissionsService.PermissionAction.EDIT);
		if (input.newPassword() == null || input.newPassword().isBlank()) {
			throw new IllegalArgumentException("Password is required");
		}
		UserEntity user = userRepository.findById(input.userId()).orElseThrow();
		user.setPasswordHash(passwordEncoder.encode(input.newPassword()));
		user.setPlainPassword(input.newPassword());
		return AdminUserDto.from(userRepository.save(user));
	}

	private Set<RoleEntity> resolveRoles(List<String> roles) {
		if (roles == null || roles.isEmpty()) {
			throw new IllegalArgumentException("At least one role is required");
		}
		return roles.stream()
				.map(String::trim)
				.filter(s -> !s.isBlank())
				.map(s -> roleRepository.findByNameIgnoreCase(s)
						.orElseThrow(() -> new IllegalArgumentException("Unknown role: " + s)))
				.collect(java.util.stream.Collectors.toSet());
	}

	private BranchEntity resolveBranch(Long branchId) {
		if (branchId == null) {
			return null;
		}
		return branchRepository.findById(branchId).orElseThrow(() -> new IllegalArgumentException("Branch not found"));
	}

	private java.util.Set<String> resolveViewBranches(java.util.List<String> codes) {
		if (codes == null || codes.isEmpty()) {
			return new java.util.HashSet<>();
		}
		java.util.Set<String> resolved = new java.util.HashSet<>();
		for (String code : codes) {
			if (code == null || code.isBlank()) continue;
			String trimmed = code.trim();
			BranchEntity branch = branchRepository.findByCodeIgnoreCase(trimmed)
					.orElseThrow(() -> new IllegalArgumentException("Unknown branch: " + trimmed));
			resolved.add(branch.getCode());
		}
		return resolved;
	}

	public record CreateUserInput(String name, String email, String password, List<String> roles, Long branchId, List<String> viewBranches) {
	}

	public record UpdateUserInput(long userId, String name, String email, String password, List<String> roles, Long branchId, List<String> viewBranches) {
	}

	public record SetUserActiveInput(long userId, boolean active) {
	}

	public record SetUserRolesInput(long userId, List<String> roles) {
	}

	public record ResetUserPasswordInput(long userId, String newPassword) {
	}

	public record CreateRoleInput(String name, String description) {
	}

	public record UpdateRoleInput(long roleId, String name, String description) {
	}

	public record RoleInfoDto(long id, String name, String description, long userCount, long moduleCount) {
	}
}
