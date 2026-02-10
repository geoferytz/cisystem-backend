package com.cosmetics.inventory.category;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryService {
	private final CategoryRepository categoryRepository;

	public CategoryService(CategoryRepository categoryRepository) {
		this.categoryRepository = categoryRepository;
	}

	@Transactional(readOnly = true)
	public List<CategoryEntity> findCategories(String query, Boolean active) {
		if (query != null && !query.isBlank()) {
			String q = query.trim();
			return categoryRepository.findTop200ByNameContainingIgnoreCaseOrderByNameAsc(q);
		}
		if (active != null) {
			return categoryRepository.findTop200ByActiveOrderByNameAsc(active);
		}
		return categoryRepository.findAll();
	}

	@Transactional
	public CategoryEntity createCategory(CreateCategoryCommand cmd) {
		if (cmd.name() == null || cmd.name().isBlank()) {
			throw new IllegalArgumentException("Name is required");
		}

		categoryRepository.findByNameIgnoreCase(cmd.name()).ifPresent(existing -> {
			throw new IllegalArgumentException("Category name already exists");
		});

		CategoryEntity c = new CategoryEntity();
		c.setName(cmd.name().trim());
		c.setDescription(cmd.description());
		return categoryRepository.save(c);
	}

	@Transactional
	public CategoryEntity updateCategory(UpdateCategoryCommand cmd) {
		CategoryEntity c = categoryRepository.findById(cmd.id()).orElseThrow();

		if (cmd.name() != null && !cmd.name().isBlank() && !cmd.name().equalsIgnoreCase(c.getName())) {
			categoryRepository.findByNameIgnoreCase(cmd.name()).ifPresent(existing -> {
				throw new IllegalArgumentException("Category name already exists");
			});
			c.setName(cmd.name().trim());
		}

		if (cmd.description() != null) {
			c.setDescription(cmd.description());
		}

		if (cmd.active() != null) {
			c.setActive(cmd.active());
		}

		return categoryRepository.save(c);
	}

	@Transactional
	public boolean deleteCategory(long id) {
		if (!categoryRepository.existsById(id)) {
			return false;
		}
		categoryRepository.deleteById(id);
		return true;
	}

	public record CreateCategoryCommand(String name, String description) {
	}

	public record UpdateCategoryCommand(long id, String name, String description, Boolean active) {
	}
}
