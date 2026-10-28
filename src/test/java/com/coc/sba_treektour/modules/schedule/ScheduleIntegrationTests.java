package com.coc.sba_treektour.modules.schedule;

import com.coc.sba_treektour.modules.account.repository.UserRepository;
import com.coc.sba_treektour.modules.schedule.exception.ScheduleConflictException;
import com.coc.sba_treektour.modules.schedule.service.HoldExpiryService;
import com.coc.sba_treektour.modules.schedule.service.ParticipantPaymentService;
import com.coc.sba_treektour.modules.schedule.service.ScheduleService;
import com.jayway.jsonpath.JsonPath;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "schedule.expiry.enabled=false")
@AutoConfigureMockMvc
class ScheduleIntegrationTests {
    @Autowired HoldExpiryService expiry;
    @Autowired ParticipantPaymentService paymentService;
    @Autowired ScheduleService scheduleService;
    @Autowired UserRepository accounts;
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

    @Test
    void guideAssignmentsAreScopedEnrichedAndPaginated() throws Exception {
        long first = schedule(4);
        long second = id(call(post("/api/schedules"), admin,
                scheduleBody(secondGuideId, start.plusDays(1), 4), 201));
        id(call(post("/api/schedules"), admin, scheduleBody(null, start.plusDays(2), 4), 201));
        jdbc.update("UPDATE tour_events SET status='ARCHIVED' WHERE event_id=?", eventId);
        var list = call(get("/api/schedules/my-assignments").param("size", "1"), guide, null, 200);
        assertThat(JsonPath.<List<Integer>>read(list.getResponse().getContentAsString(), "$.data[*].id"))
                .containsExactly((int) first);
        var next = call(get("/api/schedules/my-assignments").param("size", "1").param("page", "1"),
                guide, null, 200);
        assertThat(JsonPath.<List<Integer>>read(next.getResponse().getContentAsString(), "$.data[*].id"))
                .containsExactly((int) second);
        var detail = call(get("/api/schedules/my-assignments/" + first), guide, null, 200);
        assertThat(JsonPath.<String>read(detail.getResponse().getContentAsString(), "$.data.eventName"))
                .isEqualTo("Integration test");
        assertThat(JsonPath.<String>read(detail.getResponse().getContentAsString(), "$.data.branchName"))
                .isEqualTo("Integration test");
        var filtered = call(get("/api/schedules/my-assignments").param("status", "COMPLETED"),
                guide, null, 200);
        assertThat(JsonPath.<List<?>>read(filtered.getResponse().getContentAsString(), "$.data")).isEmpty();
        call(get("/api/schedules/my-assignments").param("size", "101"), guide, null, 400);
        call(get("/api/schedules/my-assignments").param("page", "-1"), guide, null, 400);
    }

    @Test
    void guideAssignmentsRejectOtherRolesAndRevokeImmediately() throws Exception {
        long assigned = schedule(4);
        String anotherGuide = account("GUIDE");
        var empty = call(get("/api/schedules/my-assignments"), anotherGuide, null, 200);
        assertThat(JsonPath.<List<?>>read(empty.getResponse().getContentAsString(), "$.data")).isEmpty();
        call(get("/api/schedules/my-assignments/" + assigned), anotherGuide, null, 403);
        call(get("/api/schedules/my-assignments"), null, null, 401);
        call(get("/api/schedules/my-assignments"), customer, null, 403);
        call(get("/api/schedules/my-assignments"), admin, null, 403);
        call(get("/api/schedules/my-assignments/" + assigned), customer, null, 403);
        call(put("/api/schedules/" + assigned), admin,
                "{\"guideId\":null,\"maxParticipants\":4}", 200);
        call(get("/api/schedules/my-assignments/" + assigned), guide, null, 403);
        call(get("/api/schedules/" + assigned + "/participants"), guide, null, 403);
        var revoked = call(get("/api/schedules/my-assignments"), guide, null, 200);
        assertThat(JsonPath.<List<?>>read(revoked.getResponse().getContentAsString(), "$.data")).isEmpty();
    }

    @Test
    void inactiveGuideProfileIsNotAnAssignment() throws Exception {
        long assigned = schedule(4);
        jdbc.update("UPDATE tour_guides SET status='INACTIVE' WHERE guide_id=?", guideId);
        var list = call(get("/api/schedules/my-assignments"), guide, null, 200);
        assertThat(JsonPath.<List<?>>read(list.getResponse().getContentAsString(), "$.data")).isEmpty();
        call(get("/api/schedules/my-assignments/" + assigned), guide, null, 403);
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
                        + " hours',end_datetime=now()+interval '2 hours',registration_closes_at=now()-interval '2 hours' WHERE schedule_id=?",
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
        confirm(ticket);
        changeStatus(scheduleId, "CLOSED", 200);
        jdbc.update("UPDATE event_schedules SET start_datetime=now()-interval '1 minute',end_datetime=now()+interval '2 hours',registration_closes_at=now()-interval '1 minute' WHERE schedule_id=?", scheduleId);
        jdbc.update("UPDATE tour_guides SET user_id=? WHERE guide_id=?", users.get(0), guideId);
        call(patch("/api/participants/{id}/check-in", ticket), guide, null, 403);
        jdbc.update("UPDATE tour_guides SET user_id=? WHERE guide_id=?", users.get(3), guideId);
        call(patch("/api/participants/{id}/check-in", ticket), guide, null, 200);
        call(patch("/api/participants/{id}/no-show", ticket), guide, null, 400);
        changeStatus(scheduleId, "IN_PROGRESS", 200);
        jdbc.update("UPDATE event_schedules SET start_datetime=now()-interval '4 hours',end_datetime=now()-interval '1 hour',registration_closes_at=now()-interval '4 hours' WHERE schedule_id=?", scheduleId);
        changeStatus(scheduleId, "COMPLETED", 200);
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
                .hasSize(2);
        call(get("/api/schedules/{id}/histories", scheduleId), guide, null, 200);
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
            jdbc.update("DELETE FROM schedule_payment_reviews WHERE participant_id IN (SELECT p.participant_id FROM event_participants p JOIN event_schedules s ON s.schedule_id=p.schedule_id WHERE s.event_id=?)", eventId);
            jdbc.update("DELETE FROM schedule_participant_history WHERE participant_id IN (SELECT p.participant_id FROM event_participants p JOIN event_schedules s ON s.schedule_id=p.schedule_id WHERE s.event_id=?)", eventId);
            jdbc.update("DELETE FROM transactions WHERE participant_id IN (SELECT p.participant_id FROM event_participants p JOIN event_schedules s ON s.schedule_id=p.schedule_id WHERE s.event_id=?)", eventId);
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
        for (Long user : users) jdbc.update("DELETE FROM staff WHERE user_id=?", user);
        if (branchId != null) jdbc.update("DELETE FROM branches WHERE branch_id=?", branchId);
        for (Long branch : extraBranches) jdbc.update("DELETE FROM branches WHERE branch_id=?", branch);
        for (Long user : users) jdbc.update("DELETE FROM users WHERE user_id=?", user);
    }

    private final List<Long> extraBranches = new ArrayList<>();

    @Test
    void attendanceWindowAndCompletionPreserveNoShowAndRepeatedPayment() throws Exception {
        long schedule = schedule(2);
        long present = id(call(post("/api/schedules/{id}/register", schedule), customer, "{}", 201));
        long absent = id(call(post("/api/schedules/{id}/register", schedule), other, "{}", 201));
        long transaction = transaction(present, "SUCCEEDED", 1500000);
        var reference = registrationReference(present);
        assertThat(paymentConfirm(present, reference, transaction)).isEqualTo("CONFIRMED");
        confirm(absent);
        changeStatus(schedule, "CLOSED", 200);
        call(patch("/api/participants/{id}/check-in", present), guide, null, 400);
        call(patch("/api/participants/{id}/no-show", absent), guide, null, 400);
        jdbc.update("UPDATE event_schedules SET start_datetime=now()-interval '1 minute',end_datetime=now()+interval '2 hours',registration_closes_at=now()-interval '1 minute' WHERE schedule_id=?", schedule);
        call(patch("/api/participants/{id}/check-in", present), guide, null, 200);
        call(patch("/api/participants/{id}/check-in", present), guide, null, 200);
        call(post("/api/participants/{id}/cancel", present), customer, null, 400);
        jdbc.update("UPDATE event_schedules SET start_datetime=now()-interval '45 minutes',registration_closes_at=now()-interval '45 minutes' WHERE schedule_id=?", schedule);
        call(patch("/api/participants/{id}/no-show", absent), guide, null, 200);
        call(patch("/api/participants/{id}/no-show", absent), guide, null, 200);
        changeStatus(schedule, "IN_PROGRESS", 200);
        changeStatus(schedule, "COMPLETED", 400);
        jdbc.update("UPDATE event_schedules SET start_datetime=now()-interval '4 hours',end_datetime=now()-interval '1 hour',registration_closes_at=now()-interval '4 hours' WHERE schedule_id=?", schedule);
        changeStatus(schedule, "COMPLETED", 200);
        assertThat(jdbc.queryForObject("SELECT participant_status FROM event_participants WHERE participant_id=?", String.class, present)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT participant_status FROM event_participants WHERE participant_id=?", String.class, absent)).isEqualTo("NO_SHOW");
        assertThat(paymentConfirm(present, reference, transaction)).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM schedule_payment_reviews WHERE participant_id=?", Integer.class, present)).isZero();
    }

    @Test
    void wrongPaymentAmountAndReferenceRequireReviewWithoutConfirming() throws Exception {
        long schedule = schedule(2);
        long ticket = id(call(post("/api/schedules/{id}/register", schedule), customer, "{}", 201));
        long wrongAmount = transaction(ticket, "SUCCEEDED", 100);
        assertThat(paymentConfirm(ticket, registrationReference(ticket), wrongAmount)).isEqualTo("REVIEW_REQUIRED");
        long wrongAttempt = transaction(ticket, "SUCCEEDED", 1500000);
        assertThat(paymentConfirm(ticket, UUID.randomUUID(), wrongAttempt)).isEqualTo("REVIEW_REQUIRED");
        assertThat(jdbc.queryForObject("SELECT participant_status FROM event_participants WHERE participant_id=?", String.class, ticket)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT amount FROM schedule_payment_reviews WHERE transaction_id=?", Integer.class, wrongAmount)).isEqualTo(100);
        // A reviewed transaction needs reconciliation; retrying it must not auto-confirm/refund twice.
        assertThat(paymentConfirm(ticket, registrationReference(ticket), wrongAttempt)).isEqualTo("REVIEW_REQUIRED");
    }

    @Test
    void expiryAndCancellationRaceReleasesOneSeatAndClosesDueSchedule() throws Exception {
        long schedule = schedule(1);
        long ticket = id(call(post("/api/schedules/{id}/register", schedule), customer, "{}", 201));
        jdbc.update("UPDATE event_participants SET expires_at=now()-interval '1 second' WHERE participant_id=?", ticket);
        assertThat(race(() -> { expiry.expireSchedule(schedule); return 200; },
                () -> mvc.perform(post("/api/participants/{id}/cancel", ticket).header("Authorization", customer)).andReturn().getResponse().getStatus()))
                .containsExactly(200, 200);
        assertThat(jdbc.queryForObject("SELECT current_participants FROM event_schedules WHERE schedule_id=?", Integer.class, schedule)).isZero();
        jdbc.update("UPDATE event_schedules SET registration_closes_at=now()-interval '1 minute' WHERE schedule_id=?", schedule);
        expiry.expireSchedule(schedule);
        expiry.expireSchedule(schedule);
        assertThat(jdbc.queryForObject("SELECT status FROM event_schedules WHERE schedule_id=?", String.class, schedule)).isEqualTo("CLOSED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM event_schedule_history WHERE schedule_id=? AND new_status='CLOSED'", Integer.class, schedule)).isEqualTo(1);
    }

    @Test
    void earlierRescheduleShortensHoldsAndHistoryKeepsTheActor() throws Exception {
        long schedule = schedule(2);
        long ticket = id(call(post("/api/schedules/{id}/register", schedule), customer, "{}", 201));
        var cutoff = OffsetDateTime.now().plusMinutes(5).withNano(0);
        call(post("/api/schedules/{id}/reschedule", schedule), admin,
                "{\"newStartDatetime\":\"" + start.plusDays(1) + "\",\"newEndDatetime\":\"" + start.plusDays(1).plusHours(4)
                        + "\",\"registrationClosesAt\":\"" + cutoff + "\",\"changeReason\":\"Điều chỉnh thời hạn\"}", 200);
        assertThat(jdbc.queryForObject("SELECT expires_at FROM event_participants WHERE participant_id=?", OffsetDateTime.class, ticket).toInstant()).isEqualTo(cutoff.toInstant());
        assertThat(jdbc.queryForObject("SELECT changed_by FROM event_schedule_history WHERE schedule_id=? ORDER BY history_id DESC LIMIT 1", Long.class, schedule)).isEqualTo(users.getFirst());
        call(get("/api/participants/{id}/histories", ticket), other, null, 403);
        call(get("/api/participants/{id}/histories", ticket), customer, null, 200);
        call(get("/api/schedules/{id}/participants", schedule).param("size", "1"), guide, null, 200);
    }

    @Test
    void staffMustBelongToTheActiveOrganizingBranch() throws Exception {
        String staff = account("STAFF");
        long staffId = users.getLast();
        jdbc.update("INSERT INTO staff(user_id,branch_id) VALUES (?,?)", staffId, branchId);
        long own = schedule(2);
        long ticket = id(call(post("/api/schedules/{id}/register", own), customer, "{}", 201));
        long foreign = jdbc.queryForObject("INSERT INTO branches(branch_name) VALUES ('Other test branch') RETURNING branch_id", Long.class);
        extraBranches.add(foreign);
        long another = id(call(post("/api/schedules"), admin, scheduleBody(null, start.plusDays(2), 2), 201));
        jdbc.update("UPDATE event_schedules SET branch_id=? WHERE schedule_id=?", foreign, another);
        long otherTicket = id(call(post("/api/schedules/{id}/register", another), other, "{}", 201));
        call(get("/api/schedules/{id}/participants", own), staff, null, 200);
        call(get("/api/schedules/{id}/participants", another), staff, null, 403);
        call(get("/api/schedules/{id}/histories", another), staff, null, 403);
        call(get("/api/participants/{id}", otherTicket), staff, null, 403);
        call(post("/api/participants/{id}/cancel", otherTicket), staff, "{\"reason\":\"Hủy thay khách\"}", 403);
        call(patch("/api/participants/{id}/confirm-payment", otherTicket), staff, confirmationBody(otherTicket), 403);
        call(patch("/api/participants/{id}/check-in", otherTicket), staff, null, 403);
        call(patch("/api/participants/{id}/no-show", otherTicket), staff, null, 403);
        call(put("/api/schedules/{id}", another), staff, "{\"maxParticipants\":3}", 403);
        call(patch("/api/schedules/{id}/status", another), staff, "{\"status\":\"CLOSED\",\"changeReason\":\"Close\"}", 403);
        call(post("/api/schedules/{id}/reschedule", another), staff,
                "{\"newStartDatetime\":\"" + start.plusDays(3) + "\",\"newEndDatetime\":\"" + start.plusDays(3).plusHours(4)
                        + "\",\"changeReason\":\"Move\"}", 403);
        var foreignBody = scheduleBody(null, start.plusDays(4), 2).replace("\"branchId\":" + branchId, "\"branchId\":" + foreign);
        call(post("/api/schedules"), staff, foreignBody, 403);
        call(post("/api/participants/{id}/cancel", ticket), staff, null, 400);
        call(post("/api/participants/{id}/cancel", ticket), staff, "{\"reason\":\"Khách yêu cầu\"}", 200);
        jdbc.update("UPDATE staff SET status='INACTIVE' WHERE user_id=?", staffId);
        call(get("/api/schedules/{id}/participants", own), staff, null, 403);
        jdbc.update("UPDATE staff SET status='ACTIVE' WHERE user_id=?", staffId);
        jdbc.update("UPDATE branches SET status='INACTIVE' WHERE branch_id=?", branchId);
        call(get("/api/schedules/{id}/participants", own), staff, null, 403);
    }

    @Test
    void publishedSchedulesArePublicButHiddenSchedulesAreBranchScoped() throws Exception {
        String staff = account("STAFF");
        long staffId = users.getLast();
        long schedule = schedule(2);
        jdbc.update("UPDATE tour_events SET status='ARCHIVED' WHERE event_id=?", eventId);
        call(get("/api/schedules/{id}", schedule), staff, null, 404);
        var empty = call(get("/api/schedules").param("eventId", eventId.toString()), staff, null, 200);
        assertThat((List<?>) JsonPath.read(empty.getResponse().getContentAsString(), "$.data")).isEmpty();
        jdbc.update("INSERT INTO staff(user_id,branch_id) VALUES (?,?)", staffId, branchId);
        call(get("/api/schedules/{id}", schedule), staff, null, 200);
        var visible = call(get("/api/schedules").param("eventId", eventId.toString()), staff, null, 200);
        assertThat((List<?>) JsonPath.read(visible.getResponse().getContentAsString(), "$.data")).hasSize(1);
    }

    @Test
    void fullIsDerivedAndLifecycleCannotSkipAheadOrReopenAfterCutoff() throws Exception {
        long schedule = schedule(1);
        changeStatus(schedule, "FULL", 400);
        changeStatus(schedule, "COMPLETED", 400);
        changeStatus(schedule, "IN_PROGRESS", 400);
        call(post("/api/schedules/{id}/register", schedule), admin, "{}", 403);
        call(post("/api/schedules/{id}/register", schedule), guide, "{}", 403);
        call(post("/api/schedules/{id}/register", schedule), customer, "{}", 201);
        changeStatus(schedule, "CLOSED", 200);
        changeStatus(schedule, "OPEN", 200);
        assertThat(jdbc.queryForObject("SELECT status FROM event_schedules WHERE schedule_id=?", String.class, schedule)).isEqualTo("FULL");
        changeStatus(schedule, "CLOSED", 200);
        jdbc.update("UPDATE event_schedules SET registration_closes_at=now()-interval '1 minute' WHERE schedule_id=?", schedule);
        changeStatus(schedule, "OPEN", 400);
    }

    @Test
    void holdsExpireOnceAndReregistrationRejectsOldManualConfirmationReference() throws Exception {
        long schedule = schedule(1);
        long ticket = id(call(post("/api/schedules/{id}/register", schedule), customer, "{}", 201));
        String oldConfirmation = confirmationBody(ticket);
        jdbc.update("UPDATE event_participants SET expires_at=now()-interval '1 second' WHERE participant_id=?", ticket);
        expiry.expireSchedule(schedule);
        expiry.expireSchedule(schedule);
        assertThat(jdbc.queryForObject("SELECT current_participants FROM event_schedules WHERE schedule_id=?", Integer.class, schedule)).isZero();
        assertThat(jdbc.queryForObject("SELECT participant_status FROM event_participants WHERE participant_id=?", String.class, ticket)).isEqualTo("EXPIRED");
        assertThat(id(call(post("/api/schedules/{id}/register", schedule), customer, "{}", 201))).isEqualTo(ticket);
        call(patch("/api/participants/{id}/confirm-payment", ticket), admin, oldConfirmation, 409);
        confirm(ticket);
        confirm(ticket);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM schedule_participant_history WHERE participant_id=? AND action='MANUAL_CONFIRM'", Integer.class, ticket)).isEqualTo(1);
        call(patch("/api/participants/{id}/check-in", ticket), guide, null, 400);
        call(patch("/api/participants/{id}/no-show", ticket), guide, null, 400);
    }

    @Test
    void cancellingScheduleCancelsTicketsAndQueuesManualRefundReview() throws Exception {
        long schedule = schedule(2);
        long ticket = id(call(post("/api/schedules/{id}/register", schedule), customer, "{}", 201));
        call(post("/api/schedules/{id}/register", schedule), other, "{}", 201);
        confirm(ticket);
        changeStatus(schedule, "CANCELLED", 200);
        changeStatus(schedule, "CANCELLED", 200);
        assertThat(jdbc.queryForObject("SELECT current_participants FROM event_schedules WHERE schedule_id=?", Integer.class, schedule)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM event_participants WHERE schedule_id=? AND participant_status='CANCELLED'", Integer.class, schedule)).isEqualTo(2);
        call(patch("/api/participants/{id}/confirm-payment", ticket), admin, confirmationBody(ticket), 409);
        call(patch("/api/participants/{id}/check-in", ticket), guide, null, 400);
        call(patch("/api/participants/{id}/no-show", ticket), guide, null, 400);
        var reviews = call(get("/api/participants/payment-reviews").param("branchId", branchId.toString()), admin, null, 200);
        assertThat((List<?>) JsonPath.read(reviews.getResponse().getContentAsString(), "$.data")).hasSize(1);
        call(get("/api/participants/payment-reviews"), customer, null, 403);
    }

    @Test
    void callbackValidatesPersistedTransactionAndIsIdempotent() throws Exception {
        long schedule = schedule(1);
        long ticket = id(call(post("/api/schedules/{id}/register", schedule), customer, "{}", 201));
        long transaction = transaction(ticket, "PENDING", 1500000);
        var reference = registrationReference(ticket);
        Assertions.assertThatThrownBy(() -> paymentConfirm(ticket, reference, transaction))
                .isInstanceOf(ScheduleConflictException.class);
        jdbc.update("UPDATE transactions SET status='SUCCEEDED' WHERE transaction_id=?", transaction);
        assertThat(paymentConfirm(ticket, reference, transaction)).isEqualTo("CONFIRMED");
        assertThat(paymentConfirm(ticket, reference, transaction)).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM schedule_participant_history WHERE participant_id=? AND action='PAYMENT_CONFIRM'", Integer.class, ticket)).isEqualTo(1);
        call(post("/api/participants/{id}/cancel", ticket), customer, null, 200);
        assertThat(paymentConfirm(ticket, reference, transaction)).isEqualTo("REVIEW_REQUIRED");
        call(post("/api/schedules/{id}/register", schedule), customer, "{}", 409);
    }

    @Test
    void lateOrWrongAttemptPaymentNeverResurrectsAnExpiredTicket() throws Exception {
        long schedule = schedule(1);
        long ticket = id(call(post("/api/schedules/{id}/register", schedule), customer, "{}", 201));
        var reference = registrationReference(ticket);
        long transaction = transaction(ticket, "SUCCEEDED", 1500000);
        jdbc.update("UPDATE event_participants SET expires_at=now()-interval '1 second' WHERE participant_id=?", ticket);
        assertThat(paymentConfirm(ticket, reference, transaction)).isEqualTo("REVIEW_REQUIRED");
        assertThat(paymentConfirm(ticket, reference, transaction)).isEqualTo("REVIEW_REQUIRED");
        assertThat(jdbc.queryForObject("SELECT participant_status FROM event_participants WHERE participant_id=?", String.class, ticket)).isEqualTo("EXPIRED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM schedule_payment_reviews WHERE transaction_id=?", Integer.class, transaction)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT current_participants FROM event_schedules WHERE schedule_id=?", Integer.class, schedule)).isZero();
    }

    @Test
    void cancellationAndPaymentRaceKeepsCapacityAndReviewConsistent() throws Exception {
        long schedule = schedule(1);
        long ticket = id(call(post("/api/schedules/{id}/register", schedule), customer, "{}", 201));
        var reference = registrationReference(ticket);
        long transaction = transaction(ticket, "SUCCEEDED", 1500000);
        race(() -> { paymentConfirm(ticket, reference, transaction); return 200; },
                () -> mvc.perform(post("/api/participants/{id}/cancel", ticket).header("Authorization", customer)).andReturn().getResponse().getStatus());
        assertThat(jdbc.queryForObject("SELECT participant_status FROM event_participants WHERE participant_id=?", String.class, ticket)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT current_participants FROM event_schedules WHERE schedule_id=?", Integer.class, schedule)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM schedule_payment_reviews WHERE transaction_id=?", Integer.class, transaction)).isEqualTo(1);
    }

    @Test
    void invalidPaginationAndMalformedBodiesReturn400AndServiceCallsAreProtected() throws Exception {
        call(get("/api/schedules").param("size", "101"), null, null, 400);
        call(get("/api/schedules").param("page", "-1"), null, null, 400);
        call(get("/api/schedules").param("status", "INVALID"), null, null, 400);
        call(post("/api/schedules"), admin, "{", 400);
        var user = accounts.findById(users.get(1)).orElseThrow();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        try {
            Assertions.assertThatThrownBy(() -> scheduleService.create(null))
                    .isInstanceOf(AccessDeniedException.class);
            Assertions.assertThatThrownBy(() -> paymentService.confirm(1L, UUID.randomUUID(), 1L))
                    .isInstanceOf(AccessDeniedException.class);
        } finally { SecurityContextHolder.clearContext(); }
    }

    private UUID registrationReference(long ticket) {
        return jdbc.queryForObject("SELECT registration_reference FROM event_participants WHERE participant_id=?", UUID.class, ticket);
    }

    private long transaction(long ticket, String status, int amount) {
        return jdbc.queryForObject("INSERT INTO transactions(participant_id,transaction_type,amount,payment_method,status) VALUES (?,'PAYMENT',?,'ZALOPAY',?) RETURNING transaction_id", Long.class, ticket, amount, status);
    }

    private String paymentConfirm(long ticket, UUID reference, long transaction) {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken("internal-payment", null,
                List.of(new SimpleGrantedAuthority("SCHEDULE_PAYMENT"))));
        SecurityContextHolder.setContext(context);
        try { return paymentService.confirm(ticket, reference, transaction).name(); }
        finally { SecurityContextHolder.clearContext(); }
    }

    private void changeStatus(long id, String state, int expected) throws Exception {
        call(patch("/api/schedules/{id}/status", id), admin,
                "{\"status\":\"" + state + "\",\"changeReason\":\"Kiểm thử nghiệp vụ\"}", expected);
    }

    private String confirmationBody(long ticket) {
        var reference = jdbc.queryForObject("SELECT registration_reference FROM event_participants WHERE participant_id=?", UUID.class, ticket);
        return "{\"registrationReference\":\"" + reference + "\",\"paymentReference\":\"RECEIPT-" + ticket + "\"}";
    }

    private void confirm(long ticket) throws Exception {
        call(patch("/api/participants/{id}/confirm-payment", ticket), admin, confirmationBody(ticket), 200);
    }
}
