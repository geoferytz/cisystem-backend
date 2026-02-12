package com.cosmetics.inventory.graphql;

import com.cosmetics.inventory.expense.ExpenseCategoryEntity;
import com.cosmetics.inventory.expense.ExpenseCategoryRepository;
import com.cosmetics.inventory.expense.ExpenseCategoryService;
import com.cosmetics.inventory.expense.ExpenseEntity;
import com.cosmetics.inventory.expense.ExpenseService;
import com.cosmetics.inventory.expense.ExpensePaymentMethod;
import com.cosmetics.inventory.user.PermissionGuard;
import com.cosmetics.inventory.user.PermissionModule;
import com.cosmetics.inventory.user.PermissionsService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.time.LocalDate;
import java.util.List;

@Controller
public class ExpenseGraphqlController {
	private final ExpenseCategoryService expenseCategoryService;
	private final ExpenseCategoryRepository expenseCategoryRepository;
	private final ExpenseService expenseService;
	private final PermissionGuard permissionGuard;

	public ExpenseGraphqlController(ExpenseCategoryService expenseCategoryService, ExpenseCategoryRepository expenseCategoryRepository, ExpenseService expenseService, PermissionGuard permissionGuard) {
		this.expenseCategoryService = expenseCategoryService;
		this.expenseCategoryRepository = expenseCategoryRepository;
		this.expenseService = expenseService;
		this.permissionGuard = permissionGuard;
	}

	@QueryMapping
	@PreAuthorize("isAuthenticated()")
	public List<ExpenseCategoryEntity> expenseCategories(@Argument ExpenseCategoryFilter filter, Authentication authentication) {
		permissionGuard.require(authentication, PermissionModule.EXPENSE_CATEGORIES, PermissionsService.PermissionAction.VIEW);
		String query = filter != null ? filter.query() : null;
		Boolean active = filter != null ? filter.active() : null;
		return expenseCategoryService.findCategories(query, active);
	}

	@MutationMapping
	@PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
	public ExpenseCategoryEntity createExpenseCategory(@Argument CreateExpenseCategoryInput input, Authentication authentication) {
		permissionGuard.require(authentication, PermissionModule.EXPENSE_CATEGORIES, PermissionsService.PermissionAction.CREATE);
		return expenseCategoryService.create(new ExpenseCategoryService.CreateExpenseCategoryCommand(
				input.name(),
				input.description()
		));
	}

	@MutationMapping
	@PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
	public ExpenseCategoryEntity updateExpenseCategory(@Argument UpdateExpenseCategoryInput input, Authentication authentication) {
		permissionGuard.require(authentication, PermissionModule.EXPENSE_CATEGORIES, PermissionsService.PermissionAction.EDIT);
		return expenseCategoryService.update(new ExpenseCategoryService.UpdateExpenseCategoryCommand(
				input.id(),
				input.name(),
				input.description(),
				input.active()
		));
	}

	@MutationMapping
	@PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
	public boolean deleteExpenseCategory(@Argument DeleteExpenseCategoryInput input, Authentication authentication) {
		permissionGuard.require(authentication, PermissionModule.EXPENSE_CATEGORIES, PermissionsService.PermissionAction.DELETE);
		return expenseCategoryService.delete(input.id());
	}

	@QueryMapping
	@PreAuthorize("isAuthenticated()")
	public List<ExpenseEntity> expenses(@Argument ExpenseFilter filter, Authentication authentication) {
		permissionGuard.require(authentication, PermissionModule.EXPENSES, PermissionsService.PermissionAction.VIEW);
		LocalDate from = null;
		LocalDate to = null;
		if (filter != null && filter.from() != null && !filter.from().isBlank()) {
			from = LocalDate.parse(filter.from().trim());
		}
		if (filter != null && filter.to() != null && !filter.to().isBlank()) {
			to = LocalDate.parse(filter.to().trim());
		}
		return expenseService.findExpenses(from, to);
	}

	@MutationMapping
	@PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
	public ExpenseEntity createExpense(@Argument CreateExpenseInput input, Authentication authentication) {
		permissionGuard.require(authentication, PermissionModule.EXPENSES, PermissionsService.PermissionAction.CREATE);
		String createdBy = authentication != null ? String.valueOf(authentication.getPrincipal()) : null;
		LocalDate d = input.date() != null && !input.date().isBlank() ? LocalDate.parse(input.date().trim()) : null;
		ExpensePaymentMethod pm = input.paymentMethod() != null ? ExpensePaymentMethod.valueOf(input.paymentMethod().trim().toUpperCase()) : null;
		return expenseService.create(new ExpenseService.CreateExpenseCommand(
				d,
				input.categoryId(),
				input.description(),
				input.amount(),
				pm
		), createdBy);
	}

	@MutationMapping
	@PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
	public ExpenseEntity updateExpense(@Argument UpdateExpenseInput input, Authentication authentication) {
		permissionGuard.require(authentication, PermissionModule.EXPENSES, PermissionsService.PermissionAction.EDIT);
		LocalDate d = input.date() != null && !input.date().isBlank() ? LocalDate.parse(input.date().trim()) : null;
		ExpensePaymentMethod pm = input.paymentMethod() != null ? ExpensePaymentMethod.valueOf(input.paymentMethod().trim().toUpperCase()) : null;
		return expenseService.update(new ExpenseService.UpdateExpenseCommand(
				input.id(),
				d,
				input.categoryId(),
				input.description(),
				input.amount(),
				pm
		));
	}

	@MutationMapping
	@PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
	public boolean deleteExpense(@Argument DeleteExpenseInput input, Authentication authentication) {
		permissionGuard.require(authentication, PermissionModule.EXPENSES, PermissionsService.PermissionAction.DELETE);
		return expenseService.delete(input.id());
	}

	@SchemaMapping(typeName = "Expense", field = "date")
	@PreAuthorize("isAuthenticated()")
	public String date(ExpenseEntity e) {
		return e.getExpenseDate().toString();
	}

	@SchemaMapping(typeName = "Expense", field = "amount")
	@PreAuthorize("isAuthenticated()")
	public double amount(ExpenseEntity e) {
		return e.getAmount().doubleValue();
	}

	@SchemaMapping(typeName = "Expense", field = "createdAt")
	@PreAuthorize("isAuthenticated()")
	public String createdAt(ExpenseEntity e) {
		return e.getCreatedAt().toString();
	}

	@SchemaMapping(typeName = "Expense", field = "paymentMethod")
	@PreAuthorize("isAuthenticated()")
	public String paymentMethod(ExpenseEntity e) {
		return e.getPaymentMethod().name();
	}

	@SchemaMapping(typeName = "Expense", field = "category")
	@PreAuthorize("isAuthenticated()")
	public ExpenseCategoryEntity category(ExpenseEntity e) {
		Long id = e.getId();
		if (id == null) return null;
		return expenseCategoryRepository.findById(e.getCategory().getId()).orElse(null);
	}

	public record ExpenseCategoryFilter(String query, Boolean active) {
	}

	public record CreateExpenseCategoryInput(String name, String description) {
	}

	public record UpdateExpenseCategoryInput(long id, String name, String description, Boolean active) {
	}

	public record DeleteExpenseCategoryInput(long id) {
	}

	public record ExpenseFilter(String from, String to) {
	}

	public record CreateExpenseInput(String date, Long categoryId, String description, Double amount, String paymentMethod) {
	}

	public record UpdateExpenseInput(long id, String date, Long categoryId, String description, Double amount, String paymentMethod) {
	}

	public record DeleteExpenseInput(long id) {
	}
}
