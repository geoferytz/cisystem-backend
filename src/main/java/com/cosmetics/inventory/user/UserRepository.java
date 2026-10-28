package com.cosmetics.inventory.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
	Optional<UserEntity> findByEmailIgnoreCase(String email);

	List<UserEntity> findAllByOrderByIdDesc();

	long countByRoles_Name(String name);
}
