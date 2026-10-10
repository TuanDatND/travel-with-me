package com.coc.sba_treektour.tour;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.coc.sba_treektour.tour.entity.*;
import com.coc.sba_treektour.tour.repository.*;
import com.coc.sba_treektour.tour.service.*;
import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TourApiIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired EventRepository events;
    @Autowired GuideRepository guides;
    @Autowired EventService eventService;
    @Autowired GuideService guideService;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean CloudinaryImageStorage storage;
    private Long userId;
    private final String fullEvent =
            """
            {"eventName":"Ta Nang", "eventType":"TREKKING", "description":"Two days outdoors",
             "location":"Lam Dong", "basePrice":1500000.00,
             "details":{"durationMinutes":2880,"difficultyLevel":"MODERATE",
                        "meetingPoint":"Outpost", "requirements":"Trekking shoes"}}
            """;

    /** Tạo tài khoản mẫu cho mỗi bài kiểm thử; dữ liệu được rollback sau khi chạy. */
    @BeforeEach
    void account() {
        Long roleId =
                jdbc.queryForObject(
                        "INSERT INTO roles(role_name) VALUES (?) RETURNING role_id",
                        Long.class,
                        "TEST_" + UUID.randomUUID());
        userId =
                jdbc.queryForObject(
                        "INSERT INTO users(role_id,full_name,email,password) VALUES (?,?,?,?)"
                            + " RETURNING user_id",
                        Long.class,
                        roleId,
                        "Guide Name",
                        UUID.randomUUID() + "@example.test",
                        "test-only-hash");
    }

    /** Đọc ID từ JSON response để các bước kiểm thử dùng tài nguyên vừa tạo. */
    private long id(MvcResult result, String field) throws Exception {
        return ((Number)
                        JsonPath.read(
                                result.getResponse()
                                        .getContentAsString(
                                                java.nio.charset.StandardCharsets.UTF_8),
                                "$.data." + field))
                .longValue();
    }

    /** Tạo tour tối giản theo trạng thái để kiểm tra hiển thị và quyền truy cập. */
    private TourEvent event(EventStatus status) {
        TourEvent e = new TourEvent();
        e.setEventName("Ta Nang");
        e.setEventType(EventType.TREKKING);
        e.setBasePrice(BigDecimal.TEN);
        e.setStatus(status);
        return events.saveAndFlush(e);
    }

    /** Tạo PNG mẫu để kiểm thử upload mà không đọc file ngoài hay gọi Cloudinary thật. */
    private MockMultipartFile png() {
        return new MockMultipartFile(
                "file",
                "image.png",
                "image/png",
                Base64.getDecoder()
                        .decode(
                                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII="));
    }

    /** Kiểm tra khách xem được tour công bố và nhận 404 với tour nháp hoặc lưu trữ. */
    @Test
    void publicBrowsingRequiresNoLoginAndHidesDraftAndArchive() throws Exception {
        TourEvent draft = event(EventStatus.DRAFT);
        TourEvent published = event(EventStatus.PUBLISHED);
        TourEvent archived = event(EventStatus.ARCHIVED);
        mvc.perform(get("/api/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].eventId").value(published.getId()));
        mvc.perform(get("/api/events/{id}", draft.getId())).andExpect(status().isNotFound());
        mvc.perform(get("/api/events/{id}", archived.getId())).andExpect(status().isNotFound());
        mvc.perform(get("/api/events/{id}", published.getId())).andExpect(status().isOk());
    }

    /** Kiểm tra người chưa đăng nhập không dùng được API quản trị tour. */
    @Test
    void anonymousCannotManage() throws Exception {
        mvc.perform(get("/api/admin/events"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.data.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.timestamp").isString());
        mvc.perform(
                        post("/api/admin/events")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(fullEvent))
                .andExpect(status().isUnauthorized());
    }

    /** Kiểm tra khách hàng không được quản trị tour hoặc hướng dẫn viên. */
    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customerCannotManage() throws Exception {
        mvc.perform(get("/api/admin/guides")).andExpect(status().isForbidden());
        mvc.perform(
                        post("/api/admin/events")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(fullEvent))
                .andExpect(status().isForbidden());
    }

    /** Kiểm tra ADMIN ghi dữ liệu không cần CSRF khi API dùng JWT stateless. */
    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanWriteWithoutCsrf() throws Exception {
        mvc.perform(
                        post("/api/admin/events")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(fullEvent))
                .andExpect(status().isCreated());
    }

    /** Kiểm tra luồng tạo nháp, thêm ảnh, công bố, tìm kiếm công khai và lưu trữ tour. */
    @Test
    @WithMockUser(roles = "ADMIN")
    void createUploadPublishBrowseAndArchive() throws Exception {
        long eventId =
                id(
                        mvc.perform(
                                        post("/api/admin/events")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(fullEvent))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.status").value(201))
                                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                                .andReturn(),
                        "eventId");
        mvc.perform(
                        patch("/api/admin/events/{id}/status", eventId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isConflict());
        when(storage.upload(any()))
                .thenReturn(
                        new CloudinaryImageStorage.StoredImage(
                                "https://images.example.test/a.png", "events/a"));
        long imageId =
                id(
                        mvc.perform(multipart("/api/admin/events/{id}/images", eventId).file(png()))
                                .andExpect(status().isCreated())
                                .andReturn(),
                        "imageId");
        mvc.perform(
                        patch("/api/admin/events/{id}/status", eventId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/events/{id}", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.images[0].imageId").value(imageId))
                .andExpect(jsonPath("$.data.images[0].cloudinaryPublicId").doesNotExist());
        mvc.perform(
                        get("/api/events")
                                .param("eventType", "TREKKING")
                                .param("difficultyLevel", "MODERATE")
                                .param("minPrice", "1000000")
                                .param("maxPrice", "2000000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
        mvc.perform(delete("/api/admin/events/{id}/images/{image}", eventId, imageId))
                .andExpect(status().isConflict());
        mvc.perform(
                        patch("/api/admin/events/{id}/status", eventId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"ARCHIVED\"}"))
                .andExpect(status().isOk());
        mvc.perform(
                        put("/api/admin/events/{id}", eventId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(fullEvent))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/events/{id}", eventId)).andExpect(status().isNotFound());
        assertThat(eventService.reference(eventId).status()).isEqualTo(EventStatus.ARCHIVED);
    }

    /** Kiểm tra xử lý lỗi lưu trữ, ID ảnh không thuộc tour và file giả ảnh. */
    @Test
    @WithMockUser(roles = "ADMIN")
    void imageOwnershipAndStorageFailureAreHandled() throws Exception {
        TourEvent e = event(EventStatus.DRAFT);
        when(storage.upload(any()))
                .thenThrow(
                        new TourException(
                                org.springframework.http.HttpStatus.BAD_GATEWAY,
                                "STORAGE_ERROR",
                                "Failed"));
        mvc.perform(multipart("/api/admin/events/{id}/images", e.getId()).file(png()))
                .andExpect(status().isBadGateway());
        assertThat(e.getImages()).isEmpty();
        mvc.perform(delete("/api/admin/events/{id}/images/999999", e.getId()))
                .andExpect(status().isNotFound());
        mvc.perform(
                        multipart("/api/admin/events/{id}/images", e.getId())
                                .file(
                                        new MockMultipartFile(
                                                "file",
                                                "fake.png",
                                                "image/png",
                                                "not-an-image".getBytes())))
                .andExpect(status().isBadRequest());
    }

    /** Kiểm tra lỗi dữ liệu, JSON và phân trang trả đúng status cùng cấu trúc lỗi. */
    @Test
    @WithMockUser(roles = "ADMIN")
    void validationAndPaginationErrorsAreStructured() throws Exception {
        mvc.perform(
                        post("/api/admin/events")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"eventName":" ","eventType":"TREKKING","basePrice":-1}
                                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.fieldErrors.eventName").exists())
                .andExpect(jsonPath("$.data.fieldErrors.basePrice").exists());
        mvc.perform(get("/api/events").param("size", "101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/events").param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/events").param("minPrice", "20").param("maxPrice", "10"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/events").param("eventType", "INVALID"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/events").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.code").value("INVALID_INPUT"));
    }

    /** Kiểm tra nhiều chuyên môn cùng tài khoản, ẩn dữ liệu riêng tư và chặn chuyên môn trùng. */
    @Test
    @WithMockUser(roles = "ADMIN")
    void guidesAllowDifferentSpecializationsAndHidePrivateFields() throws Exception {
        String input =
                "{\"userId\":" + userId + ",\"experienceYears\":3,\"specialization\":\"Trekking\"}";
        long guideId =
                id(
                        mvc.perform(
                                        post("/api/admin/guides")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(input))
                                .andExpect(status().isCreated())
                                .andReturn(),
                        "guideId");
        mvc.perform(
                        post("/api/admin/guides")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(input.replace("Trekking", "Camping")))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/guides").param("specialization", "trek"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
        mvc.perform(get("/api/guides/{id}", guideId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("Guide Name"))
                .andExpect(jsonPath("$.data.userId").doesNotExist())
                .andExpect(jsonPath("$.data.email").doesNotExist())
                .andExpect(jsonPath("$.data.password").doesNotExist());
        assertThat(guideService.reference(guideId).userId()).isEqualTo(userId);
        mvc.perform(
                        patch("/api/admin/guides/{id}/status", guideId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/guides/{id}", guideId)).andExpect(status().isNotFound());
        mvc.perform(
                        post("/api/admin/guides")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(input.replace("Trekking", " trekking ")))
                .andExpect(status().isConflict());
    }

    /** Kiểm tra tài khoản bị khóa không tạo được hồ sơ mới và bị ẩn công khai. */
    @Test
    @WithMockUser(roles = "ADMIN")
    void blockedAccountsCannotGetNewProfilesAndAreHidden() throws Exception {
        TourGuide g = new TourGuide();
        g.setUserId(userId);
        g.setSpecialization("Trekking");
        g.setExperienceYears(1);
        guides.saveAndFlush(g);
        jdbc.update("UPDATE users SET status='BLOCKED' WHERE user_id=?", userId);
        mvc.perform(get("/api/guides/{id}", g.getId())).andExpect(status().isNotFound());
        mvc.perform(get("/api/guides"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mvc.perform(
                        post("/api/admin/guides")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"userId\":"
                                                + userId
                                                + ",\"experienceYears\":1,\"specialization\":\"Camping\"}"))
                .andExpect(status().isConflict());
    }

    /** Kiểm tra không thể sửa tour công bố thành nội dung thiếu điều kiện bắt buộc. */
    @Test
    @WithMockUser(roles = "ADMIN")
    void publishedContentCannotBeMadeIncomplete() throws Exception {
        long eventId =
                id(
                        mvc.perform(
                                        post("/api/admin/events")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(fullEvent))
                                .andReturn(),
                        "eventId");
        when(storage.upload(any()))
                .thenReturn(
                        new CloudinaryImageStorage.StoredImage(
                                "https://images.example.test/b.png", "events/b"));
        mvc.perform(multipart("/api/admin/events/{id}/images", eventId).file(png()))
                .andExpect(status().isCreated());
        mvc.perform(
                        patch("/api/admin/events/{id}/status", eventId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isOk());
        mvc.perform(
                        put("/api/admin/events/{id}", eventId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(fullEvent.replace("Trekking shoes", "")))
                .andExpect(status().isConflict());
    }

    /** Kiểm tra metadata còn nguyên khi Cloudinary không xóa được tài sản. */
    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteImageKeepsRecordWhenStorageFails() throws Exception {
        TourEvent e = event(EventStatus.DRAFT);
        when(storage.upload(any()))
                .thenReturn(
                        new CloudinaryImageStorage.StoredImage(
                                "https://images.example.test/c.png", "events/c"));
        long imageId =
                id(
                        mvc.perform(
                                        multipart("/api/admin/events/{id}/images", e.getId())
                                                .file(png()))
                                .andReturn(),
                        "imageId");
        doThrow(
                        new TourException(
                                org.springframework.http.HttpStatus.BAD_GATEWAY,
                                "STORAGE_ERROR",
                                "Failed"))
                .when(storage)
                .delete("events/c");
        mvc.perform(delete("/api/admin/events/{id}/images/{image}", e.getId(), imageId))
                .andExpect(status().isBadGateway());
        assertThat(e.getImages()).hasSize(1);
    }

    /** Kiểm tra unique index chặn chuyên môn trùng kể cả khi bỏ qua service. */
    @Test
    void databaseRejectsDuplicateSpecializationEvenWithoutServiceCheck() {
        jdbc.update(
                "INSERT INTO tour_guides(user_id,experience_years,specialization) VALUES"
                    + " (?,1,'Trekking')",
                userId);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "INSERT INTO"
                                            + " tour_guides(user_id,experience_years,specialization)"
                                            + " VALUES (?,1,' trekking ')",
                                        userId))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    /** Kiểm tra JPA lưu cập nhật hồ sơ managed và DB từ chối đổi sang chuyên môn trùng. */
    @Test
    @WithMockUser(roles = "ADMIN")
    void managedGuideUpdatesPersistAndDatabaseRejectsDuplicateUpdates() throws Exception {
        Long first =
                jdbc.queryForObject(
                        "INSERT INTO tour_guides(user_id,experience_years,specialization) VALUES"
                            + " (?,1,'Trekking') RETURNING guide_id",
                        Long.class,
                        userId);
        Long second =
                jdbc.queryForObject(
                        "INSERT INTO tour_guides(user_id,experience_years,specialization) VALUES"
                            + " (?,1,'Camping') RETURNING guide_id",
                        Long.class,
                        userId);
        mvc.perform(
                        put("/api/admin/guides/{id}", first)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"experienceYears\":5,\"specialization\":\"Hiking\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.experienceYears").value(5));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT specialization FROM tour_guides WHERE guide_id=?",
                                String.class,
                                first))
                .isEqualTo("Hiking");
        mvc.perform(
                        put("/api/admin/guides/{id}", second)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"experienceYears\":2,\"specialization\":\" hiking \"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.code").value("CONFLICT"));
    }
}
