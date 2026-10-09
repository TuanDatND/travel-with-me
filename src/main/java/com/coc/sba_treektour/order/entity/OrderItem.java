package com.coc.sba_treektour.order.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_item_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    // Product is owned by the product module; keep only its ID until integration.
    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 30)
    private OrderItemType itemType;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "rental_start_date")
    private LocalDate rentalStartDate;

    @Column(name = "rental_end_date")
    private LocalDate rentalEndDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderItemStatus status = OrderItemStatus.PENDING;

    protected OrderItem() {}

    public OrderItem(Long productId, OrderItemType itemType, int quantity, BigDecimal unitPrice,
                     LocalDate rentalStartDate, LocalDate rentalEndDate, OrderItemStatus status) {
        this.productId = productId;
        this.itemType = itemType;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.rentalStartDate = rentalStartDate;
        this.rentalEndDate = rentalEndDate;
        this.status = status;
    }

    void assignOrder(Order order) { this.order = order; }

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public OrderItemType getItemType() { return itemType; }
    public int getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public LocalDate getRentalStartDate() { return rentalStartDate; }
    public LocalDate getRentalEndDate() { return rentalEndDate; }
    public OrderItemStatus getStatus() { return status; }
}
