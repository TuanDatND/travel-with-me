package com.coc.sba_treektour.payment.repository;

import com.coc.sba_treektour.payment.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
}
