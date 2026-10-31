package com.coc.sba_treektour.modules.inventory.repository;

import com.coc.sba_treektour.modules.inventory.entity.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDate;
import java.util.List;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    Optional<Inventory> findByBranchIdAndProductId(Long branchId, Long productId);
    Page<Inventory> findByBranchId(Long branchId, Pageable page);
    @Query(value = """
            SELECT i.rental_start_date AS rentalStartDate, i.rental_end_date AS rentalEndDate, i.quantity AS quantity
            FROM order_items i JOIN orders o ON o.order_id = i.order_id
            WHERE o.branch_id = :branchId AND i.product_id = :productId AND i.item_type = 'RENTAL'
              AND o.order_status NOT IN ('CANCELLED', 'COMPLETED')
              AND i.status IN ('PENDING', 'CONFIRMED')
              AND i.rental_start_date <= :endDate AND i.rental_end_date >= :startDate
            """, nativeQuery = true)
    List<RentalReservationProjection> findRentalReservations(@Param("branchId") Long branchId,
            @Param("productId") Long productId, @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inventory i where i.branchId = :branch and i.productId = :product")
    Optional<Inventory> lock(@Param("branch") Long branchId, @Param("product") Long productId);
}
