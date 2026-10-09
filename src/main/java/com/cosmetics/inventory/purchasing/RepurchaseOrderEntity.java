package com.cosmetics.inventory.purchasing;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "repurchase_orders")
public class RepurchaseOrderEntity {
    @Id
    @Column(length = 36)
    String id;
    @Version
    Long version;
    @Column(length = 200)
    String supplier;
    @Column(length = 120)
    String invoiceNumber;
    @Column(nullable = false)
    Instant createdAt = Instant.now();
    @Column(length = 320)
    String createdBy;
    Boolean activation;
    @Column(length = 60)
    String branch = "MAIN";
    Long receivedPurchaseId;
    @ElementCollection
    @CollectionTable(name = "repurchase_order_lines", joinColumns = @JoinColumn(name = "order_id"))
    @OrderColumn(name = "line_index")
    List<Line> lines = new ArrayList<>();

    @Embeddable
    public static class Line {
        Long productId;
        @Column(length = 80)
        String sku;
        @Column(length = 250)
        String productName;
        int quantity;
        @Column(precision = 19, scale = 4)
        BigDecimal buyingPrice;
        @Column(precision = 19, scale = 4)
        BigDecimal sellingPrice;
        @Column(precision = 19, scale = 4)
        BigDecimal originalBuyingPrice;
        @Column(precision = 19, scale = 4)
        BigDecimal originalSellingPrice;
        @Column(length = 80)
        String batchNumber;
        LocalDate expiryDate;
        @Column(length = 120)
        String location;
    }
}
