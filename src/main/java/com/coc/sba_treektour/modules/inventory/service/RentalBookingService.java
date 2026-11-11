package com.coc.sba_treektour.modules.inventory.service;

import com.coc.sba_treektour.modules.inventory.dto.request.RentalBookingRequest;
import com.coc.sba_treektour.modules.order.dto.response.OrderResponse;

public interface RentalBookingService {
    OrderResponse book(RentalBookingRequest request);
}
