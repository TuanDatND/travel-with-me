package com.coc.sba_treektour.modules.order.service;

import com.coc.sba_treektour.modules.order.dto.requset.CreateOrderRequest;
import com.coc.sba_treektour.modules.order.dto.respone.OrderResponse;

import java.util.List;

public interface OrderService {
    OrderResponse create(CreateOrderRequest request, String email);

    OrderResponse findById(Long id);

    List<OrderResponse> findAll(Long userId);

    OrderResponse update(Long id, CreateOrderRequest request, String email);

    void delete(Long id, String email);

    OrderResponse findMyOrder(Long id, String email);

    List<OrderResponse> findMyOrders(String email);

    OrderResponse cancel(Long id, String email);

    OrderResponse operate(Long id, String action);
}
