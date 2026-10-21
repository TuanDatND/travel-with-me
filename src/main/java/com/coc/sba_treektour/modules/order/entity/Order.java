package com.coc.sba_treektour.modules.order.entity;

import com.coc.sba_treektour.modules.account.entity.User;
import com.coc.sba_treektour.modules.branch.entity.Branch;
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

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

    public Order(User user, Branch branch, OffsetDateTime orderDate, OrderStatus status, BigDecimal totalAmount) {
        this.user = user;
        this.branch = branch;
        this.orderDate = orderDate;
        this.status = status;
        this.totalAmount = totalAmount;
    }

    public void update(Branch branch, OffsetDateTime orderDate, OrderStatus status, BigDecimal totalAmount) {
        this.branch = branch;
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
    public Long getUserId() { return user.getId(); }
    public Long getBranchId() { return branch.getId(); }
    public User getUser() { return user; }
    public OffsetDateTime getOrderDate() { return orderDate; }
    public OrderStatus getStatus() { return status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public List<OrderItem> getItems() { return items; }
}
