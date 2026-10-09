package com.cosmetics.inventory;

import com.cosmetics.inventory.branch.BranchScope;
import com.cosmetics.inventory.inventory.InventoryRepository;
import com.cosmetics.inventory.product.ProductEntity;
import com.cosmetics.inventory.product.ProductRepository;
import com.cosmetics.inventory.purchasing.*;
import com.cosmetics.inventory.user.PermissionGuard;
import com.cosmetics.inventory.user.PermissionModule;
import com.cosmetics.inventory.user.PermissionsService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RepurchaseServiceTest {
    private final RepurchaseOrderRepository orders = mock(RepurchaseOrderRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final InventoryRepository inventory = mock(InventoryRepository.class);
    private final PurchasingService purchasing = mock(PurchasingService.class);
    private final PermissionGuard permissions = mock(PermissionGuard.class);
    private final EntityManager em = mock(EntityManager.class);
    private final BranchScope branchScope = mock(BranchScope.class);
    private final RepurchaseService service = new RepurchaseService(orders, products, inventory, purchasing, permissions, em, branchScope);
    private final UsernamePasswordAuthenticationToken user = new UsernamePasswordAuthenticationToken("storekeeper", null);
    private final String id = UUID.randomUUID().toString();
    private ProductEntity product;
    private RepurchaseOrderEntity persisted;

    @BeforeEach
    void setup() {
        product = new ProductEntity();
        ReflectionTestUtils.setField(product, "id", 1L);
        product.setSku("SKU-1");
        product.setName("Cream");
        product.setBuyingPrice(new BigDecimal("5"));
        product.setSellingPrice(new BigDecimal("10"));
        when(products.findById(1L)).thenReturn(Optional.of(product));
        when(em.find(ProductEntity.class, 1L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(product);
        when(orders.lockById(id)).thenAnswer(call -> Optional.ofNullable(persisted));
        when(orders.saveAndFlush(any())).thenAnswer(call -> { persisted = call.getArgument(0); return persisted; });
    }

    private RepurchaseService.SubmitCommand command(int quantity, String buying) {
        return new RepurchaseService.SubmitCommand(id, "Supplier", "INV-1", false, null,
                List.of(new RepurchaseService.LineInput(1L, quantity, new BigDecimal(buying), new BigDecimal("12"), null, null, null)));
    }

    private List<RepurchaseService.ReceiptLine> receipt() {
        return List.of(new RepurchaseService.ReceiptLine(1L, "RP-1", LocalDate.now().plusYears(1).toString()));
    }

    @Test
    void submitKeepsInventoryAndPricesUnchangedAndRetriesReuseOrder() {
        var result = service.submit(command(5, "6"), user);
        assertNull(result.receivedPurchaseId());
        assertEquals(5, result.lines().getFirst().quantity());
        service.submit(command(5, "6"), user);
        verify(orders, times(1)).saveAndFlush(any());
        verifyNoInteractions(inventory, purchasing);
        assertEquals(new BigDecimal("5"), product.getBuyingPrice());
    }

    @Test
    void receiptCreatesOnePurchaseAndAppliesPricesOnlyOnce() {
        service.submit(command(5, "6"), user);
        var purchase = new PurchaseOrderEntity();
        ReflectionTestUtils.setField(purchase, "id", 9L);
        when(purchasing.receivePurchase(any(), eq(user))).thenReturn(purchase);
        assertEquals(9L, service.receive(id, receipt(), user).receivedPurchaseId());
        assertEquals(9L, service.receive(id, receipt(), user).receivedPurchaseId());
        verify(purchasing, times(1)).receivePurchase(argThat(c -> c.lines().getFirst().quantityReceived() == 5), eq(user));
        assertEquals(new BigDecimal("6"), product.getBuyingPrice());
        assertEquals(new BigDecimal("12"), product.getSellingPrice());
    }

    @Test
    void rejectsInvalidPricesQuantitiesAndInactiveProducts() {
        assertThrows(IllegalArgumentException.class, () -> service.submit(command(0, "6"), user));
        assertThrows(IllegalArgumentException.class, () -> service.submit(command(5, "-1"), user));
        product.setActive(false);
        assertThrows(IllegalArgumentException.class, () -> service.submit(command(5, "6"), user));
        verifyNoInteractions(purchasing);
    }

    @Test
    void rejectsMissingOrExpiredBatchDetails() {
        service.submit(command(5, "6"), user);
        assertThrows(IllegalArgumentException.class, () -> service.receive(id, List.of(), user));
        assertThrows(IllegalArgumentException.class, () -> service.receive(id,
                List.of(new RepurchaseService.ReceiptLine(1L, "B1", LocalDate.now().minusDays(1).toString())), user));
        verifyNoInteractions(purchasing);
    }

    @Test
    void changedProductPricesRequireReviewInsteadOfOverwriting() {
        service.submit(command(5, "6"), user);
        product.setSellingPrice(new BigDecimal("20"));
        assertThrows(IllegalArgumentException.class, () -> service.receive(id, receipt(), user));
        verifyNoInteractions(purchasing);
    }

    @Test
    void activationOrderAllowsInactiveProductAndActivatesItOnReceipt() {
        product.setActive(false);
        product.setBuyingPrice(null);
        product.setSellingPrice(null);
        var cmd = new RepurchaseService.SubmitCommand(id, null, null, true, null,
                List.of(new RepurchaseService.LineInput(1L, 5, BigDecimal.ZERO, new BigDecimal("12"),
                        "B-9", LocalDate.now().plusYears(1).toString(), "MAIN")));
        var order = service.submit(cmd, user);
        assertTrue(order.activation());
        assertEquals("B-9", order.lines().getFirst().batchNumber());
        var purchase = new PurchaseOrderEntity();
        ReflectionTestUtils.setField(purchase, "id", 9L);
        when(purchasing.receivePurchase(any(), eq(user))).thenReturn(purchase);
        assertEquals(9L, service.receive(id, receipt(), user).receivedPurchaseId());
        assertTrue(product.isActive());
        assertEquals(new BigDecimal("12"), product.getSellingPrice());
        verify(permissions, never()).require(user, PermissionModule.PRODUCTS, PermissionsService.PermissionAction.EDIT);
    }

    @Test
    void priceChangesRequireProductEditPermission() {
        doThrow(new org.springframework.security.access.AccessDeniedException("Forbidden"))
                .when(permissions).require(user, PermissionModule.PRODUCTS, PermissionsService.PermissionAction.EDIT);
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.submit(command(5, "6"), user));
        verify(orders, never()).saveAndFlush(any());
    }
}
