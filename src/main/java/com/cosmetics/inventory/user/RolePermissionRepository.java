package com.cosmetics.inventory.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RolePermissionRepository extends JpaRepository<RolePermissionEntity, Long> {
	List<RolePermissionEntity> findByRoleIdOrderByModuleAsc(long roleId);

	Optional<RolePermissionEntity> findByRoleIdAndModule(long roleId, PermissionModule module);

	List<RolePermissionEntity> findByRoleIdInAndModule(Collection<Long> roleIds, PermissionModule module);

	long countByRoleId(long roleId);
}
