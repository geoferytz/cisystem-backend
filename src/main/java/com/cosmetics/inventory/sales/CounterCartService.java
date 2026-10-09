package com.cosmetics.inventory.sales;

import com.cosmetics.inventory.graphql.SalesOrderDto;
import com.cosmetics.inventory.inventory.InventoryRepository;
import com.cosmetics.inventory.product.ProductBatchEntity;
import com.cosmetics.inventory.product.ProductEntity;
import com.cosmetics.inventory.product.ProductRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class CounterCartService {
    private final CounterCartRepository carts;
    private final ProductRepository products;
    private final InventoryRepository inventory;
    private final SalesService sales;
    private final SalesOrderRepository orders;
    private final com.cosmetics.inventory.branch.BranchScope branchScope;

    public CounterCartService(CounterCartRepository carts, ProductRepository products, InventoryRepository inventory, SalesService sales, SalesOrderRepository orders, com.cosmetics.inventory.branch.BranchScope branchScope) {
        this.carts = carts;
        this.products = products;
        this.inventory = inventory;
        this.sales = sales;
        this.orders = orders;
        this.branchScope = branchScope;
    }

    @Transactional(readOnly = true)
    public List<ProductDto> products(String location) {
        String selectedLocation = location(location);
        Map<Long, Integer> available = new HashMap<>();
        Map<Long, ProductBatchEntity> earliestBatch = new HashMap<>();
        LocalDate today = LocalDate.now();
        for (var item : inventory.findAll()) {
            var batch = item.getBatch();
            if (selectedLocation.equals(item.getLocation()) && !batch.getExpiryDate().isBefore(today) && item.getQtyOnHand() > 0) {
                Long productId = batch.getProduct().getId();
                available.merge(productId, item.getQtyOnHand(), Math::addExact);
                ProductBatchEntity current = earliestBatch.get(productId);
                if (current == null || batch.getExpiryDate().isBefore(current.getExpiryDate())) {
                    earliestBatch.put(productId, batch);
                }
            }
        }
        return products.findAll().stream().filter(ProductEntity::isActive)
                .sorted(Comparator.comparing(ProductEntity::getName, String.CASE_INSENSITIVE_ORDER))
                .map(p -> {
                    ProductBatchEntity batch = earliestBatch.get(p.getId());
                    LocalDate expiry = batch == null ? null : batch.getExpiryDate();
                    Integer days = expiry == null ? null : (int) ChronoUnit.DAYS.between(today, expiry);
                    BigDecimal selling = p.getSellingPrice();
                    BigDecimal suggested = null;
                    if (selling != null && days != null && days <= 60) {
                        double discount;
                        if (days <= 7) discount = 0.5;
                        else if (days <= 30) discount = 0.75;
                        else discount = 0.9;
                        suggested = selling.multiply(BigDecimal.valueOf(discount)).setScale(2, RoundingMode.HALF_UP);
                    }
                    return new ProductDto(p.getId(), p.getSku(), p.getBarcode(), p.getName(), p.getBrand(), p.getCategory(),
                            p.getUnitOfMeasure(), selling, available.getOrDefault(p.getId(), 0), batch == null ? null : batch.getBatchNumber(), expiry == null ? null : expiry.toString(), days, suggested);
                }).toList();
    }

    @Transactional(readOnly = true)
    public List<CartDto> pending() {
        return carts.findByStatusOrderByUpdatedAtDesc(CounterCartEntity.Status.PENDING).stream().map(this::dto).toList();
    }

    @Transactional
    public CartDto save(SaveCartCommand input, Authentication authentication) {
        String id = UUID.fromString(input.id()).toString();
        String customer = text(input.customer(), 200);
        String reference = text(input.referenceNumber(), 120);
        if (input.lines() == null || input.lines().isEmpty() || input.lines().size() > 100) {
            throw new IllegalArgumentException("Add between 1 and 100 products to the cart");
        }
        CounterCartEntity cart = carts.lockById(id).orElse(null);
        if (cart != null) {
            requirePending(cart);
            if (input.version() == null && sameRequest(cart, customer, reference, input.lines())) return dto(cart);
            requireVersion(cart, input.version());
        } else {
            if (input.version() != null) throw new IllegalArgumentException("This held cart no longer exists. Refresh pending sales.");
            cart = new CounterCartEntity();
            cart.setId(id);
            cart.setCreatedBy(authentication.getName());
        }
        List<CounterCartEntity.Line> lines = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (LineCommand item : input.lines()) {
            if (item == null || item.quantity() < 1 || item.quantity() > 1000000) {
                throw new IllegalArgumentException("Quantity must be a whole number between 1 and 1,000,000");
            }
            var product = pricedProduct(item.productId());
            var line = new CounterCartEntity.Line();
            line.productId = product.getId();
            line.sku = product.getSku();
            line.productName = product.getName();
            line.quantity = item.quantity();
            line.unitPrice = product.getSellingPrice();
            line.location = location(item.location());
            if (!keys.add(line.productId + ":" + line.location)) throw new IllegalArgumentException("Combine duplicate products in the cart");
            lines.add(line);
        }
        cart.setCustomer(customer);
        cart.setReferenceNumber(reference);
        cart.getLines().clear();
        cart.getLines().addAll(lines);
        cart.setUpdatedAt(Instant.now());
        return dto(carts.saveAndFlush(cart));
    }

    @Transactional
    public SalesOrderDto checkout(String id, long version, BigDecimal expectedTotal, Authentication authentication) {
        CounterCartEntity cart = carts.lockById(id).orElseThrow(() -> new IllegalArgumentException("Held cart not found"));
        if (cart.getStatus() == CounterCartEntity.Status.COMPLETED) {
            return SalesOrderDto.from(orders.findById(cart.getCompletedSaleId())
                    .orElseThrow(() -> new IllegalArgumentException("This cart was already checked out")));
        }
        requirePending(cart);
        requireVersion(cart, version);
        for (var line : cart.getLines()) {
            branchScope.assertBranch(authentication, line.location);
        }
        if (expectedTotal == null || expectedTotal.compareTo(total(cart)) != 0) {
            throw new IllegalArgumentException("The cart total changed. Review it before completing the sale.");
        }
        for (var line : cart.getLines()) {
            if (pricedProduct(line.productId).getSellingPrice().compareTo(line.unitPrice) != 0) {
                throw new IllegalArgumentException("A selling price changed. Save the cart again to review current prices.");
            }
        }
        var saleLines = cart.getLines().stream().sorted(Comparator.comparing(l -> l.productId))
                .map(l -> new SalesService.CreateSaleLineCommand(l.productId, l.quantity, l.unitPrice.doubleValue(), l.location)).toList();
        var order = sales.createSale(new SalesService.CreateSaleCommand(cart.getCustomer(), cart.getReferenceNumber(), saleLines), authentication);
        cart.setStatus(CounterCartEntity.Status.COMPLETED);
        cart.setCompletedSaleId(order.getId());
        cart.setUpdatedAt(Instant.now());
        carts.saveAndFlush(cart);
        return SalesOrderDto.from(order);
    }

    @Transactional
    public boolean cancel(String id, long version, Authentication authentication) {
        var cart = carts.lockById(id).orElseThrow(() -> new IllegalArgumentException("Held cart not found"));
        requirePending(cart);
        requireVersion(cart, version);
        for (var line : cart.getLines()) {
            branchScope.assertBranch(authentication, line.location);
        }
        cart.setStatus(CounterCartEntity.Status.CANCELLED);
        cart.setUpdatedAt(Instant.now());
        carts.saveAndFlush(cart);
        return true;
    }

    private ProductEntity pricedProduct(long id) {
        var product = products.findById(id).orElseThrow(() -> new IllegalArgumentException("Product not found"));
        if (!product.isActive() || product.getSellingPrice() == null || product.getSellingPrice().signum() < 0) {
            throw new IllegalArgumentException("Product " + product.getSku() + " is inactive or has no valid selling price");
        }
        return product;
    }

    private void requirePending(CounterCartEntity cart) {
        if (cart.getStatus() != CounterCartEntity.Status.PENDING) throw new IllegalArgumentException("This cart is no longer pending. Refresh pending sales.");
    }

    private void requireVersion(CounterCartEntity cart, Long version) {
        if (version == null || version != cart.getVersion()) throw new IllegalArgumentException("Another cashier changed this cart. Refresh and reopen it before saving.");
    }

    private boolean sameRequest(CounterCartEntity cart, String customer, String reference, List<LineCommand> lines) {
        if (!Objects.equals(customer, cart.getCustomer()) || !Objects.equals(reference, cart.getReferenceNumber()) || lines.size() != cart.getLines().size()) return false;
        for (int i = 0; i < lines.size(); i++) {
            var a = lines.get(i);
            var b = cart.getLines().get(i);
            if (a == null || a.productId() != b.productId || a.quantity() != b.quantity || !location(a.location()).equals(b.location)) return false;
        }
        return true;
    }

    private String text(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String result = value.trim();
        if (result.length() > max) throw new IllegalArgumentException("Text exceeds " + max + " characters");
        return result;
    }

    private String location(String value) {
        String result = text(value, 120);
        return result == null ? "MAIN" : result;
    }

    private BigDecimal total(CounterCartEntity cart) {
        return cart.getLines().stream().map(l -> l.unitPrice.multiply(BigDecimal.valueOf(l.quantity))).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private CartDto dto(CounterCartEntity cart) {
        return new CartDto(cart.getId(), cart.getVersion(), cart.getCustomer(), cart.getReferenceNumber(), cart.getCreatedAt().toString(), cart.getUpdatedAt().toString(), cart.getCreatedBy(), total(cart),
                cart.getLines().stream().map(l -> new LineDto(l.productId, l.sku, l.productName, l.quantity, l.unitPrice, l.location)).toList());
    }

    public record SaveCartCommand(String id, Long version, String customer, String referenceNumber, List<LineCommand> lines) {}
    public record LineCommand(long productId, int quantity, String location) {}
    public record LineDto(Long productId, String sku, String productName, int quantity, BigDecimal unitPrice, String location) {}
    public record CartDto(String id, long version, String customer, String referenceNumber, String createdAt, String updatedAt, String createdBy, BigDecimal total, List<LineDto> lines) {}
    public record ProductDto(Long id, String sku, String barcode, String name, String brand, String category, String unitOfMeasure, BigDecimal sellingPrice, int availableQuantity, String batchNumber, String expiryDate, Integer daysToExpiry, BigDecimal suggestedSellingPrice) {}
}
