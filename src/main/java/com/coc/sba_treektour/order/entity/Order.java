package com.coc.sba_treektour.order.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long id;

    // Foreign-key relationships will be added when account/branch modules expose their entities.
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "order_date", nullable = false)
    private OffsetDateTime orderDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_status", nullable = false, length = 30)
    private OrderStatus status = OrderStatus.PENDING;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {}

    public Order(Long userId, Long branchId, OffsetDateTime orderDate, OrderStatus status, BigDecimal totalAmount) {
        this.userId = userId;
        this.branchId = branchId;
        this.orderDate = orderDate;
        this.status = status;
        this.totalAmount = totalAmount;
    }

    public void update(Long userId, Long branchId, OffsetDateTime orderDate, OrderStatus status, BigDecimal totalAmount) {
        this.userId = userId;
        this.branchId = branchId;
        this.orderDate = orderDate;
        this.status = status;
        this.totalAmount = totalAmount;
    }

    public void replaceItems(List<OrderItem> newItems) {
        items.clear();
        newItems.forEach(this::addItem);
    }

    public void addItem(OrderItem item) {
        item.assignOrder(this);
        items.add(item);
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getBranchId() { return branchId; }
    public OffsetDateTime getOrderDate() { return orderDate; }
    public OrderStatus getStatus() { return status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public List<OrderItem> getItems() { return items; }
}
