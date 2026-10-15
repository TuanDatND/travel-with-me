package com.coc.sba_treektour.inventory;

import com.coc.sba_treektour.common.config.JwtService;
import com.coc.sba_treektour.modules.account.entity.User;
import com.coc.sba_treektour.modules.account.repository.UserRepository;
import com.coc.sba_treektour.modules.inventory.dto.InventoryDtos.AdjustRequest;
import com.coc.sba_treektour.modules.inventory.repository.InventoryRepository;
import com.coc.sba_treektour.modules.inventory.service.InventoryService;
import com.coc.sba_treektour.modules.product.dto.ProductDtos.*;
import com.coc.sba_treektour.modules.product.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Transactional
class CatalogIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ProductService products;
    @Autowired InventoryService inventory;
    @Autowired InventoryRepository rows;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;
    @Autowired jakarta.persistence.EntityManager entityManager;
    Long product, branch;

    @BeforeEach
    void fixtures() {
        branch = jdbc.queryForObject("INSERT INTO branches(branch_name) VALUES ('Catalog test') RETURNING branch_id", Long.class);
        product = products.create(new ProductRequest("Lều thử " + UUID.randomUUID(), "Camping", new BigDecimal("1000000"), new BigDecimal("120000"))).id();
    }

    @AfterEach
    void cleanupCommittedConcurrencyFixtures() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            jdbc.update("DELETE FROM inventory WHERE product_id = ?", product);
            jdbc.update("DELETE FROM products WHERE product_id = ?", product);
            jdbc.update("DELETE FROM branches WHERE branch_id = ?", branch);
        }
    }

    @Test
    void publicSearchFiltersModesPricesAndHidesInactive() throws Exception {
        var hidden = products.create(new ProductRequest("HIDDEN-CATALOG", "Camping", null, BigDecimal.ONE));
        products.status(hidden.id(), Status.INACTIVE);
        mvc.perform(get("/api/products").param("keyword", "Lều thử").param("category", "camping")
                .param("mode", "RENTAL").param("minPrice", "100000").param("maxPrice", "150000"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
        mvc.perform(get("/api/products/" + hidden.id())).andExpect(status().isNotFound());
        mvc.perform(get("/api/products").param("keyword", "HIDDEN-CATALOG"))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void badFiltersAndMissingProductsHaveCorrectHttpStatus() throws Exception {
        mvc.perform(get("/api/products").param("size", "101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/products").param("minPrice", "1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/products").param("mode", "RENTAL").param("minPrice", "10").param("maxPrice", "1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/products").param("mode", "BAD")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/products/999999999")).andExpect(status().isNotFound());
        mvc.perform(get("/api/products").param("keyword", "%")).andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void protectedEndpointsRequireAuthentication() throws Exception {
        mvc.perform(post("/api/admin/products").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/inventory/branches/" + branch)).andExpect(status().isUnauthorized());
    }

    @Test
    void desktopPagesAndTheirAssetsLoadBeforeLoginWhileDataStaysProtected() throws Exception {
        for (String path : List.of("/equipment.html", "/equipment-detail.html", "/equipment-admin.html",
                "/equipment-edit.html", "/inventory.html", "/login.html", "/assets/catalog/catalog.css",
                "/assets/catalog/common.js", "/assets/catalog/products.js", "/assets/catalog/inventory.js")) {
            mvc.perform(get(path)).andExpect(status().isOk());
        }
        mvc.perform(get("/api/admin/products")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/inventory/branches/" + branch)).andExpect(status().isUnauthorized());
    }

    @Test @WithMockUser(authorities = "CUSTOMER")
    void customerCannotWriteProductsOrReadPrivateInventory() throws Exception {
        mvc.perform(get("/api/admin/products")).andExpect(status().isForbidden());
        mvc.perform(get("/api/inventory/branches/" + branch)).andExpect(status().isForbidden());
    }

    @Test @WithMockUser(authorities = "ADMIN")
    void adminCrudAndValidation() throws Exception {
        mvc.perform(post("/api/admin/products").contentType("application/json").content("""
                {"name":"Balô","salePrice":0,"rentalPricePerDay":null}
                """)).andExpect(status().isCreated()).andExpect(jsonPath("$.data.salePrice").value(0));
        for (String invalid : List.of("{}", "{\"name\":\"No prices\"}", "{\"name\":\"Negative\",\"salePrice\":-1}",
                "{\"name\":\"Precision\",\"salePrice\":1.001}")) {
            mvc.perform(post("/api/admin/products").contentType("application/json").content(invalid)).andExpect(status().isBadRequest());
        }
        mvc.perform(put("/api/admin/products/" + product).contentType("application/json").content("""
                {"name":"Đã cập nhật","category":"Hiking","rentalPricePerDay":50000}
                """)).andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("Đã cập nhật"));
        mvc.perform(patch("/api/admin/products/" + product + "/status").contentType("application/json").content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/products/" + product)).andExpect(jsonPath("$.data.status").value("INACTIVE"));
    }

    @Test
    void inactiveAccountJwtCannotManageCatalog() throws Exception {
        User account = account("ADMIN");
        account.setStatus("INACTIVE"); users.saveAndFlush(account);
        mvc.perform(get("/api/admin/products").header("Authorization", "Bearer " + jwt.generateToken(account)))
                .andExpect(status().isUnauthorized());
    }

    @Test @WithMockUser(authorities = "ADMIN")
    void stockImportAdjustmentAndStatusPreserveQuantities() throws Exception {
        mvc.perform(post(stockPath() + "/import").contentType("application/json").content("{\"quantity\":10}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.quantityTotal").value(10));
        mvc.perform(post(stockPath() + "/import").contentType("application/json").content("{\"quantity\":2}"))
                .andExpect(jsonPath("$.data.quantityAvailable").value(12));
        inventory.handOutRental(branch, product, 3);
        mvc.perform(put(stockPath()).contentType("application/json").content("{\"quantityTotal\":10,\"quantityAvailable\":8}"))
                .andExpect(status().isConflict());
        mvc.perform(put(stockPath()).contentType("application/json").content("{\"quantityTotal\":12,\"quantityAvailable\":8}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.quantityRented").value(3));
        mvc.perform(patch(stockPath() + "/status").contentType("application/json").content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk());
        mvc.perform(post(stockPath() + "/import").contentType("application/json").content("{\"quantity\":1}"))
                .andExpect(status().isConflict());
        inventory.receiveRental(branch, product, 3);
        assertThat(rows.findByBranchIdAndProductId(branch, product).orElseThrow().getQuantityRented()).isZero();
    }

    @Test @WithMockUser(authorities = "ADMIN")
    void invalidStockRequestsAndOverflowAreRejected() throws Exception {
        inventory.importStock(branch, product, Integer.MAX_VALUE);
        mvc.perform(post(stockPath() + "/import").contentType("application/json").content("{\"quantity\":0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(stockPath() + "/import").contentType("application/json").content("{\"quantity\":1}"))
                .andExpect(status().isConflict());
        mvc.perform(put(stockPath()).contentType("application/json").content("{\"quantityTotal\":-1,\"quantityAvailable\":0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put(stockPath()).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        assertThat(rows.findByBranchIdAndProductId(branch, product).orElseThrow().getQuantityTotal()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void staffAccessIsLimitedToAssignedActiveBranch() throws Exception {
        User staff = account("STAFF");
        jdbc.update("INSERT INTO staff(branch_id, user_id) VALUES (?, ?)", branch, staff.getId());
        mvc.perform(post(stockPath() + "/import").with(user(staff)).contentType("application/json").content("{\"quantity\":3}"))
                .andExpect(status().isOk());
        Long other = jdbc.queryForObject("INSERT INTO branches(branch_name) VALUES ('Other') RETURNING branch_id", Long.class);
        mvc.perform(get("/api/inventory/branches/" + other).with(user(staff))).andExpect(status().isForbidden());
        jdbc.update("UPDATE staff SET status='INACTIVE' WHERE user_id=?", staff.getId());
        entityManager.clear();
        mvc.perform(get("/api/inventory/branches/" + branch).with(user(staff))).andExpect(status().isForbidden());
    }

    @Test @WithMockUser(authorities = "ADMIN")
    void stockLifecycleAndInsufficientQuantity() {
        inventory.importStock(branch, product, 10);
        inventory.handOutRental(branch, product, 4);
        inventory.fulfillSale(branch, product, 2);
        var row = rows.findByBranchIdAndProductId(branch, product).orElseThrow();
        assertThat(row.getQuantityTotal()).isEqualTo(8);
        assertThat(row.getQuantityAvailable()).isEqualTo(4);
        assertThat(row.getQuantityRented()).isEqualTo(4);
        inventory.receiveRental(branch, product, 4);
        assertThat(row.getQuantityAvailable()).isEqualTo(8);
        assertThatThrownBy(() -> inventory.receiveRental(branch, product, 1)).isInstanceOf(CatalogException.class);
    }

    @Test @WithMockUser(authorities = "ADMIN")
    void availabilityUsesPeakConcurrentReservationsAndInclusiveDates() {
        inventory.importStock(branch, product, 10);
        LocalDate day = LocalDate.now().plusDays(1);
        reservation(day, day.plusDays(1), 4, "CONFIRMED", "CONFIRMED");
        reservation(day.plusDays(2), day.plusDays(3), 5, "PENDING", "PENDING");
        reservation(day.plusDays(1), day.plusDays(2), 2, "CONFIRMED", "CONFIRMED");
        reservation(day, day.plusDays(3), 100, "CANCELLED", "CANCELLED");
        var available = inventory.availability(branch, product, day, day.plusDays(3));
        assertThat(available.peakReserved()).isEqualTo(7);
        assertThat(available.quantityRentable()).isEqualTo(3);
        assertThat(inventory.availability(branch, product, day.plusDays(4), day.plusDays(4)).quantityRentable()).isEqualTo(10);
    }

    @Test @WithMockUser(authorities = "ADMIN")
    void rentedStockIsNotSubtractedTwiceAndInactiveStockIsHidden() {
        inventory.importStock(branch, product, 10);
        inventory.handOutRental(branch, product, 3);
        LocalDate day = LocalDate.now().plusDays(1);
        reservation(day, day, 3, "IN_PROGRESS", "RENTED");
        assertThat(inventory.availability(branch, product, day, day).quantityRentable()).isEqualTo(7);
        inventory.status(branch, product, Status.INACTIVE);
        assertThat(inventory.availability(branch, product, day, day).quantityRentable()).isZero();
    }

    @Test
    void availabilityValidatesDatesAndReturnsZeroForMissingStock() throws Exception {
        LocalDate day = LocalDate.now().plusDays(1);
        mvc.perform(get("/api/inventory/availability").param("branchId", branch.toString()).param("productId", product.toString())
                .param("startDate", day.toString()).param("endDate", day.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.quantityRentable").value(0));
        mvc.perform(get("/api/inventory/availability").param("branchId", branch.toString()).param("productId", product.toString())
                .param("startDate", day.toString()).param("endDate", day.minusDays(1).toString()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/inventory/availability").param("branchId", branch.toString())).andExpect(status().isBadRequest());
    }

    @Test @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void twoConcurrentSalesCannotOversell() throws Exception {
        jdbc.update("INSERT INTO inventory(branch_id,product_id,quantity_total,quantity_available) VALUES (?,?,1,1)", branch, product);
        AtomicInteger successes = new AtomicInteger();
        parallel(() -> { try { inventory.fulfillSale(branch, product, 1); successes.incrementAndGet(); }
            catch (CatalogException expected) { assertThat(expected.status().value()).isEqualTo(409); } });
        assertThat(successes.get()).isEqualTo(1);
        var row = rows.findByBranchIdAndProductId(branch, product).orElseThrow();
        assertThat(row.getQuantityTotal()).isZero(); assertThat(row.getQuantityAvailable()).isZero();
    }

    @Test @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentFirstImportsCreateOneRowWithoutLostUpdates() throws Exception {
        parallel(() -> {
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new UsernamePasswordAuthenticationToken("admin", null, List.of(new SimpleGrantedAuthority("ADMIN"))));
            SecurityContextHolder.setContext(context);
            try { inventory.importStock(branch, product, 7); } finally { SecurityContextHolder.clearContext(); }
        });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory WHERE branch_id=? AND product_id=?", Long.class, branch, product)).isEqualTo(1);
        assertThat(rows.findByBranchIdAndProductId(branch, product).orElseThrow().getQuantityAvailable()).isEqualTo(14);
    }

    private void parallel(Runnable action) throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var barrier = new CyclicBarrier(2);
        try {
            var futures = new ArrayList<Future<?>>();
            for (int i = 0; i < 2; i++) futures.add(pool.submit(() -> {
                try { barrier.await(10, TimeUnit.SECONDS); action.run(); }
                catch (Exception exception) { throw new RuntimeException(exception); }
            }));
            for (var future : futures) future.get(20, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); pool.awaitTermination(10, TimeUnit.SECONDS); }
    }
    private User account(String role) {
        Long id = jdbc.queryForObject("INSERT INTO users(role_id,full_name,email,password) SELECT role_id,'Catalog user',?,'unused' FROM roles WHERE role_name=? RETURNING user_id",
                Long.class, UUID.randomUUID() + "@example.test", role);
        return users.findById(id).orElseThrow();
    }
    private void reservation(LocalDate start, LocalDate end, int quantity, String orderStatus, String itemStatus) {
        User customer = account("CUSTOMER");
        Long order = jdbc.queryForObject("INSERT INTO orders(user_id,branch_id,total_amount,order_status) VALUES (?,?,1,?) RETURNING order_id", Long.class, customer.getId(), branch, orderStatus);
        jdbc.update("INSERT INTO order_items(order_id,product_id,item_type,quantity,unit_price,rental_start_date,rental_end_date,status) VALUES (?,?,'RENTAL',?,1,?,?,?)",
                order, product, quantity, java.sql.Date.valueOf(start), java.sql.Date.valueOf(end), itemStatus);
    }
    private String stockPath() { return "/api/inventory/branches/" + branch + "/products/" + product; }
}
