package com.cosmetics.inventory.graphql;

import com.cosmetics.inventory.category.CategoryEntity;
import com.cosmetics.inventory.category.CategoryRepository;
import com.cosmetics.inventory.category.CategoryService;
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
public class CategoryGraphqlController {
	private final CategoryService categoryService;
	private final CategoryRepository categoryRepository;
	private final PermissionGuard permissionGuard;

	public CategoryGraphqlController(CategoryService categoryService, CategoryRepository categoryRepository, PermissionGuard permissionGuard) {
		this.categoryService = categoryService;
		this.categoryRepository = categoryRepository;
		this.permissionGuard = permissionGuard;
	}

	@QueryMapping
	@PreAuthorize("isAuthenticated()")
	public List<CategoryEntity> categories(@Argument CategoryFilter filter, Authentication authentication) {
		permissionGuard.require(authentication, PermissionModule.CATEGORIES, PermissionsService.PermissionAction.VIEW);
		String query = filter != null ? filter.query() : null;
		Boolean active = filter != null ? filter.active() : null;
		return categoryService.findCategories(query, active);
	}

	@QueryMapping
	@PreAuthorize("isAuthenticated()")
	public CategoryEntity category(@Argument long id, Authentication authentication) {
		permissionGuard.require(authentication, PermissionModule.CATEGORIES, PermissionsService.PermissionAction.VIEW);
		return categoryRepository.findById(id).orElse(null);
	}

	@MutationMapping
	@PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
	public CategoryEntity createCategory(@Argument CreateCategoryInput input, Authentication authentication) {
		permissionGuard.require(authentication, PermissionModule.CATEGORIES, PermissionsService.PermissionAction.CREATE);
		return categoryService.createCategory(new CategoryService.CreateCategoryCommand(
				input.name(),
				input.description()
		));
	}

	@MutationMapping
	@PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
	public CategoryEntity updateCategory(@Argument UpdateCategoryInput input, Authentication authentication) {
		permissionGuard.require(authentication, PermissionModule.CATEGORIES, PermissionsService.PermissionAction.EDIT);
		return categoryService.updateCategory(new CategoryService.UpdateCategoryCommand(
				input.id(),
				input.name(),
				input.description(),
				input.active()
		));
	}

	@MutationMapping
	@PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
	public boolean deleteCategory(@Argument DeleteCategoryInput input, Authentication authentication) {
		permissionGuard.require(authentication, PermissionModule.CATEGORIES, PermissionsService.PermissionAction.DELETE);
		return categoryService.deleteCategory(input.id());
	}

	public record CategoryFilter(String query, Boolean active) {
	}

	public record CreateCategoryInput(String name, String description) {
	}

	public record UpdateCategoryInput(long id, String name, String description, Boolean active) {
	}

	public record DeleteCategoryInput(long id) {
	}
}
