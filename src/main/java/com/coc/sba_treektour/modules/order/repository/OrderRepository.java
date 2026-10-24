package com.coc.sba_treektour.modules.order.repository;

import com.coc.sba_treektour.modules.order.entity.Order;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select o from Order o where o.id = :id")
    java.util.Optional<Order> lockById(@org.springframework.data.repository.query.Param("id") Long id);
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"items", "user", "branch"})
    List<Order> findByUser_IdOrderByOrderDateDesc(Long userId);

    @Override
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"items", "user", "branch"})
    List<Order> findAll();
}
