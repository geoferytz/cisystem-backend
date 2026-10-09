package com.cosmetics.inventory.purchasing;

import com.cosmetics.inventory.inventory.InventoryRepository;
import com.cosmetics.inventory.product.ProductEntity;
import com.cosmetics.inventory.product.ProductRepository;
import com.cosmetics.inventory.user.PermissionGuard;
import com.cosmetics.inventory.user.PermissionModule;
import com.cosmetics.inventory.user.PermissionsService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import com.cosmetics.inventory.branch.BranchScope;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
public class RepurchaseService {
    private final RepurchaseOrderRepository orders;
    private final ProductRepository products;
    private final InventoryRepository inventory;
    private final PurchasingService purchasing;
    private final PermissionGuard permissions;
    private final EntityManager entityManager;
    private final BranchScope branchScope;

    public RepurchaseService(RepurchaseOrderRepository orders, ProductRepository products, InventoryRepository inventory,
                             PurchasingService purchasing, PermissionGuard permissions, EntityManager entityManager,
                             BranchScope branchScope) {
        this.orders = orders;
        this.products = products;
        this.inventory = inventory;
        this.purchasing = purchasing;
        this.permissions = permissions;
        this.entityManager = entityManager;
        this.branchScope = branchScope;
    }

    @Transactional(readOnly = true)
    public List<CatalogProduct> catalog(String location) {
        Map<Long, Integer> quantities = new HashMap<>();
        for (var item : inventory.findAll()) {
            if (location.equalsIgnoreCase(item.getLocation()) && !item.getBatch().getExpiryDate().isBefore(LocalDate.now())) {
                quantities.merge(item.getBatch().getProduct().getId(), item.getQtyOnHand(), Integer::sum);
            }
        }
        return products.findAll(Sort.by("name")).stream().filter(ProductEntity::isActive)
                .map(p -> new CatalogProduct(p.getId(), p.getSku(), p.getName(),
                p.getBarcode(), p.getBrand(), p.getCategory(), p.getUnitOfMeasure(), p.isActive(),
                p.getBuyingPrice(), p.getSellingPrice(), quantities.getOrDefault(p.getId(), 0))).toList();
    }

    @Transactional(readOnly = true)
    public List<Order> pending(String branch) {
        return orders.findByReceivedPurchaseIdIsNullOrderByCreatedAtDesc().stream()
                .filter(o -> branch == null || branch.equalsIgnoreCase(o.branch))
                .map(this::dto).toList();
    }

    @Transactional
    public Order submit(SubmitCommand cmd, Authentication user) {
        UUID.fromString(cmd.id());
        var existing = orders.lockById(cmd.id());
        if (existing.isPresent()) {
            var saved = existing.get();
            boolean matches = Objects.equals(saved.supplier, text(cmd.supplier(), 200))
                    && Objects.equals(saved.invoiceNumber, text(cmd.invoiceNumber(), 120))
                    && cmd.lines() != null && saved.lines.size() == cmd.lines().size();
            for (int i = 0; matches && i < saved.lines.size(); i++) {
                var old = saved.lines.get(i);
                var input = cmd.lines().get(i);
                matches = old.productId == input.productId() && old.quantity == input.quantity()
                        && same(old.buyingPrice, input.buyingPrice()) && same(old.sellingPrice, input.sellingPrice());
            }
            if (!matches) throw new IllegalArgumentException("This order was already submitted with different details. Refresh pending orders before starting a new order.");
            return dto(saved);
        }
        if (cmd.lines() == null || cmd.lines().isEmpty() || cmd.lines().size() > 200) {
            throw new IllegalArgumentException("Add between 1 and 200 products");
        }
        var order = new RepurchaseOrderEntity();
        order.id = cmd.id();
        order.supplier = text(cmd.supplier(), 200);
        order.invoiceNumber = text(cmd.invoiceNumber(), 120);
        order.createdBy = user.getName();
        order.activation = Boolean.valueOf(cmd.activation());
        order.branch = branchScope.resolveLocation(user, cmd.branch());
        Set<Long> ids = new HashSet<>();
        for (var input : cmd.lines()) {
            if (!ids.add(input.productId()) || input.quantity() < 1 || input.quantity() > 1000000) {
                throw new IllegalArgumentException("Products must be unique with a quantity between 1 and 1000000");
            }
            validatePrice(input.buyingPrice());
            validatePrice(input.sellingPrice());
            var product = products.findById(input.productId()).orElseThrow(() -> new IllegalArgumentException("Product not found"));
            if (!cmd.activation() && !product.isActive()) throw new IllegalArgumentException("Cannot repurchase inactive product: " + product.getName());
            if (!cmd.activation()) requirePricePermission(product, input.buyingPrice(), input.sellingPrice(), user);
            var line = new RepurchaseOrderEntity.Line();
            line.productId = product.getId();
            line.sku = product.getSku();
            line.productName = product.getName();
            line.quantity = input.quantity();
            line.buyingPrice = input.buyingPrice();
            line.sellingPrice = input.sellingPrice();
            line.originalBuyingPrice = product.getBuyingPrice();
            line.originalSellingPrice = product.getSellingPrice();
            line.batchNumber = text(input.batchNumber(), 80);
            line.expiryDate = input.expiryDate() == null || input.expiryDate().isBlank() ? null : LocalDate.parse(input.expiryDate().trim());
            if (line.expiryDate != null && line.expiryDate.isBefore(LocalDate.now())) {
                throw new IllegalArgumentException("Expiry date cannot be in the past for " + product.getName());
            }
            if (line.expiryDate != null && (line.batchNumber == null || line.batchNumber.isBlank())) {
                throw new IllegalArgumentException("Batch number is required when expiry date is provided for " + product.getName());
            }
            line.location = text(input.location(), 120);
            order.lines.add(line);
        }
        return dto(orders.saveAndFlush(order));
    }

    @Transactional
    public Order receive(String id, List<ReceiptLine> receipts, Authentication user) {
        var order = orders.lockById(id).orElseThrow(() -> new IllegalArgumentException("Repurchase order not found"));
        branchScope.assertBranch(user, order.branch);
        if (order.receivedPurchaseId != null) return dto(order);
        if (receipts == null || receipts.size() != order.lines.size()) {
            throw new IllegalArgumentException("Batch details are required for every product");
        }
        Map<Long, ReceiptLine> byProduct = new HashMap<>();
        for (var receipt : receipts) {
            if (byProduct.put(receipt.productId(), receipt) != null || receipt.batchNumber() == null
                    || receipt.batchNumber().isBlank() || receipt.batchNumber().trim().length() > 80
                    || receipt.expiryDate() == null || LocalDate.parse(receipt.expiryDate()).isBefore(LocalDate.now())) {
                throw new IllegalArgumentException("Provide unique products, batch codes and non-expired expiry dates");
            }
        }
        var purchaseLines = new ArrayList<PurchasingService.ReceivePurchaseLineCommand>();
        for (var line : order.lines.stream().sorted(Comparator.comparing(l -> l.productId)).toList()) {
            var receipt = byProduct.get(line.productId);
            if (receipt == null) throw new IllegalArgumentException("Missing batch details for " + line.productName);
            var product = entityManager.find(ProductEntity.class, line.productId, LockModeType.PESSIMISTIC_WRITE);
            if (product == null || (!Boolean.TRUE.equals(order.activation) && !product.isActive())) throw new IllegalArgumentException("Product is missing or inactive: " + line.productName);
            boolean buyingChanged = !same(line.buyingPrice, line.originalBuyingPrice);
            boolean sellingChanged = !same(line.sellingPrice, line.originalSellingPrice);
            if ((buyingChanged && !same(product.getBuyingPrice(), line.originalBuyingPrice))
                    || (sellingChanged && !same(product.getSellingPrice(), line.originalSellingPrice))) {
                throw new IllegalArgumentException("Product prices changed since this order was submitted: " + line.productName + ". Submit a new order with current prices.");
            }
            if (buyingChanged || sellingChanged) {
                if (!Boolean.TRUE.equals(order.activation)) permissions.require(user, PermissionModule.PRODUCTS, PermissionsService.PermissionAction.EDIT);
                if (buyingChanged) product.setBuyingPrice(line.buyingPrice);
                if (sellingChanged) product.setSellingPrice(line.sellingPrice);
            }
            if (Boolean.TRUE.equals(order.activation)) product.setActive(true);
            purchaseLines.add(new PurchasingService.ReceivePurchaseLineCommand(line.productId, receipt.batchNumber(),
                    receipt.expiryDate(), line.buyingPrice.doubleValue(), line.quantity));
        }
        var purchase = purchasing.receivePurchase(new PurchasingService.ReceivePurchaseCommand(order.supplier, order.invoiceNumber, order.branch, purchaseLines), user);
        order.receivedPurchaseId = purchase.getId();
        return dto(order);
    }

    private void requirePricePermission(ProductEntity product, BigDecimal buying, BigDecimal selling, Authentication user) {
        if (!same(buying, product.getBuyingPrice()) || !same(selling, product.getSellingPrice())) {
            permissions.require(user, PermissionModule.PRODUCTS, PermissionsService.PermissionAction.EDIT);
        }
    }

    private boolean same(BigDecimal a, BigDecimal b) { return a == null ? b == null : b != null && a.compareTo(b) == 0; }

    private void validatePrice(BigDecimal price) {
        if (price == null || price.signum() < 0 || price.compareTo(new BigDecimal("1000000000")) > 0 || price.stripTrailingZeros().scale() > 4) {
            throw new IllegalArgumentException("Prices must be non-negative, at most 1000000000, with up to four decimal places");
        }
    }

    private String text(String value, int max) {
        if (value == null || value.isBlank()) return null;
        if (value.trim().length() > max) throw new IllegalArgumentException("Supplier or invoice number is too long");
        return value.trim();
    }

    private Order dto(RepurchaseOrderEntity order) {
        return new Order(order.id, order.supplier, order.invoiceNumber, order.createdAt.toString(), order.createdBy,
                Boolean.TRUE.equals(order.activation), order.branch, order.receivedPurchaseId, order.lines.stream().map(l -> new OrderLine(l.productId, l.sku, l.productName,
                l.quantity, l.buyingPrice, l.sellingPrice, l.batchNumber,
                l.expiryDate != null ? l.expiryDate.toString() : null, l.location)).toList());
    }

    public record CatalogProduct(Long id, String sku, String name, String barcode, String brand, String category,
                                 String unitOfMeasure, boolean active, BigDecimal buyingPrice, BigDecimal sellingPrice, int availableQuantity) {}
    public record SubmitCommand(String id, String supplier, String invoiceNumber, boolean activation, String branch, List<LineInput> lines) {}
    public record LineInput(long productId, int quantity, BigDecimal buyingPrice, BigDecimal sellingPrice,
                            String batchNumber, String expiryDate, String location) {}
    public record ReceiptLine(long productId, String batchNumber, String expiryDate) {}
    public record Order(String id, String supplier, String invoiceNumber, String createdAt, String createdBy, boolean activation, String branch, Long receivedPurchaseId, List<OrderLine> lines) {}
    public record OrderLine(Long productId, String sku, String productName, int quantity, BigDecimal buyingPrice, BigDecimal sellingPrice,
                            String batchNumber, String expiryDate, String location) {}
}
