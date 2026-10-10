package com.coc.sba_treektour.tour;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;

@SpringBootTest
@AutoConfigureMockMvc
class TourScheduleIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    private final List<Long> users = new ArrayList<>();
    private Long eventId, branchId, guideId, secondGuideId;
    private String admin, customer, other, guide;
    private OffsetDateTime start;

    /** Tạo fixture đã commit và JWT thật để kiểm thử cả giao dịch đồng thời. */
    @BeforeEach
    void setup() throws Exception {
        admin = account("ADMIN");
        customer = account("CUSTOMER");
        other = account("CUSTOMER");
        guide = account("GUIDE");
        branchId =
                jdbc.queryForObject(
                        "INSERT INTO branches(branch_name) VALUES ('Integration test') RETURNING"
                                + " branch_id",
                        Long.class);
        eventId =
                jdbc.queryForObject(
                        "INSERT INTO tour_events(event_name,event_type,base_price,status) VALUES"
                                + " ('Integration test','TREKKING',1500000,'PUBLISHED') RETURNING"
                                + " event_id",
                        Long.class);
        guideId =
                jdbc.queryForObject(
                        "INSERT INTO tour_guides(user_id,specialization) VALUES (?,'Trekking test')"
                                + " RETURNING guide_id",
                        Long.class,
                        users.get(3));
        secondGuideId =
                jdbc.queryForObject(
                        "INSERT INTO tour_guides(user_id,specialization) VALUES (?,'Camping test')"
                                + " RETURNING guide_id",
                        Long.class,
                        users.get(3));
        start = OffsetDateTime.now().plusDays(30).withNano(0);
    }

    /** Tạo tài khoản và đăng nhập qua auth controller, không mock principal. */
    private String account(String role) throws Exception {
        jdbc.update("INSERT INTO roles(role_name) VALUES (?) ON CONFLICT DO NOTHING", role);
        String email = UUID.randomUUID() + "@integration.test";
        users.add(
                jdbc.queryForObject(
                        "INSERT INTO users(role_id,full_name,email,password) SELECT"
                                + " role_id,'Integration test',?,? FROM roles WHERE role_name=?"
                                + " RETURNING user_id",
                        Long.class,
                        email,
                        passwords.encode("test-password"),
                        role));
        var result =
                call(
                        post("/api/auth/login"),
                        null,
                        "{\"email\":\"" + email + "\",\"password\":\"test-password\"}",
                        200);
        return "Bearer " + JsonPath.read(result.getResponse().getContentAsString(), "$.data.token");
    }

    /** Gửi request JSON và kiểm tra HTTP status mong đợi. */
    private MvcResult call(
            MockHttpServletRequestBuilder request, String token, String body, int status)
            throws Exception {
        if (token != null) request.header("Authorization", token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(request).andExpect(status().is(status)).andReturn();
    }

    /** Đọc ID của dữ liệu được tạo qua API. */
    private long id(MvcResult result) throws Exception {
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.data.id"))
                .longValue();
    }

    /** Tạo nội dung lịch với thời gian và guide được chỉ định. */
    private String scheduleBody(Long assignedGuide, OffsetDateTime when, int capacity) {
        return """
        {"eventId":%d,"branchId":%d,"guideId":%s,"startDatetime":"%s","endDatetime":"%s","maxParticipants":%d}
        """
                .formatted(eventId, branchId, assignedGuide, when, when.plusHours(4), capacity);
    }

    /** Tạo lịch bằng quyền ADMIN để dùng trong các bước kiểm thử tiếp theo. */
    private long schedule(int capacity) throws Exception {
        return id(call(post("/api/schedules"), admin, scheduleBody(guideId, start, capacity), 201));
    }

    /** Kiểm tra xem công khai, đăng ký JWT, giá server, quyền chủ vé, hủy và đăng ký lại. */
    @Test
    void browseRegisterCancelAndRegisterAgain() throws Exception {
        long scheduleId = schedule(1);
        call(
                get("/api/schedules").param("eventId", eventId.toString()).param("status", "OPEN"),
                null,
                null,
                200);
        call(get("/api/schedules/{id}", scheduleId), null, null, 200);
        call(post("/api/schedules/{id}/register", scheduleId), null, "{}", 401);
        call(post("/api/schedules"), customer, scheduleBody(null, start, 1), 403);
        var registered =
                call(
                        post("/api/schedules/{id}/register", scheduleId),
                        customer,
                        "{\"note\":\"Ăn chay\",\"userId\":"
                                + users.get(2)
                                + ",\"registeredPrice\":1}",
                        201);
        long ticket = id(registered);
        assertThat(
                        ((Number)
                                        JsonPath.read(
                                                registered.getResponse().getContentAsString(),
                                                "$.data.userId"))
                                .longValue())
                .isEqualTo(users.get(1));
        assertThat(
                        ((Number)
                                        JsonPath.read(
                                                registered.getResponse().getContentAsString(),
                                                "$.data.registeredPrice"))
                                .intValue())
                .isEqualTo(1500000);
        assertThat(
                        (String)
                                JsonPath.read(
                                        registered.getResponse().getContentAsString(),
                                        "$.data.participantStatus"))
                .isEqualTo("PENDING");
        call(post("/api/schedules/{id}/register", scheduleId), customer, "{}", 409);
        call(post("/api/schedules/{id}/register", scheduleId), other, "{}", 409);
        call(get("/api/participants/{id}", ticket), other, null, 403);
        call(get("/api/participants/by-user/{id}", users.get(1)), other, null, 403);
        call(post("/api/participants/{id}/cancel", ticket), other, null, 403);
        call(patch("/api/participants/{id}/confirm-payment", ticket), customer, null, 403);
        call(get("/api/participants/{id}", ticket), customer, null, 200);
        call(post("/api/participants/{id}/cancel", ticket), customer, null, 200);
        call(post("/api/participants/{id}/cancel", ticket), customer, null, 200);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT current_participants FROM event_schedules WHERE"
                                        + " schedule_id=?",
                                Integer.class,
                                scheduleId))
                .isZero();
        jdbc.update("UPDATE tour_events SET base_price=1800000 WHERE event_id=?", eventId);
        var again = call(post("/api/schedules/{id}/register", scheduleId), customer, "{}", 201);
        assertThat(id(again)).isEqualTo(ticket);
        assertThat(
                        ((Number)
                                        JsonPath.read(
                                                again.getResponse().getContentAsString(),
                                                "$.data.registeredPrice"))
                                .intValue())
                .isEqualTo(1800000);
    }

    /** Kiểm tra guide ACTIVE, trùng lịch theo userId và khoảng thời gian liền kề hợp lệ. */
    @Test
    void validatesTourGuideBranchAndScheduleOverlap() throws Exception {
        jdbc.update("UPDATE tour_events SET status='DRAFT' WHERE event_id=?", eventId);
        call(post("/api/schedules"), admin, scheduleBody(guideId, start, 2), 409);
        jdbc.update("UPDATE tour_events SET status='PUBLISHED' WHERE event_id=?", eventId);
        jdbc.update("UPDATE tour_guides SET status='INACTIVE' WHERE guide_id=?", guideId);
        call(post("/api/schedules"), admin, scheduleBody(guideId, start, 2), 409);
        jdbc.update("UPDATE tour_guides SET status='ACTIVE' WHERE guide_id=?", guideId);
        jdbc.update("UPDATE users SET status='BLOCKED' WHERE user_id=?", users.get(3));
        call(post("/api/schedules"), admin, scheduleBody(guideId, start, 2), 409);
        jdbc.update("UPDATE users SET status='ACTIVE' WHERE user_id=?", users.get(3));
        jdbc.update("UPDATE branches SET status='INACTIVE' WHERE branch_id=?", branchId);
        call(post("/api/schedules"), admin, scheduleBody(guideId, start, 2), 400);
        jdbc.update("UPDATE branches SET status='ACTIVE' WHERE branch_id=?", branchId);
        long first = schedule(2);
        call(
                post("/api/schedules"),
                admin,
                scheduleBody(secondGuideId, start.plusHours(1), 2),
                409);
        long adjacent =
                id(
                        call(
                                post("/api/schedules"),
                                admin,
                                scheduleBody(secondGuideId, start.plusHours(4), 2),
                                201));
        call(
                post("/api/schedules/{id}/reschedule", adjacent),
                admin,
                "{\"newStartDatetime\":\""
                        + start.plusHours(1)
                        + "\",\"newEndDatetime\":\""
                        + start.plusHours(5)
                        + "\",\"changeReason\":\"Trùng guide\"}",
                409);
        long unassigned =
                id(
                        call(
                                post("/api/schedules"),
                                admin,
                                scheduleBody(null, start.plusHours(1), 2),
                                201));
        call(
                put("/api/schedules/{id}", unassigned),
                admin,
                "{\"guideId\":" + guideId + ",\"maxParticipants\":2}",
                409);
        call(patch("/api/schedules/{id}/status", first), admin, "{\"status\":\"FULL\"}", 400);
        var filtered =
                call(
                        get("/api/schedules")
                                .param("eventId", eventId.toString())
                                .param("status", "CLOSED"),
                        null,
                        null,
                        200);
        assertThat((List<?>) JsonPath.read(filtered.getResponse().getContentAsString(), "$.data"))
                .isEmpty();
    }

    /** Chặn đăng ký tour lưu trữ hoặc lịch đã khởi hành; khách không đọc được lịch tour ẩn. */
    @Test
    void archivedTourAndPastDepartureRejectRegistration() throws Exception {
        long scheduleId = schedule(2);
        jdbc.update("UPDATE tour_events SET status='ARCHIVED' WHERE event_id=?", eventId);
        call(get("/api/schedules/{id}", scheduleId), null, null, 404);
        var list =
                call(get("/api/schedules").param("eventId", eventId.toString()), null, null, 200);
        assertThat((List<?>) JsonPath.read(list.getResponse().getContentAsString(), "$.data"))
                .isEmpty();
        call(get("/api/schedules/{id}", scheduleId), admin, null, 200);
        call(post("/api/schedules/{id}/register", scheduleId), customer, "{}", 409);
        jdbc.update("UPDATE tour_events SET status='PUBLISHED' WHERE event_id=?", eventId);
        jdbc.update(
                "UPDATE event_schedules SET start_datetime=now()-interval '2"
                        + " hours',end_datetime=now()+interval '2 hours' WHERE schedule_id=?",
                scheduleId);
        call(post("/api/schedules/{id}/register", scheduleId), customer, "{}", 409);
    }

    /** Kiểm tra guide được phân công, xác nhận thủ công và hoàn tất vé đã điểm danh. */
    @Test
    void assignedGuideCanCheckInAfterManagerConfirms() throws Exception {
        long scheduleId = schedule(2);
        long ticket =
                id(call(post("/api/schedules/{id}/register", scheduleId), customer, "{}", 201));
        call(get("/api/schedules/{id}/participants", scheduleId), customer, null, 403);
        call(get("/api/schedules/{id}/participants", scheduleId), guide, null, 200);
        call(patch("/api/participants/{id}/check-in", ticket), guide, null, 400);
        call(patch("/api/participants/{id}/confirm-payment", ticket), admin, null, 200);
        jdbc.update("UPDATE tour_guides SET user_id=? WHERE guide_id=?", users.get(0), guideId);
        call(patch("/api/participants/{id}/check-in", ticket), guide, null, 403);
        jdbc.update("UPDATE tour_guides SET user_id=? WHERE guide_id=?", users.get(3), guideId);
        call(patch("/api/participants/{id}/check-in", ticket), guide, null, 200);
        call(patch("/api/participants/{id}/no-show", ticket), guide, null, 400);
        call(
                patch("/api/schedules/{id}/status", scheduleId),
                admin,
                "{\"status\":\"COMPLETED\"}",
                200);
        var completed = call(get("/api/participants/{id}", ticket), customer, null, 200);
        assertThat(
                        (String)
                                JsonPath.read(
                                        completed.getResponse().getContentAsString(),
                                        "$.data.participantStatus"))
                .isEqualTo("COMPLETED");
        call(post("/api/participants/{id}/cancel", ticket), customer, null, 400);
        call(patch("/api/schedules/{id}/status", scheduleId), admin, "{\"status\":\"OPEN\"}", 400);
    }

    /**
     * Dời lịch lưu history; guide không được đọc history và tài khoản bị khóa mất quyền đăng ký.
     */
    @Test
    void reschedulePersistsHistoryAndBlockedJwtIsRejected() throws Exception {
        long scheduleId = schedule(2);
        call(
                post("/api/schedules/{id}/reschedule", scheduleId),
                admin,
                "{\"newStartDatetime\":\""
                        + start.plusDays(1)
                        + "\",\"newEndDatetime\":\""
                        + start.plusDays(1).plusHours(4)
                        + "\",\"changeReason\":\"Đổi ngày khởi hành\"}",
                200);
        var history = call(get("/api/schedules/{id}/histories", scheduleId), admin, null, 200);
        assertThat((List<?>) JsonPath.read(history.getResponse().getContentAsString(), "$.data"))
                .hasSize(1);
        call(get("/api/schedules/{id}/histories", scheduleId), guide, null, 403);
        jdbc.update("UPDATE users SET status='BLOCKED' WHERE user_id=?", users.get(1));
        call(post("/api/schedules/{id}/register", scheduleId), customer, "{}", 401);
    }

    /** Cùng người đăng ký đồng thời chỉ có một vé; hủy đồng thời không trừ chỗ của người khác. */
    @Test
    void concurrentDuplicateRegistrationAndCancellationAreIdempotent() throws Exception {
        long scheduleId = schedule(2);
        var results =
                race(
                        () ->
                                mvc.perform(
                                                post("/api/schedules/{id}/register", scheduleId)
                                                        .header("Authorization", customer)
                                                        .contentType(MediaType.APPLICATION_JSON)
                                                        .content("{}"))
                                        .andReturn()
                                        .getResponse()
                                        .getStatus(),
                        () ->
                                mvc.perform(
                                                post("/api/schedules/{id}/register", scheduleId)
                                                        .header("Authorization", customer)
                                                        .contentType(MediaType.APPLICATION_JSON)
                                                        .content("{}"))
                                        .andReturn()
                                        .getResponse()
                                        .getStatus());
        assertThat(results).containsExactlyInAnyOrder(201, 409);
        call(post("/api/schedules/{id}/register", scheduleId), other, "{}", 201);
        long ticket =
                jdbc.queryForObject(
                        "SELECT participant_id FROM event_participants WHERE schedule_id=? AND"
                            + " user_id=?",
                        Long.class,
                        scheduleId,
                        users.get(1));
        assertThat(
                        race(
                                () ->
                                        mvc.perform(
                                                        post(
                                                                        "/api/participants/{id}/cancel",
                                                                        ticket)
                                                                .header("Authorization", customer))
                                                .andReturn()
                                                .getResponse()
                                                .getStatus(),
                                () ->
                                        mvc.perform(
                                                        post(
                                                                        "/api/participants/{id}/cancel",
                                                                        ticket)
                                                                .header("Authorization", customer))
                                                .andReturn()
                                                .getResponse()
                                                .getStatus()))
                .containsExactly(200, 200);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT current_participants FROM event_schedules WHERE"
                                    + " schedule_id=?",
                                Integer.class,
                                scheduleId))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM event_schedules WHERE schedule_id=?",
                                String.class,
                                scheduleId))
                .isEqualTo("OPEN");
    }

    /** Hai request tranh chỗ cuối chỉ được tạo đúng một vé và không vượt sức chứa. */
    @Test
    void concurrentRegistrationsDoNotOversell() throws Exception {
        long scheduleId = schedule(1);
        List<Integer> results =
                race(
                        () ->
                                mvc.perform(
                                                post("/api/schedules/{id}/register", scheduleId)
                                                        .header("Authorization", customer)
                                                        .contentType(MediaType.APPLICATION_JSON)
                                                        .content("{}"))
                                        .andReturn()
                                        .getResponse()
                                        .getStatus(),
                        () ->
                                mvc.perform(
                                                post("/api/schedules/{id}/register", scheduleId)
                                                        .header("Authorization", other)
                                                        .contentType(MediaType.APPLICATION_JSON)
                                                        .content("{}"))
                                        .andReturn()
                                        .getResponse()
                                        .getStatus());
        assertThat(results).containsExactlyInAnyOrder(201, 409);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT current_participants FROM event_schedules WHERE"
                                        + " schedule_id=?",
                                Integer.class,
                                scheduleId))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM event_participants WHERE schedule_id=?",
                                Integer.class,
                                scheduleId))
                .isEqualTo(1);
    }

    /** Hai request tạo lịch trùng cho hai guideId cùng người chỉ thành công một request. */
    @Test
    void concurrentGuideAssignmentsDoNotOverlap() throws Exception {
        List<Integer> results =
                race(
                        () ->
                                mvc.perform(
                                                post("/api/schedules")
                                                        .header("Authorization", admin)
                                                        .contentType(MediaType.APPLICATION_JSON)
                                                        .content(scheduleBody(guideId, start, 2)))
                                        .andReturn()
                                        .getResponse()
                                        .getStatus(),
                        () ->
                                mvc.perform(
                                                post("/api/schedules")
                                                        .header("Authorization", admin)
                                                        .contentType(MediaType.APPLICATION_JSON)
                                                        .content(
                                                                scheduleBody(
                                                                        secondGuideId, start, 2)))
                                        .andReturn()
                                        .getResponse()
                                        .getStatus());
        assertThat(results).containsExactlyInAnyOrder(201, 409);
    }

    /** Khởi chạy hai request đồng thời, giới hạn thời gian để phát hiện deadlock. */
    private List<Integer> race(Callable<Integer> first, Callable<Integer> second) throws Exception {
        var ready = new CountDownLatch(2);
        var go = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var futures = new ArrayList<Future<Integer>>();
            for (var task : List.of(first, second))
                futures.add(
                        pool.submit(
                                () -> {
                                    ready.countDown();
                                    if (!go.await(10, TimeUnit.SECONDS))
                                        throw new IllegalStateException("Timeout");
                                    return task.call();
                                }));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            return List.of(
                    futures.get(0).get(20, TimeUnit.SECONDS),
                    futures.get(1).get(20, TimeUnit.SECONDS));
        }
    }

    /** Xóa đúng dữ liệu fixture của test; không thay đổi dữ liệu có sẵn. */
    @AfterEach
    void cleanup() {
        if (eventId != null) {
            jdbc.update(
                    "DELETE FROM event_participants WHERE schedule_id IN (SELECT schedule_id FROM"
                            + " event_schedules WHERE event_id=?)",
                    eventId);
            jdbc.update(
                    "DELETE FROM event_schedule_history WHERE schedule_id IN (SELECT schedule_id"
                            + " FROM event_schedules WHERE event_id=?)",
                    eventId);
            jdbc.update("DELETE FROM event_schedules WHERE event_id=?", eventId);
            jdbc.update("DELETE FROM tour_events WHERE event_id=?", eventId);
        }
        for (Long user : users) jdbc.update("DELETE FROM tour_guides WHERE user_id=?", user);
        if (branchId != null) jdbc.update("DELETE FROM branches WHERE branch_id=?", branchId);
        for (Long user : users) jdbc.update("DELETE FROM users WHERE user_id=?", user);
    }
}
