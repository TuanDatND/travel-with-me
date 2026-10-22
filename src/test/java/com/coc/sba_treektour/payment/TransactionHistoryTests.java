package com.coc.sba_treektour.payment;

import com.coc.sba_treektour.common.exception.GlobalExceptionHandler;
import com.coc.sba_treektour.modules.payment.controller.TransactionController;
import com.coc.sba_treektour.modules.payment.entity.Transaction;
import com.coc.sba_treektour.modules.payment.enums.TransactionStatus;
import com.coc.sba_treektour.modules.payment.repository.TransactionRepository;
import com.coc.sba_treektour.modules.payment.service.TransactionsServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TransactionHistoryTests {
    private final TransactionRepository repository = mock(TransactionRepository.class);
    // Các cấu hình ZaloPay không được dùng khi đọc lịch sử.
    private final TransactionsServiceImpl service = new TransactionsServiceImpl(repository, null, null, null, null, null, null);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new TransactionController(service))
            .setControllerAdvice(new GlobalExceptionHandler()).build();

    @Test
    void defaultsReturnPagedHistoryWithLongTargetIds() throws Exception {
        var pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        var order = org.mockito.Mockito.mock(com.coc.sba_treektour.modules.order.entity.Order.class);
        when(order.getId()).thenReturn(5L);
        var transaction = Transaction.builder().id(11L).order(order)
                .transactionType("PAYMENT").amount(new BigDecimal("150000"))
                .paymentMethod("ZALOPAY").status(TransactionStatus.SUCCEEDED)
                .createdAt(OffsetDateTime.parse("2026-10-22T10:00:00+07:00")).build();
        when(repository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(transaction), pageable, 21));

        mvc.perform(get("/api/payments/transactions/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalElements").value(21))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.content[0].transactionId").value(11))
                .andExpect(jsonPath("$.data.content[0].orderId").value(5))
                .andExpect(jsonPath("$.data.content[0].amount").value(150000))
                .andExpect(jsonPath("$.data.content[0].status").value("SUCCEEDED"));
        verify(repository).findAll(pageable);
    }

    @Test
    void pageBeyondEndReturnsEmptyContentAndPreservesTotals() throws Exception {
        var pageable = PageRequest.of(3, 10, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        when(repository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 21));

        mvc.perform(get("/api/payments/transactions/history").param("page", "3").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.page").value(3))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.totalElements").value(21))
                .andExpect(jsonPath("$.data.totalPages").value(3));
    }

    @Test
    void emptyHistoryReturnsZeroTotals() throws Exception {
        var pageable = PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        when(repository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));

        mvc.perform(get("/api/payments/transactions/history").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.totalPages").value(0));
    }

    @Test
    void rejectsInvalidPaginationBeforeQueryingDatabase() throws Exception {
        for (String[] params : List.of(new String[]{"-1", "20"}, new String[]{"0", "0"},
                new String[]{"0", "-1"}, new String[]{"0", "101"}, new String[]{"abc", "20"})) {
            mvc.perform(get("/api/payments/transactions/history")
                            .param("page", params[0]).param("size", params[1]))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(repository);
    }
}
