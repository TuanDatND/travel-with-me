package com.coc.sba_treektour.tour;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@SpringBootTest
@AutoConfigureMockMvc
class TourJwtIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    private final List<Long> userIds = new ArrayList<>();
    private Long eventId;

    /** Tạo tài khoản đã commit để kiểm tra JWT thực tế ngoài transaction của test. */
    private String account(String role) {
        jdbc.update(
                "INSERT INTO roles(role_name) VALUES (?) ON CONFLICT (role_name) DO NOTHING", role);
        String email = UUID.randomUUID() + "@example.test";
        userIds.add(
                jdbc.queryForObject(
                        "INSERT INTO users(role_id,full_name,email,password,status) SELECT"
                            + " role_id,?,?,?,'ACTIVE' FROM roles WHERE role_name=? RETURNING"
                            + " user_id",
                        Long.class,
                        "Tour JWT test",
                        email,
                        passwords.encode("test-password"),
                        role));
        return email;
    }

    /** Đăng nhập qua controller thật của member 1 và lấy Bearer token. */
    private String login(String email) throws Exception {
        var result =
                mvc.perform(
                                post("/api/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"email\":\""
                                                        + email
                                                        + "\",\"password\":\"test-password\"}"))
                        .andExpect(status().isOk())
                        .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.token");
    }

    /** Kiểm tra quyền tour, tạo guide, tài khoản bị khóa và token sai mà không dùng mock user. */
    @Test
    void realLoginTokensAuthorizeTourWithoutSessionOrCsrf() throws Exception {
        String adminEmail = account("ROLE_ADMIN");
        String customerEmail = account("ROLE_USER");
        String admin = "Bearer " + login(adminEmail);
        String customer = "Bearer " + login(customerEmail);
        mvc.perform(get("/api/events")).andExpect(status().isOk());
        mvc.perform(get("/api/admin/events")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/events").header("Authorization", customer))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/events").header("Authorization", admin))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/events").with(httpBasic(adminEmail, "test-password")))
                .andExpect(status().isUnauthorized());
        var created =
                mvc.perform(
                                post("/api/admin/events")
                                        .header("Authorization", admin)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"eventName\":\"JWT test"
                                                    + " tour\",\"eventType\":\"TREKKING\",\"basePrice\":100000}"))
                        .andExpect(status().isCreated())
                        .andReturn();
        eventId =
                ((Number)
                                JsonPath.read(
                                        created.getResponse().getContentAsString(),
                                        "$.data.eventId"))
                        .longValue();
        mvc.perform(get("/api/events/{id}", eventId)).andExpect(status().isNotFound());
        mvc.perform(
                        post("/api/admin/guides")
                                .header("Authorization", admin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"userId\":"
                                                + userIds.get(1)
                                                + ",\"experienceYears\":3,\"specialization\":\"Trekking\"}"))
                .andExpect(status().isCreated());
        for (String status : List.of("INACTIVE", "BLOCKED")) {
            jdbc.update("UPDATE users SET status=? WHERE email=?", status, adminEmail);
            mvc.perform(get("/api/admin/events").header("Authorization", admin))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/api/admin/events").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    /** Xóa đúng dữ liệu mẫu của bài test, không tác động tài khoản hoặc tour có sẵn. */
    @AfterEach
    void cleanup() {
        if (eventId != null) jdbc.update("DELETE FROM tour_events WHERE event_id=?", eventId);
        for (Long userId : userIds) {
            jdbc.update("DELETE FROM tour_guides WHERE user_id=?", userId);
            jdbc.update("DELETE FROM users WHERE user_id=?", userId);
        }
    }
}
