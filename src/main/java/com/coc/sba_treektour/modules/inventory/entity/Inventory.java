package com.coc.sba_treektour.modules.inventory.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "inventory", uniqueConstraints = @UniqueConstraint(columnNames = {"branch_id", "product_id"}))
@Getter @Setter @NoArgsConstructor
public class Inventory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inventory_id")
    private Long id;
    // Chi nhánh chưa READY_TO_MAP; giữ hợp đồng ID và FK sẵn có trong V1.
    @Column(name = "branch_id", nullable = false)
    private Long branchId;
    @Column(name = "product_id", nullable = false)
    private Long productId;
    @Column(name = "quantity_total", nullable = false)
    private int quantityTotal;
    @Column(name = "quantity_available", nullable = false)
    private int quantityAvailable;
    @Column(name = "quantity_rented", nullable = false)
    private int quantityRented;
    @Column(nullable = false, length = 30)
    private String status = "ACTIVE";
}
