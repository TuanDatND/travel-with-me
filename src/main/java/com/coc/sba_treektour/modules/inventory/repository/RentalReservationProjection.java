package com.coc.sba_treektour.modules.inventory.repository;

import java.time.LocalDate;

public interface RentalReservationProjection {
    LocalDate getRentalStartDate();
    LocalDate getRentalEndDate();
    int getQuantity();
}
