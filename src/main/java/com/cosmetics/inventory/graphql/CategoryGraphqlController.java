package com.cosmetics.inventory.graphql;

import com.cosmetics.inventory.category.CategoryEntity;
import com.cosmetics.inventory.category.CategoryRepository;
import com.cosmetics.inventory.category.CategoryService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
public class CategoryGraphqlController {
	private final CategoryService categoryService;
	private final CategoryRepository categoryRepository;

	public CategoryGraphqlController(CategoryService categoryService, CategoryRepository categoryRepository) {
		this.categoryService = categoryService;
		this.categoryRepository = categoryRepository;
	}

	@QueryMapping
	@PreAuthorize("isAuthenticated()")
	public List<CategoryEntity> categories(@Argument CategoryFilter filter) {
		String query = filter != null ? filter.query() : null;
		Boolean active = filter != null ? filter.active() : null;
		return categoryService.findCategories(query, active);
	}

	@QueryMapping
	@PreAuthorize("isAuthenticated()")
	public CategoryEntity category(@Argument long id) {
		return categoryRepository.findById(id).orElse(null);
	}

	@MutationMapping
	@PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
	public CategoryEntity createCategory(@Argument CreateCategoryInput input) {
		return categoryService.createCategory(new CategoryService.CreateCategoryCommand(
				input.name(),
				input.description()
		));
	}

	@MutationMapping
	@PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
	public CategoryEntity updateCategory(@Argument UpdateCategoryInput input) {
		return categoryService.updateCategory(new CategoryService.UpdateCategoryCommand(
				input.id(),
				input.name(),
				input.description(),
				input.active()
		));
	}

	@MutationMapping
	@PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
	public boolean deleteCategory(@Argument DeleteCategoryInput input) {
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
