package com.coc.sba_treektour.modules.payment.repository;

import com.coc.sba_treektour.modules.payment.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findAllByOrderByCreatedAtDesc();
}
