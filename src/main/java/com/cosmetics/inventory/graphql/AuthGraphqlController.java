package com.cosmetics.inventory.graphql;

import com.cosmetics.inventory.security.JwtService;
import com.cosmetics.inventory.user.UserEntity;
import com.cosmetics.inventory.user.UserRepository;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
public class AuthGraphqlController {
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;

	public AuthGraphqlController(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
	}

	@MutationMapping
	public AuthPayload login(@Argument LoginInput input) {
		UserEntity user = userRepository.findByEmailIgnoreCase(input.email())
				.orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));
		if (!user.isActive()) {
			throw new IllegalArgumentException("User is inactive");
		}
		if (!passwordEncoder.matches(input.password(), user.getPasswordHash())) {
			throw new IllegalArgumentException("Invalid credentials");
		}

		List<String> roles = user.getRoles().stream().map(r -> r.getName().name()).toList();
		String token = jwtService.createAccessToken(user.getEmail(), roles);
		return new AuthPayload(token);
	}

	@MutationMapping
	@PreAuthorize("isAuthenticated()")
	public boolean changeMyPassword(@Argument ChangeMyPasswordInput input, Authentication authentication) {
		String email = (String) authentication.getPrincipal();
		UserEntity user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
		if (!passwordEncoder.matches(input.currentPassword(), user.getPasswordHash())) {
			throw new IllegalArgumentException("Invalid current password");
		}
		user.setPasswordHash(passwordEncoder.encode(input.newPassword()));
		userRepository.save(user);
		return true;
	}

	@QueryMapping
	@PreAuthorize("isAuthenticated()")
	public UserDto me(Authentication authentication) {
		String email = (String) authentication.getPrincipal();
		UserEntity user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
		return UserDto.from(user);
	}

	public record LoginInput(String email, String password) {
	}

	public record ChangeMyPasswordInput(String currentPassword, String newPassword) {
	}

	public record AuthPayload(String accessToken) {
	}
}
