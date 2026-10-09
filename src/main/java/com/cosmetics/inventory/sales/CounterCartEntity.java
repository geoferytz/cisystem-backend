package com.cosmetics.inventory.sales;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "counter_carts")
public class CounterCartEntity {
    public enum Status { PENDING, COMPLETED, CANCELLED }

    @Id
    @Column(length = 36)
    private String id;

    @Version
    private long version;

    @Column(length = 200)
    private String customer;

    @Column(length = 120)
    private String referenceNumber;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Status status = Status.PENDING;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(length = 320)
    private String createdBy;

    private Long completedSaleId;

    @ElementCollection
    @CollectionTable(name = "counter_cart_lines", joinColumns = @JoinColumn(name = "cart_id"))
    @OrderColumn(name = "line_index")
    private List<Line> lines = new ArrayList<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public long getVersion() { return version; }
    public String getCustomer() { return customer; }
    public void setCustomer(String customer) { this.customer = customer; }
    public String getReferenceNumber() { return referenceNumber; }
    public void setReferenceNumber(String referenceNumber) { this.referenceNumber = referenceNumber; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Long getCompletedSaleId() { return completedSaleId; }
    public void setCompletedSaleId(Long completedSaleId) { this.completedSaleId = completedSaleId; }
    public List<Line> getLines() { return lines; }

    @Embeddable
    public static class Line {
        @Column(nullable = false)
        Long productId;
        @Column(nullable = false, length = 80)
        String sku;
        @Column(nullable = false, length = 250)
        String productName;
        @Column(nullable = false)
        int quantity;
        @Column(nullable = false, precision = 19, scale = 4)
        BigDecimal unitPrice;
        @Column(nullable = false, length = 120)
        String location;
    }
}
