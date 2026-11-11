package com.coc.sba_treektour.modules.inventory.dto.response;

import java.time.LocalDate;

public record AvailabilityResponse(Long branchId, Long productId, LocalDate startDate,
        LocalDate endDate, int quantityAvailable, long peakReserved, int quantityRentable) {}
