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

	public PermissionsService(UserRepository userRepository, UserPermissionRepository userPermissionRepository) {
		this.userRepository = userRepository;
		this.userPermissionRepository = userPermissionRepository;
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
			userPermissionRepository.save(entity);
		}

		return userPermissionRepository.findByUserIdOrderByModuleAsc(user.getId());
	}

	@Transactional(readOnly = true)
	public boolean hasPermission(long userId, PermissionModule module, PermissionAction action) {
		var p = userPermissionRepository.findByUserIdAndModule(userId, module).orElse(null);
		if (p == null) return false;
		return switch (action) {
			case VIEW -> p.isCanView();
			case CREATE -> p.isCanCreate();
			case EDIT -> p.isCanEdit();
			case DELETE -> p.isCanDelete();
		};
	}

	public enum PermissionAction {
		VIEW,
		CREATE,
		EDIT,
		DELETE
	}

	public record UpsertPermission(PermissionModule module, Boolean canView, Boolean canCreate, Boolean canEdit, Boolean canDelete) {
	}
}
