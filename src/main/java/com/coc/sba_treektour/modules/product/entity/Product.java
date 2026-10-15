package com.coc.sba_treektour.modules.product.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "products")
@Getter @Setter @NoArgsConstructor
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Long id;
    @Column(name = "product_name", nullable = false, length = 150)
    private String name;
    @Column(length = 100)
    private String category;
    @Column(name = "sale_price", precision = 18, scale = 2)
    private BigDecimal salePrice;
    @Column(name = "rental_price_per_day", precision = 18, scale = 2)
    private BigDecimal rentalPricePerDay;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
    @Column(nullable = false, length = 30)
    private String status = "ACTIVE";
}
