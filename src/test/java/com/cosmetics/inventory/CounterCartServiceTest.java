package com.cosmetics.inventory;

import com.cosmetics.inventory.inventory.InventoryRepository;
import com.cosmetics.inventory.product.ProductEntity;
import com.cosmetics.inventory.product.ProductRepository;
import com.cosmetics.inventory.branch.BranchScope;
import com.cosmetics.inventory.sales.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CounterCartServiceTest {
    private final CounterCartRepository carts = mock(CounterCartRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final InventoryRepository inventory = mock(InventoryRepository.class);
    private final SalesService sales = mock(SalesService.class);
    private final SalesOrderRepository orders = mock(SalesOrderRepository.class);
    private final BranchScope branchScope = mock(BranchScope.class);
    private final CounterCartService service = new CounterCartService(carts, products, inventory, sales, orders, branchScope);
    private final UsernamePasswordAuthenticationToken user = new UsernamePasswordAuthenticationToken("cashier", null);
    private final String id = UUID.randomUUID().toString();
    private ProductEntity product;
    private CounterCartEntity persisted;

    @BeforeEach
    void setup() {
        product = new ProductEntity();
        ReflectionTestUtils.setField(product, "id", 1L);
        product.setSku("SKU-1");
        product.setName("Moisturizer");
        product.setSellingPrice(new BigDecimal("12.50"));
        when(products.findById(1L)).thenReturn(Optional.of(product));
        when(carts.lockById(id)).thenAnswer(call -> Optional.ofNullable(persisted));
        when(carts.saveAndFlush(any())).thenAnswer(call -> {
            persisted = call.getArgument(0);
            return persisted;
        });
    }

    private CounterCartService.SaveCartCommand command(int quantity, Long version) {
        return new CounterCartService.SaveCartCommand(id, version, "Walk-in", null,
                List.of(new CounterCartService.LineCommand(1L, quantity, "MAIN")));
    }

    @Test
    void holdingUsesConfiguredPricesWithoutDeductingStock() {
        var result = service.save(command(2, null), user);
        assertEquals(new BigDecimal("25.00"), result.total());
        assertEquals(new BigDecimal("12.50"), result.lines().getFirst().unitPrice());
        verifyNoInteractions(sales, inventory);
    }

    @Test
    void retryingTheInitialHoldDoesNotCreateAnotherCart() {
        service.save(command(2, null), user);
        service.save(command(2, null), user);
        verify(carts, times(1)).saveAndFlush(any());
    }

    @Test
    void rejectsInvalidQuantitiesAndUnpricedProducts() {
        assertThrows(IllegalArgumentException.class, () -> service.save(command(0, null), user));
        product.setSellingPrice(null);
        assertThrows(IllegalArgumentException.class, () -> service.save(command(1, null), user));
        verifyNoInteractions(sales);
    }

    @Test
    void rejectsAStaleCashierRevision() {
        service.save(command(2, null), user);
        ReflectionTestUtils.setField(persisted, "version", 2L);
        assertThrows(IllegalArgumentException.class, () -> service.save(command(3, 1L), user));
        assertThrows(IllegalArgumentException.class, () -> service.checkout(id, 1, new BigDecimal("25.00"), user));
        verifyNoInteractions(sales);
    }

    @Test
    void checkoutIsIdempotentAfterSuccessfulCompletion() {
        service.save(command(2, null), user);
        var order = new SalesOrderEntity();
        ReflectionTestUtils.setField(order, "id", 10L);
        when(sales.createSale(any(), eq(user))).thenReturn(order);
        when(orders.findById(10L)).thenReturn(Optional.of(order));
        assertEquals(10L, service.checkout(id, 0, new BigDecimal("25.00"), user).id());
        assertEquals(10L, service.checkout(id, 0, new BigDecimal("25.00"), user).id());
        verify(sales, times(1)).createSale(any(), eq(user));
        assertEquals(CounterCartEntity.Status.COMPLETED, persisted.getStatus());
    }

    @Test
    void changedPricesAndTotalsRequireAnotherReview() {
        service.save(command(2, null), user);
        assertThrows(IllegalArgumentException.class, () -> service.checkout(id, 0, BigDecimal.ONE, user));
        product.setSellingPrice(new BigDecimal("15.00"));
        assertThrows(IllegalArgumentException.class, () -> service.checkout(id, 0, new BigDecimal("25.00"), user));
        verifyNoInteractions(sales);
    }

    @Test
    void stockFailureLeavesTheCartPending() {
        service.save(command(2, null), user);
        when(sales.createSale(any(), eq(user))).thenThrow(new IllegalArgumentException("Insufficient stock"));
        assertThrows(IllegalArgumentException.class, () -> service.checkout(id, 0, new BigDecimal("25.00"), user));
        assertEquals(CounterCartEntity.Status.PENDING, persisted.getStatus());
        assertNull(persisted.getCompletedSaleId());
    }

    @Test
    void cancelledCartCannotBeCharged() {
        service.save(command(2, null), user);
        assertTrue(service.cancel(id, 0, user));
        assertThrows(IllegalArgumentException.class, () -> service.checkout(id, 0, new BigDecimal("25.00"), user));
        verifyNoInteractions(sales);
    }
}
