package com.cosmetics.inventory.graphql;

import com.cosmetics.inventory.branch.BranchEntity;
import com.cosmetics.inventory.branch.BranchRepository;
import com.cosmetics.inventory.branch.BranchService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import com.cosmetics.inventory.user.PermissionGuard;
import com.cosmetics.inventory.user.PermissionModule;
import com.cosmetics.inventory.user.PermissionsService;

import java.util.List;

@Controller
public class BranchGraphqlController {
	private final BranchService branchService;
	private final BranchRepository branchRepository;
	private final PermissionGuard permissionGuard;

	public BranchGraphqlController(BranchService branchService, BranchRepository branchRepository, PermissionGuard permissionGuard) {
		this.branchService = branchService;
		this.branchRepository = branchRepository;
		this.permissionGuard = permissionGuard;
	}

	@QueryMapping
	@PreAuthorize("isAuthenticated()")
	public List<BranchEntity> branchOptions(Authentication authentication) {
		return branchRepository.findTop200ByActiveOrderByNameAsc(true);
	}

	@QueryMapping
	@PreAuthorize("isAuthenticated()")
	public List<BranchEntity> branches(@Argument BranchFilter filter, Authentication authentication) {
		permissionGuard.require(authentication, PermissionModule.BRANCHES, PermissionsService.PermissionAction.VIEW);
		String query = filter != null ? filter.query() : null;
		Boolean active = filter != null ? filter.active() : null;
		return branchService.findBranches(query, active);
	}

	@QueryMapping
	@PreAuthorize("isAuthenticated()")
	public BranchEntity branch(@Argument long id, Authentication authentication) {
		permissionGuard.require(authentication, PermissionModule.BRANCHES, PermissionsService.PermissionAction.VIEW);
		return branchRepository.findById(id).orElse(null);
	}

	@MutationMapping
	@PreAuthorize("hasRole('ADMIN')")
	public BranchEntity createBranch(@Argument CreateBranchInput input, Authentication authentication) {
		permissionGuard.require(authentication, PermissionModule.BRANCHES, PermissionsService.PermissionAction.CREATE);
		return branchService.createBranch(new BranchService.CreateBranchCommand(
				input.code(),
				input.name(),
				input.address(),
				input.phone()
		));
	}

	@MutationMapping
	@PreAuthorize("hasRole('ADMIN')")
	public BranchEntity updateBranch(@Argument UpdateBranchInput input, Authentication authentication) {
		permissionGuard.require(authentication, PermissionModule.BRANCHES, PermissionsService.PermissionAction.EDIT);
		return branchService.updateBranch(new BranchService.UpdateBranchCommand(
				input.id(),
				input.code(),
				input.name(),
				input.address(),
				input.phone(),
				input.active()
		));
	}

	@MutationMapping
	@PreAuthorize("hasRole('ADMIN')")
	public boolean deleteBranch(@Argument DeleteBranchInput input, Authentication authentication) {
		permissionGuard.require(authentication, PermissionModule.BRANCHES, PermissionsService.PermissionAction.DELETE);
		return branchService.deleteBranch(input.id());
	}

	public record BranchFilter(String query, Boolean active) {
	}

	public record CreateBranchInput(String code, String name, String address, String phone) {
	}

	public record UpdateBranchInput(long id, String code, String name, String address, String phone, Boolean active) {
	}

	public record DeleteBranchInput(long id) {
	}
}
