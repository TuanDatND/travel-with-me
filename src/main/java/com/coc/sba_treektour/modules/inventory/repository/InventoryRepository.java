package com.coc.sba_treektour.modules.inventory.repository;

import com.coc.sba_treektour.modules.inventory.entity.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import org.springframework.data.domain.*;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    Optional<Inventory> findByBranchIdAndProductId(Long branchId, Long productId);
    Page<Inventory> findByBranchId(Long branchId, Pageable page);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inventory i where i.branchId = :branch and i.productId = :product")
    Optional<Inventory> lock(@Param("branch") Long branchId, @Param("product") Long productId);
}
