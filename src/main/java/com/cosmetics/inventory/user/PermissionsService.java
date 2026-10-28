package com.cosmetics.inventory.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class PermissionsService {
	private final UserRepository userRepository;
	private final UserPermissionRepository userPermissionRepository;

	private final RolePermissionRepository rolePermissionRepository;
	private final RoleRepository roleRepository;

	public PermissionsService(UserRepository userRepository, UserPermissionRepository userPermissionRepository, RolePermissionRepository rolePermissionRepository, RoleRepository roleRepository) {
		this.userRepository = userRepository;
		this.userPermissionRepository = userPermissionRepository;
		this.rolePermissionRepository = rolePermissionRepository;
		this.roleRepository = roleRepository;
	}

	@Transactional(readOnly = true)
	public List<UserPermissionEntity> getPermissionsForUser(long userId) {
		return userPermissionRepository.findByUserIdOrderByModuleAsc(userId);
	}

	@Transactional
	public List<UserPermissionEntity> setPermissionsForUser(long userId, List<UpsertPermission> permissions) {
		UserEntity user = userRepository.findById(userId).orElseThrow();
		Map<PermissionModule, UpsertPermission> desired = new EnumMap<>(PermissionModule.class);
		if (permissions != null) {
			for (UpsertPermission p : permissions) {
				if (p == null || p.module() == null) continue;
				desired.put(p.module(), p);
			}
		}

		for (var entry : desired.entrySet()) {
			PermissionModule module = entry.getKey();
			UpsertPermission p = entry.getValue();
			UserPermissionEntity entity = userPermissionRepository.findByUserIdAndModule(user.getId(), module)
					.orElseGet(() -> {
						UserPermissionEntity e = new UserPermissionEntity();
						e.setUser(user);
						e.setModule(module);
						return e;
					});

			entity.setCanView(Boolean.TRUE.equals(p.canView()));
			entity.setCanCreate(Boolean.TRUE.equals(p.canCreate()));
			entity.setCanEdit(Boolean.TRUE.equals(p.canEdit()));
			entity.setCanDelete(Boolean.TRUE.equals(p.canDelete()));
			if (p.extras() != null) {
				entity.setExtraActions(String.join(",", p.extras().stream()
						.filter(code -> code != null && PermissionCatalog.isValid(module, code.trim().toUpperCase()))
						.map(code -> code.trim().toUpperCase())
						.distinct()
						.toList()));
			}
			userPermissionRepository.save(entity);
		}

		return userPermissionRepository.findByUserIdOrderByModuleAsc(user.getId());
	}

	@Transactional(readOnly = true)
	public boolean hasPermission(long userId, PermissionModule module, PermissionAction action) {
		var p = userPermissionRepository.findByUserIdAndModule(userId, module).orElse(null);
		if (p != null && granted(p, action)) return true;
		return roleRows(userId, module).stream().anyMatch(r -> granted(r, action));
	}

	/** Fine-grained check. Legacy rows (extras never configured) grant all non-sensitive extras when the module is viewable. */
	@Transactional(readOnly = true)
	public boolean hasExtra(long userId, PermissionModule module, String code) {
		if (roleRows(userId, module).stream().anyMatch(r -> r.isCanView() && extrasOfRole(r).contains(code))) {
			return true;
		}
		var p = userPermissionRepository.findByUserIdAndModule(userId, module).orElse(null);
		if (p == null || !p.isCanView()) return false;
		return extrasOf(p).contains(code);
	}

	/** True only when no role grants it AND the user's own row was configured without this action. */
	@Transactional(readOnly = true)
	public boolean explicitlyDenied(long userId, PermissionModule module, String code) {
		if (roleRows(userId, module).stream().anyMatch(r -> r.isCanView() && extrasOfRole(r).contains(code))) {
			return false;
		}
		var p = userPermissionRepository.findByUserIdAndModule(userId, module).orElse(null);
		return p != null && p.getExtraActions() != null && !extrasOf(p).contains(code);
	}

	/**
	 * Merged view of user-specific grants and all role grants — used for `myPermissions`
	 * so the frontend can hide/show UI exactly like the backend enforces.
	 */
	@Transactional(readOnly = true)
	public List<UserPermissionEntity> getEffectivePermissions(long userId) {
		UserEntity user = userRepository.findById(userId).orElseThrow();
		var userRows = new EnumMap<PermissionModule, UserPermissionEntity>(PermissionModule.class);
		for (var p : userPermissionRepository.findByUserIdOrderByModuleAsc(userId)) {
			userRows.put(p.getModule(), p);
		}
		var roleIds = user.getRoles().stream().map(r -> r.getId()).toList();

		List<UserPermissionEntity> effective = new java.util.ArrayList<>();
		for (PermissionModule module : PermissionModule.values()) {
			UserPermissionEntity up = userRows.get(module);
			List<RolePermissionEntity> rps = roleIds.isEmpty() ? List.of()
					: rolePermissionRepository.findByRoleIdInAndModule(roleIds, module);
			if (up == null && rps.isEmpty()) continue;

			UserPermissionEntity merged = new UserPermissionEntity();
			merged.setUser(user);
			merged.setModule(module);
			merged.setCanView((up != null && up.isCanView()) || rps.stream().anyMatch(RolePermissionEntity::isCanView));
			merged.setCanCreate((up != null && up.isCanCreate()) || rps.stream().anyMatch(RolePermissionEntity::isCanCreate));
			merged.setCanEdit((up != null && up.isCanEdit()) || rps.stream().anyMatch(RolePermissionEntity::isCanEdit));
			merged.setCanDelete((up != null && up.isCanDelete()) || rps.stream().anyMatch(RolePermissionEntity::isCanDelete));

			java.util.Set<String> extras = new java.util.LinkedHashSet<>();
			if (up != null && up.isCanView()) extras.addAll(extrasOf(up));
			for (var rp : rps) {
				if (rp.isCanView()) extras.addAll(extrasOfRole(rp));
			}
			merged.setExtraActions(String.join(",", extras));
			effective.add(merged);
		}
		return effective;
	}

	public static boolean granted(UserPermissionEntity p, PermissionAction action) {
		return switch (action) {
			case VIEW -> p.isCanView();
			case CREATE -> p.isCanCreate();
			case EDIT -> p.isCanEdit();
			case DELETE -> p.isCanDelete();
		};
	}

	public static boolean granted(RolePermissionEntity p, PermissionAction action) {
		return switch (action) {
			case VIEW -> p.isCanView();
			case CREATE -> p.isCanCreate();
			case EDIT -> p.isCanEdit();
			case DELETE -> p.isCanDelete();
		};
	}

	public static List<String> extrasOfRole(RolePermissionEntity p) {
		if (p.getExtraActions() == null) {
			return PermissionCatalog.extras(p.getModule()).stream().filter(a -> !a.sensitive()).map(PermissionCatalog.Action::code).toList();
		}
		return java.util.Arrays.stream(p.getExtraActions().split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
	}

	private List<RolePermissionEntity> roleRows(long userId, PermissionModule module) {
		var roleIds = userRepository.findById(userId)
				.map(u -> u.getRoles().stream().map(r -> r.getId()).toList())
				.orElse(List.of());
		if (roleIds.isEmpty()) return List.of();
		return rolePermissionRepository.findByRoleIdInAndModule(roleIds, module);
	}

	public static List<String> extrasOf(UserPermissionEntity p) {
		if (p.getExtraActions() == null) {
			return PermissionCatalog.extras(p.getModule()).stream().filter(a -> !a.sensitive()).map(PermissionCatalog.Action::code).toList();
		}
		return java.util.Arrays.stream(p.getExtraActions().split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
	}

	// ---- Role permissions ----

	@Transactional(readOnly = true)
	public List<RolePermissionEntity> getPermissionsForRole(long roleId) {
		return rolePermissionRepository.findByRoleIdOrderByModuleAsc(roleId);
	}

	@Transactional
	public List<RolePermissionEntity> setPermissionsForRole(long roleId, List<UpsertPermission> permissions) {
		RoleEntity role = roleRepository.findById(roleId).orElseThrow(() -> new IllegalArgumentException("Role not found"));
		Map<PermissionModule, UpsertPermission> desired = new EnumMap<>(PermissionModule.class);
		if (permissions != null) {
			for (UpsertPermission p : permissions) {
				if (p == null || p.module() == null) continue;
				desired.put(p.module(), p);
			}
		}

		for (var entry : desired.entrySet()) {
			PermissionModule module = entry.getKey();
			UpsertPermission p = entry.getValue();
			RolePermissionEntity entity = rolePermissionRepository.findByRoleIdAndModule(role.getId(), module)
					.orElseGet(() -> {
						RolePermissionEntity e = new RolePermissionEntity();
						e.setRole(role);
						e.setModule(module);
						return e;
					});

			entity.setCanView(Boolean.TRUE.equals(p.canView()));
			entity.setCanCreate(Boolean.TRUE.equals(p.canCreate()));
			entity.setCanEdit(Boolean.TRUE.equals(p.canEdit()));
			entity.setCanDelete(Boolean.TRUE.equals(p.canDelete()));
			if (p.extras() != null) {
				entity.setExtraActions(String.join(",", p.extras().stream()
						.filter(code -> code != null && PermissionCatalog.isValid(module, code.trim().toUpperCase()))
						.map(code -> code.trim().toUpperCase())
						.distinct()
						.toList()));
			}
			rolePermissionRepository.save(entity);
		}

		return rolePermissionRepository.findByRoleIdOrderByModuleAsc(role.getId());
	}

	public enum PermissionAction {
		VIEW,
		CREATE,
		EDIT,
		DELETE
	}

	public record UpsertPermission(PermissionModule module, Boolean canView, Boolean canCreate, Boolean canEdit, Boolean canDelete, List<String> extras) {
	}
}
