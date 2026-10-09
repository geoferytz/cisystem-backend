package com.cosmetics.inventory.graphql;

import com.cosmetics.inventory.branch.BranchScope;
import com.cosmetics.inventory.expense.ExpenseCategoryEntity;
import com.cosmetics.inventory.expense.ExpenseCategoryRepository;
import com.cosmetics.inventory.sales.CounterCartService;
import com.cosmetics.inventory.user.PermissionGuard;
import com.cosmetics.inventory.user.PermissionModule;
import com.cosmetics.inventory.user.PermissionsService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import java.math.BigDecimal;
import java.util.List;

@Controller
@PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
public class CounterGraphqlController {
    private final CounterCartService service;
    private final PermissionGuard permissions;
    private final ExpenseCategoryRepository expenseCategories;
    private final BranchScope branchScope;

    public CounterGraphqlController(CounterCartService service, PermissionGuard permissions, ExpenseCategoryRepository expenseCategories, BranchScope branchScope) {
        this.service = service;
        this.permissions = permissions;
        this.expenseCategories = expenseCategories;
        this.branchScope = branchScope;
    }

    @QueryMapping
    public List<CounterCartService.ProductDto> counterProducts(@Argument String location, Authentication authentication) {
        permissions.require(authentication, PermissionModule.SALES, PermissionsService.PermissionAction.CREATE);
        return service.products(branchScope.resolveLocation(authentication, location));
    }

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public List<CounterCartService.CartDto> pendingCounterCarts(Authentication authentication) {
        permissions.require(authentication, PermissionModule.SALES, PermissionsService.PermissionAction.VIEW);
        String locked = branchScope.lockedBranchCode(authentication);
        var carts = service.pending();
        if (locked == null) {
            return carts;
        }
        return carts.stream()
                .filter(c -> c.lines() == null || c.lines().isEmpty()
                        || c.lines().stream().allMatch(l -> l.location() != null && l.location().equalsIgnoreCase(locked)))
                .toList();
    }

    @QueryMapping
    public List<ExpenseCategoryEntity> counterExpenseCategories(Authentication authentication) {
        permissions.require(authentication, PermissionModule.EXPENSES, PermissionsService.PermissionAction.CREATE);
        return expenseCategories.findTop200ByActiveOrderByNameAsc(true);
    }

    @MutationMapping
    public CounterCartService.CartDto saveCounterCart(@Argument CounterCartService.SaveCartCommand input, Authentication authentication) {
        permissions.require(authentication, PermissionModule.SALES, PermissionsService.PermissionAction.CREATE);
        var lines = input.lines() == null ? null : input.lines().stream()
                .map(l -> new CounterCartService.LineCommand(
                        l.productId(),
                        l.quantity(),
                        branchScope.resolveLocation(authentication, l.location())))
                .toList();
        return service.save(new CounterCartService.SaveCartCommand(
                input.id(), input.version(), input.customer(), input.referenceNumber(), lines), authentication);
    }

    @MutationMapping
    public SalesOrderDto checkoutCounterCart(@Argument CheckoutInput input, Authentication authentication) {
        permissions.require(authentication, PermissionModule.SALES, PermissionsService.PermissionAction.CREATE);
        if (input.expectedTotal() == null || !Double.isFinite(input.expectedTotal())) throw new IllegalArgumentException("A valid total is required");
        return service.checkout(input.id(), input.version(), BigDecimal.valueOf(input.expectedTotal()), authentication);
    }

    @MutationMapping
    public boolean cancelCounterCart(@Argument CartVersionInput input, Authentication authentication) {
        permissions.require(authentication, PermissionModule.SALES, PermissionsService.PermissionAction.DELETE);
        return service.cancel(input.id(), input.version(), authentication);
    }

    public record CheckoutInput(String id, long version, Double expectedTotal) {}
    public record CartVersionInput(String id, long version) {}
}
