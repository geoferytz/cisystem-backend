package com.cosmetics.inventory.expense;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ExpenseCategoryService {
	private final ExpenseCategoryRepository repo;

	public ExpenseCategoryService(ExpenseCategoryRepository repo) {
		this.repo = repo;
	}

	@Transactional(readOnly = true)
	public List<ExpenseCategoryEntity> findCategories(String query, Boolean active) {
		if (query != null && !query.isBlank()) {
			return repo.findTop200ByNameContainingIgnoreCaseOrderByNameAsc(query.trim());
		}
		if (active != null) {
			return repo.findTop200ByActiveOrderByNameAsc(active);
		}
		return repo.findAll();
	}

	@Transactional
	public ExpenseCategoryEntity create(CreateExpenseCategoryCommand cmd) {
		if (cmd.name() == null || cmd.name().isBlank()) {
			throw new IllegalArgumentException("Name is required");
		}
		repo.findByNameIgnoreCase(cmd.name()).ifPresent(existing -> {
			throw new IllegalArgumentException("Category name already exists");
		});

		ExpenseCategoryEntity c = new ExpenseCategoryEntity();
		c.setName(cmd.name().trim());
		c.setDescription(cmd.description());
		return repo.save(c);
	}

	@Transactional
	public ExpenseCategoryEntity update(UpdateExpenseCategoryCommand cmd) {
		ExpenseCategoryEntity c = repo.findById(cmd.id()).orElseThrow();

		if (cmd.name() != null && !cmd.name().isBlank() && !cmd.name().equalsIgnoreCase(c.getName())) {
			repo.findByNameIgnoreCase(cmd.name()).ifPresent(existing -> {
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

		return repo.save(c);
	}

	@Transactional
	public boolean delete(long id) {
		if (!repo.existsById(id)) {
			return false;
		}
		repo.deleteById(id);
		return true;
	}

	public record CreateExpenseCategoryCommand(String name, String description) {
	}

	public record UpdateExpenseCategoryCommand(long id, String name, String description, Boolean active) {
	}
}
