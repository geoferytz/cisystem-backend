package com.cosmetics.inventory.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserPermissionRepository extends JpaRepository<UserPermissionEntity, Long> {
	List<UserPermissionEntity> findByUserIdOrderByModuleAsc(Long userId);
	Optional<UserPermissionEntity> findByUserIdAndModule(Long userId, PermissionModule module);
}
