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
	 * Branch codes the caller may VIEW in read-only mode, besides their locked
	 * branch. Empty for unrestricted users (they can already see everything).
	 */
	@Transactional(readOnly = true)
	public java.util.Set<String> viewBranchCodes(Authentication authentication) {
		if (authentication == null || authentication.getPrincipal() == null) {
			return java.util.Set.of();
		}
		UserEntity user = userRepository.findByEmailIgnoreCase(String.valueOf(authentication.getPrincipal())).orElse(null);
		if (user == null) {
			return java.util.Set.of();
		}
		return user.getViewBranches() == null ? java.util.Set.of() : user.getViewBranches();
	}

	/**
	 * Write paths: locked callers always write to their own branch — requesting
	 * another branch (even a view-only one) is rejected rather than silently
	 * misfiled under the home branch.
	 */
	@Transactional(readOnly = true)
	public String resolveLocation(Authentication authentication, String requested) {
		String locked = lockedBranchCode(authentication);
		if (locked != null) {
			if (requested != null && !requested.isBlank() && !locked.equalsIgnoreCase(requested.trim())) {
				throw new IllegalArgumentException("Branch " + requested.trim() + " is view-only for this account");
			}
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
			if (requested != null && !requested.isBlank()) {
				String code = requested.trim();
				if (viewBranchCodes(authentication).stream().anyMatch(v -> v.equalsIgnoreCase(code))) {
					return branchRepository.findByCodeIgnoreCase(code).map(BranchEntity::getCode).orElse(code);
				}
			}
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
