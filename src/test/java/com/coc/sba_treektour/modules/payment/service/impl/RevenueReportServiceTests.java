package com.coc.sba_treektour.modules.payment.service.impl;

import com.coc.sba_treektour.modules.account.entity.Role;
import com.coc.sba_treektour.modules.account.entity.User;
import com.coc.sba_treektour.modules.account.repository.UserRepository;
import com.coc.sba_treektour.modules.payment.controller.RevenueReportController;
import com.coc.sba_treektour.modules.payment.dto.request.RevenueReportRequest;
import com.coc.sba_treektour.modules.payment.dto.response.MoneyTotals;
import com.coc.sba_treektour.modules.payment.dto.response.ReportTransactionRow;
import com.coc.sba_treektour.modules.payment.dto.response.RevenuePoint;
import com.coc.sba_treektour.modules.payment.enums.ReportPeriod;
import com.coc.sba_treektour.modules.payment.enums.RevenueSector;
import com.coc.sba_treektour.modules.payment.repository.RevenueReportRepository;
import com.coc.sba_treektour.modules.payment.service.RevenueReportService;
import jakarta.validation.Validation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;
import org.springframework.context.annotation.Bean;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RevenueReportServiceTests {
    RevenueReportRepository repository = mock(RevenueReportRepository.class);
    UserRepository users = mock(UserRepository.class);
    RevenueReportServiceImpl service = new RevenueReportServiceImpl(repository, users);
    User admin = User.builder().id(1L).email("admin@test").status("ACTIVE").role(Role.builder().name("ADMIN").build()).build();
    RevenueReportRequest request;

    @BeforeEach void setup() {
        request = new RevenueReportRequest();
        request.setFrom(LocalDate.parse("2026-10-01")); request.setTo(LocalDate.parse("2026-10-03"));
        login("ADMIN");
        when(users.findByEmail("admin@test")).thenReturn(Optional.of(admin));
        var zero = new MoneyTotals(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0);
        when(repository.totals(any())).thenReturn(zero);
        when(repository.pendingRefunds(any())).thenReturn(zero);
        when(repository.undated(any())).thenReturn(zero);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    private void login(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin@test", "",
                List.of(new SimpleGrantedAuthority(role))));
    }
    @Test void fillsZeroDaysAndPreservesNegativeNet() {
        when(repository.series(request)).thenReturn(List.of(new RevenuePoint(LocalDate.parse("2026-10-02"),
                BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.TEN.negate())));
        var result = service.overview(request);
        assertEquals(3, result.series().size());
        assertEquals(BigDecimal.ZERO, result.series().getFirst().netAmount());
        assertEquals(BigDecimal.TEN.negate(), result.series().get(1).netAmount());
        assertEquals("Asia/Ho_Chi_Minh", result.timezone());
        assertEquals("BACKEND_VERIFIED_SUCCESS_AT", result.timeBasis());
    }
    @Test void fillsMonthBucketsAndReportsUnknownDatesSeparately() {
        request.setFrom(LocalDate.parse("2026-09-15")); request.setTo(LocalDate.parse("2026-10-03"));
        request.setPeriod(ReportPeriod.MONTH);
        when(repository.undated(request)).thenReturn(new MoneyTotals(BigDecimal.TEN, BigDecimal.ONE, new BigDecimal("9"), 2, 1));
        var result = service.overview(request);
        assertEquals(2, result.series().size());
        assertEquals(LocalDate.parse("2026-09-01"), result.series().getFirst().date());
        assertEquals(3, result.undatedSucceededCount());
    }
    @Test void rejectsAmbiguousAttributionAndMisleadingFilters() {
        request.setProductId(1L);
        assertThrows(ResponseStatusException.class, () -> service.overview(request));
        assertThrows(ResponseStatusException.class, () -> service.transactions(request, 0, 20));
        assertEquals("ITEM_VALUE_IN_FULLY_PAID_ORDERS_BEFORE_REFUNDS", service.products(request).metric());
        request.setProductId(null); request.setPaymentMethod("MANUAL");
        assertThrows(ResponseStatusException.class, () -> service.products(request));
        request.setPaymentMethod(null); request.setSector(RevenueSector.EQUIPMENT);
        assertThrows(ResponseStatusException.class, () -> service.tours(request));
    }
    @Test void csvEscapesQuotesAndSpreadsheetFormulasAndCapsExport() {
        when(repository.transactionCount(request)).thenReturn(1L);
        var now = OffsetDateTime.now();
        when(repository.transactions(request, 0, 10000)).thenReturn(List.of(new ReportTransactionRow(1L, 2L, null, 1L,
                "=SUM(1,2)", 1L, "Tour \"name\"", "PAYMENT", "ZALOPAY", BigDecimal.TEN, now, now)));
        String csv = service.csv(request);
        assertTrue(csv.contains("\"'=SUM(1,2)\""));
        assertTrue(csv.contains("\"Tour \"\"name\"\"\""));
        assertTrue(csv.startsWith("\ufefftransactionId"));
        when(repository.transactionCount(request)).thenReturn(10001L);
        assertThrows(ResponseStatusException.class, () -> service.csv(request));
    }
    @Test void paginationIsBoundedAndUsesExactTotal() {
        when(repository.transactionCount(request)).thenReturn(21L);
        assertEquals(2, service.transactions(request, 0, 20).totalPages());
        assertThrows(ResponseStatusException.class, () -> service.transactions(request, -1, 20));
        assertThrows(ResponseStatusException.class, () -> service.transactions(request, 0, 101));
    }
    @Test void requestValidationRejectsMissingReversedOversizedOrMixedFilters() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertTrue(validator.validate(request).isEmpty());
            request.setTo(request.getFrom().minusDays(1)); assertFalse(validator.validate(request).isEmpty());
            request.setTo(request.getFrom().plusDays(366)); assertFalse(validator.validate(request).isEmpty());
            request.setTo(request.getFrom()); request.setProductId(1L); request.setTourId(1L);
            assertFalse(validator.validate(request).isEmpty());
            assertFalse(validator.validate(new RevenueReportRequest()).isEmpty());
        }
    }
    @Test void controllerRejectsInvalidDateRangeBeforeQueries() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new RevenueReportController(mock(RevenueReportService.class))).build();
        mvc.perform(get("/api/reports/revenue")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/reports/revenue").param("from", "2026-10-05").param("to", "2026-10-01"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/reports/revenue").param("from", "2026-10-01").param("to", "2026-10-05").param("period", "INVALID"))
                .andExpect(status().isBadRequest());
    }
    @Configuration @EnableMethodSecurity static class Security {
        @Bean static MethodValidationPostProcessor methodValidation() { return new MethodValidationPostProcessor(); }
    }
    @Test void realMethodSecurityRejectsEveryReportForCustomer() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(Security.class); context.registerBean(RevenueReportService.class, () -> service); context.refresh();
            var secured = context.getBean(RevenueReportService.class);
            login("CUSTOMER");
            assertThrows(AccessDeniedException.class, () -> secured.overview(request));
            assertThrows(AccessDeniedException.class, () -> secured.products(request));
            assertThrows(AccessDeniedException.class, () -> secured.tours(request));
            assertThrows(AccessDeniedException.class, () -> secured.transactions(request, 0, 20));
            assertThrows(AccessDeniedException.class, () -> secured.csv(request));
            login("ADMIN");
            assertEquals(3, secured.overview(request).series().size());
            assertThrows(ConstraintViolationException.class, () -> secured.overview(new RevenueReportRequest()));
            admin.setStatus("BLOCKED");
            assertThrows(ResponseStatusException.class, () -> secured.overview(request));
        }
    }
}
