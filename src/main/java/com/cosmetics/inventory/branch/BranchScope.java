package com.cosmetics.inventory.branch;

import com.cosmetics.inventory.user.UserEntity;
import com.cosmetics.inventory.user.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BranchScope {
	private final UserRepository userRepository;
	private final BranchRepository branchRepository;

	public BranchScope(UserRepository userRepository, BranchRepository branchRepository) {
		this.userRepository = userRepository;
		this.branchRepository = branchRepository;
	}

	/**
	 * Branch code the current user is locked to, or null when unrestricted
	 * (admins and users without an assigned branch see all branches).
	 */
	@Transactional(readOnly = true)
	public String lockedBranchCode(Authentication authentication) {
		if (authentication == null || authentication.getPrincipal() == null) {
			return null;
		}
		for (GrantedAuthority a : authentication.getAuthorities()) {
			if (a != null && "ROLE_ADMIN".equalsIgnoreCase(String.valueOf(a.getAuthority()))) {
				return null;
			}
		}
		UserEntity user = userRepository.findByEmailIgnoreCase(String.valueOf(authentication.getPrincipal())).orElse(null);
		if (user == null || user.getBranch() == null) {
			return null;
		}
		return user.getBranch().getCode();
	}

	/**
	 * Write paths: returns the caller's locked branch when restricted,
	 * otherwise validates the requested code (blank falls back to the main branch).
	 */
	@Transactional(readOnly = true)
	public String resolveLocation(Authentication authentication, String requested) {
		String locked = lockedBranchCode(authentication);
		if (locked != null) {
			return locked;
		}
		if (requested == null || requested.isBlank()) {
			return mainCode();
		}
		String code = requested.trim();
		BranchEntity branch = branchRepository.findByCodeIgnoreCase(code)
				.orElseThrow(() -> new IllegalArgumentException("Unknown branch: " + code));
		if (!branch.isActive()) {
			throw new IllegalArgumentException("Branch is inactive: " + branch.getCode());
		}
		return branch.getCode();
	}

	/**
	 * Read paths: locked branch when restricted, otherwise the requested
	 * branch code or null meaning "all branches".
	 */
	@Transactional(readOnly = true)
	public String scopeFilter(Authentication authentication, String requested) {
		String locked = lockedBranchCode(authentication);
		if (locked != null) {
			return locked;
		}
		if (requested == null || requested.isBlank()) {
			return null;
		}
		String code = requested.trim();
		return branchRepository.findByCodeIgnoreCase(code).map(BranchEntity::getCode).orElse(code);
	}

	/**
	 * Record-level guard: throws when the caller is locked to a branch and the
	 * record belongs to a different one. A null record branch counts as main.
	 */
	@Transactional(readOnly = true)
	public void assertBranch(Authentication authentication, String recordBranch) {
		String locked = lockedBranchCode(authentication);
		if (locked == null) {
			return;
		}
		String actual = (recordBranch == null || recordBranch.isBlank()) ? mainCode() : recordBranch;
		if (!locked.equalsIgnoreCase(actual)) {
			throw new IllegalArgumentException("This record belongs to another branch");
		}
	}

	public String mainCode() {
		return branchRepository.findFirstByMainTrue().map(BranchEntity::getCode).orElse("MAIN");
	}
}
