package com.coc.sba_treektour.modules.order.repository;

import com.coc.sba_treektour.modules.order.entity.Order;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUser_IdOrderByOrderDateDesc(Long userId);
}
