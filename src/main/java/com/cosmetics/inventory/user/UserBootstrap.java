package com.cosmetics.inventory.user;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class UserBootstrap implements ApplicationRunner {
	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	private final PasswordEncoder passwordEncoder;
	private final Environment env;

	public UserBootstrap(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder, Environment env) {
		this.userRepository = userRepository;
		this.roleRepository = roleRepository;
		this.passwordEncoder = passwordEncoder;
		this.env = env;
	}

	@Override
	public void run(ApplicationArguments args) {
		for (RoleName roleName : RoleName.values()) {
			roleRepository.findByName(roleName).orElseGet(() -> {
				RoleEntity r = new RoleEntity();
				r.setName(roleName);
				return roleRepository.save(r);
			});
		}

		String email = env.getProperty("APP_ADMIN_EMAIL", "admin@cisystem.local");
		String password = env.getProperty("APP_ADMIN_PASSWORD", "ChangeMe123!");
		String name = env.getProperty("APP_ADMIN_NAME", "System Admin");

		userRepository.findByEmailIgnoreCase(email).orElseGet(() -> {
			UserEntity u = new UserEntity();
			u.setEmail(email);
			u.setName(name);
			u.setPasswordHash(passwordEncoder.encode(password));
			RoleEntity adminRole = roleRepository.findByName(RoleName.ADMIN).orElseThrow();
			u.setRoles(Set.of(adminRole));
			return userRepository.save(u);
		});
	}
}
