package com.coc.sba_treektour.modules.payment.repository;

import com.coc.sba_treektour.modules.payment.dto.request.RevenueReportRequest;
import com.coc.sba_treektour.modules.payment.enums.ReportPeriod;
import com.coc.sba_treektour.modules.payment.enums.RevenueSector;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Explicit local-only PostgreSQL fixture, in an isolated schema; never uses .env or shared DB. */
@EnabledIfEnvironmentVariable(named = "REPORT_TEST_DB_URL", matches = "jdbc:postgresql://127\\.0\\.0\\.1:55438/.*")
class RevenueReportRepositoryTests {
    static JdbcTemplate sql;
    static JdbcTemplate admin;
    static RevenueReportRepository repository;
    static final String SCHEMA = "report_" + UUID.randomUUID().toString().replace("-", "");

    @BeforeAll static void setup() {
        String url = System.getenv("REPORT_TEST_DB_URL");
        var root = new DriverManagerDataSource(url, "report_test", "");
        admin = new JdbcTemplate(root);
        admin.execute("CREATE SCHEMA " + SCHEMA);
        var dataSource = new DriverManagerDataSource(url + "?currentSchema=" + SCHEMA, "report_test", "");
        sql = new JdbcTemplate(dataSource);
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V1__init.sql")).execute(dataSource);
        sql.execute("""
            INSERT INTO roles(role_id,role_name) VALUES(1,'CUSTOMER');
            INSERT INTO users(user_id,role_id,full_name,email,password) VALUES(1,1,'Test','report@test','hash');
            INSERT INTO branches(branch_id,branch_name) VALUES(1,'Branch A'),(2,'Branch B');
            INSERT INTO products(product_id,product_name,category,sale_price,rental_price_per_day,status)
                VALUES(1,'Tent','CAMPING',600,100,'ACTIVE'),(2,'Backpack','CAMPING',400,100,'ACTIVE');
            INSERT INTO orders(order_id,user_id,branch_id,total_amount) VALUES(1,1,1,1000),(2,1,2,1000),(3,1,1,600);
            INSERT INTO order_items(order_id,product_id,item_type,quantity,unit_price,status)
                VALUES(1,1,'SALE',1,600,'PENDING'),(1,2,'SALE',1,400,'PENDING');
            INSERT INTO order_items(order_id,product_id,item_type,quantity,unit_price,rental_start_date,rental_end_date,status)
                VALUES(2,1,'RENTAL',2,100,'2026-10-01','2026-10-05','PENDING');
            INSERT INTO tour_events(event_id,event_name,event_type,base_price) VALUES(1,'Langbiang','TREKKING',500);
            INSERT INTO event_schedules(schedule_id,event_id,branch_id,start_datetime,end_datetime,max_participants)
                VALUES(1,1,2,'2026-11-20 08:00+07','2026-11-20 18:00+07',10);
            INSERT INTO event_participants(participant_id,schedule_id,user_id,registered_price) VALUES(1,1,1,500);
            INSERT INTO transactions(transaction_id,order_id,transaction_type,amount,payment_method,status)
                VALUES(90,3,'PAYMENT',600,'ZALOPAY','SUCCEEDED');
            """);
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V2026103001__transaction_refund_reason.sql"),
                new ClassPathResource("db/migration/V2026103002__transaction_succeeded_at.sql")).execute(dataSource);
        sql.execute("""
            INSERT INTO transactions(transaction_id,order_id,transaction_type,amount,payment_method,status,created_at,succeeded_at) VALUES
            (1,1,'PAYMENT',1000,'ZALOPAY','SUCCEEDED','2026-09-30 08:00+07','2026-10-01 00:00+07'),
            (2,1,'REFUND',200,'MANUAL','SUCCEEDED','2026-10-30 08:00+07','2026-11-01 08:00+07'),
            (3,2,'PAYMENT',400,'ZALOPAY','SUCCEEDED','2026-10-01 08:00+07','2026-10-02 08:00+07'),
            (4,2,'PAYMENT',600,'ZALOPAY','SUCCEEDED','2026-10-02 08:00+07','2026-10-03 08:00+07'),
            (5,1,'REFUND',800,'MANUAL','PENDING','2026-10-15 08:00+07',NULL),
            (6,1,'PAYMENT',1000,'ZALOPAY','FAILED','2026-10-01 08:00+07',NULL);
            INSERT INTO transactions(transaction_id,participant_id,transaction_type,amount,payment_method,status,created_at,succeeded_at) VALUES
            (7,1,'PAYMENT',500,'ZALOPAY','SUCCEEDED','2026-10-30 08:00+07','2026-10-31 23:59:59+07'),
            (8,1,'REFUND',500,'MANUAL','SUCCEEDED','2026-10-31 23:59:59+07','2026-11-01 00:00:00+07');
            """);
        repository = new RevenueReportRepository(new NamedParameterJdbcTemplate(dataSource));
    }

    @AfterAll static void cleanup() { if (admin != null) admin.execute("DROP SCHEMA " + SCHEMA + " CASCADE"); }
    static RevenueReportRequest filter(String from, String to) {
        var f = new RevenueReportRequest(); f.setFrom(LocalDate.parse(from)); f.setTo(LocalDate.parse(to)); return f;
    }
    static void equal(String expected, BigDecimal actual) { assertEquals(0, new BigDecimal(expected).compareTo(actual)); }

    @Test void migrationDoesNotInventHistoricalSuccessTime() {
        assertNull(sql.queryForObject("SELECT succeeded_at FROM transactions WHERE transaction_id=90", Object.class));
        var old = repository.undated(filter("2026-10-01", "2026-10-31"));
        equal("600", old.receivedAmount()); assertEquals(1, old.paymentCount());
    }
    @Test void monetaryAggregatesDoNotMultiplyByItemsAndRespectVietnamBoundaries() {
        var f = filter("2026-10-01", "2026-10-31");
        var result = repository.totals(f);
        equal("2500", result.receivedAmount()); equal("0", result.refundedAmount()); assertEquals(4, result.paymentCount());
        equal("800", repository.pendingRefunds(f).refundedAmount());
        assertEquals(LocalDate.parse("2026-10-31"), repository.series(f).getLast().date());
        f.setPeriod(ReportPeriod.MONTH); assertEquals(1, repository.series(f).size());
        equal("2500", repository.series(f).getFirst().receivedAmount());
    }
    @Test void refundBelongsToItsOwnSuccessMonthAndNetCanBeNegative() {
        var f = filter("2026-11-01", "2026-11-30");
        equal("-700", repository.totals(f).netAmount());
        equal("700", repository.totals(f).refundedAmount());
        assertEquals(0, repository.totals(f).paymentCount());
    }
    @Test void branchSectorAndMethodFiltersApplyWithoutDroppingEitherTarget() {
        var f = filter("2026-10-01", "2026-10-31");
        assertEquals(2, repository.branches(f).size()); assertEquals(2, repository.sectors(f).size());
        f.setBranchId(2L); equal("1500", repository.totals(f).receivedAmount());
        f.setSector(RevenueSector.TOUR); equal("500", repository.totals(f).receivedAmount());
        f.setPaymentMethod("MANUAL"); equal("0", repository.totals(f).receivedAmount());
        assertEquals(0, repository.transactionCount(f));
    }
    @Test void productsUseSnapshotPriceInclusiveRentalDaysAndFirstFullPaymentOnly() {
        var f = filter("2026-10-01", "2026-10-31");
        var rows = repository.products(f);
        assertEquals(2, rows.size());
        var tent = rows.stream().filter(row -> row.productId() == 1L).findFirst().orElseThrow();
        equal("600", tent.saleValue()); equal("1000", tent.rentalValue());
        assertEquals(10, tent.rentalDays()); assertEquals(2, tent.paidOrderCount());
        assertEquals(3, repository.productSeries(f).size());
        assertTrue(repository.products(filter("2026-10-02", "2026-10-02")).isEmpty());
        assertTrue(repository.products(filter("2026-11-01", "2026-11-30")).isEmpty());
        f.setProductId(2L); assertEquals(1, repository.products(f).size());
        f.setBranchId(2L); assertTrue(repository.products(f).isEmpty());
    }
    @Test void toursAndSchedulesHaveSeparateGrossRefundAndUniqueTickets() {
        var f = filter("2026-10-01", "2026-11-30");
        var tour = repository.tours(f).getFirst();
        equal("500", tour.receivedAmount()); equal("500", tour.refundedAmount()); equal("0", tour.netAmount());
        assertEquals(1, tour.paidTicketCount()); assertEquals(2, repository.tourSeries(f).size());
        assertEquals(2L, repository.schedules(f).getFirst().branchId());
        f.setTourId(999L); assertTrue(repository.tours(f).isEmpty());
    }
    @Test void transactionPaginationIsStableAndContainsSuccessTimestamp() {
        var f = filter("2026-10-01", "2026-10-31");
        assertEquals(4, repository.transactionCount(f));
        assertEquals(7L, repository.transactions(f, 0, 1).getFirst().transactionId());
        assertEquals(4L, repository.transactions(f, 1, 1).getFirst().transactionId());
        assertTrue(repository.transactions(f, 9, 20).isEmpty());
        assertEquals(2L, repository.transactions(f, 0, 1).getFirst().branchId());
    }
}
