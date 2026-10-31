package com.coc.sba_treektour.modules.tour;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.coc.sba_treektour.modules.tour.entity.TourEvent;
import com.coc.sba_treektour.modules.tour.enums.EventStatus;
import com.coc.sba_treektour.modules.tour.enums.EventType;
import com.coc.sba_treektour.modules.tour.repository.EventRepository;
import com.coc.sba_treektour.modules.tour.service.EventService;
import com.coc.sba_treektour.modules.tour.storage.CloudinaryImageStorage;
import com.coc.sba_treektour.modules.tour.entity.TourGuide;
import com.coc.sba_treektour.modules.tour.exception.TourException;
import com.coc.sba_treektour.modules.tour.repository.GuideRepository;
import com.coc.sba_treektour.modules.tour.service.GuideService;
import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TourApiIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired EntityManager entityManager;
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

    private final String richDetails =
            """
            {"durationMinutes":2880,"difficultyLevel":"MODERATE","meetingPoint":"Outpost",
             "requirements":"Giày trekking", "itinerary":[
              {"day":1,"title":"Vượt đồi cỏ","summary":"Đi bộ đến điểm cắm trại",
               "distanceKm":"8–10 km","elevation":"Lên 450 m","highlights":["Đồi cỏ"],
               "activities":[{"time":"07:00 – 08:00","title":"Tập trung","location":"Đà Lạt",
                              "description":"Kiểm tra trang bị","icon":"meet"}]},
              {"day":2,"title":"Trở về","activities":[{"time":"08:00","title":"Xuống núi"}]}
             ],"packingItems":[{"text":"Giày trekking","required":true},
                               {"text":"Kính râm","required":false}]}
            """;

    /** Ghép chi tiết vào request tour hợp lệ để kiểm tra đúng validation lồng nhau. */
    private String contentRequest(String details) {
        return "{\"eventName\":\"Tour nội"
                + " dung\",\"eventType\":\"TREKKING\",\"basePrice\":1500000,\"description\":\"Hai"
                + " ngày\",\"location\":\"Lâm Đồng\",\"details\":"
                + details
                + "}";
    }

    /** Lưu/đọc JSONB thật, sửa nội dung rồi công bố và kiểm tra response công khai. */
    @Test
    @WithMockUser(authorities = "ADMIN")
    void detailedContentPersistsUpdatesAndIsPublicAfterPublishing() throws Exception {
        long eventId =
                id(
                        mvc.perform(
                                        post("/api/admin/events")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(contentRequest(richDetails)))
                                .andExpect(status().isCreated())
                                .andExpect(
                                        jsonPath("$.data.details.itinerary[1].activities[0].icon")
                                                .value("hike"))
                                .andReturn(),
                        "eventId");
        entityManager.clear();
        mvc.perform(get("/api/admin/events/{id}", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.details.itinerary.length()").value(2))
                .andExpect(jsonPath("$.data.details.itinerary[0].title").value("Vượt đồi cỏ"))
                .andExpect(
                        jsonPath("$.data.details.itinerary[0].activities[0].location")
                                .value("Đà Lạt"))
                .andExpect(jsonPath("$.data.details.packingItems[1].required").value(false));
        mvc.perform(get("/api/events/{id}", eventId)).andExpect(status().isNotFound());
        mvc.perform(
                        put("/api/admin/events/{id}", eventId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(contentRequest(richDetails.replace("8–10 km", "9 km"))))
                .andExpect(status().isOk());
        entityManager.clear();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT itinerary #>> '{0,distanceKm}' FROM event_details WHERE"
                                        + " event_id=?",
                                String.class,
                                eventId))
                .isEqualTo("9 km");
        // Ảnh legacy làm fixture; không đăng ký callback Cloudinary qua transaction của test.
        jdbc.update(
                "INSERT INTO event_images(event_id,image_url) VALUES (?,?)",
                eventId,
                "https://example.test/content.png");
        entityManager.clear();
        mvc.perform(
                        patch("/api/admin/events/{id}/status", eventId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isOk());
        entityManager.clear();
        mvc.perform(
                        get("/api/events/{id}", eventId)
                                .with(user("guest").authorities(() -> "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.details.itinerary[0].distanceKm").value("9 km"))
                .andExpect(jsonPath("$.data.details.packingItems[0].text").value("Giày trekking"));
    }

    /** Request cũ và danh sách null vẫn chạy; PUT danh sách rỗng xóa nội dung cũ rõ ràng. */
    @Test
    @WithMockUser(authorities = "ADMIN")
    void oldRequestsRemainCompatibleAndEmptyListsClearContent() throws Exception {
        long eventId =
                id(
                        mvc.perform(
                                        post("/api/admin/events")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(fullEvent))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.data.details.itinerary").isEmpty())
                                .andExpect(jsonPath("$.data.details.packingItems").isEmpty())
                                .andReturn(),
                        "eventId");
        mvc.perform(
                        put("/api/admin/events/{id}", eventId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(contentRequest(richDetails)))
                .andExpect(status().isOk());
        mvc.perform(
                        put("/api/admin/events/{id}", eventId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(contentRequest("{\"itinerary\":[],\"packingItems\":[]}")))
                .andExpect(status().isOk());
        entityManager.clear();
        mvc.perform(get("/api/admin/events/{id}", eventId))
                .andExpect(jsonPath("$.data.details.itinerary").isEmpty())
                .andExpect(jsonPath("$.data.details.packingItems").isEmpty());
        mvc.perform(
                        post("/api/admin/events")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        contentRequest(
                                                "{\"itinerary\":null,\"packingItems\":null}")))
                .andExpect(status().isCreated());
    }

    /** Chặn số ngày sai thứ tự, phần tử null, thiếu dữ liệu và vượt giới hạn nội dung. */
    @Test
    @WithMockUser(authorities = "ADMIN")
    void detailedContentRejectsInvalidNestedInput() throws Exception {
        List<String> invalid =
                List.of(
                        richDetails.replace("\"day\":2", "\"day\":1"),
                        richDetails.replace("\"day\":1", "\"day\":0"),
                        richDetails.replace("\"icon\":\"meet\"", "\"icon\":\"unknown\""),
                        richDetails.replace("\"title\":\"Tập trung\"", "\"title\":\"  \""),
                        richDetails.replace("\"required\":true", "\"required\":null"),
                        "{\"itinerary\":[null]}",
                        "{\"packingItems\":[null]}",
                        richDetails.replace(
                                "\"highlights\":[\"Đồi cỏ\"]", "\"highlights\":[\" \"]"),
                        "{\"itinerary\":["
                                + "{\"day\":1,\"title\":\"Ngày\"},".repeat(30)
                                + "{\"day\":1,\"title\":\"Ngày\"}]}",
                        "{\"packingItems\":["
                                + "{\"text\":\"Đồ\",\"required\":true},".repeat(100)
                                + "{\"text\":\"Đồ\",\"required\":true}]}");
        for (String details : invalid) {
            mvc.perform(
                            post("/api/admin/events")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(contentRequest(details)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.data.code").value("INVALID_INPUT"));
        }
    }

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
        TourEvent e = new TourEvent("Test", EventType.TREKKING, BigDecimal.TEN);
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
    @WithMockUser(authorities = "CUSTOMER")
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
    @WithMockUser(authorities = "ADMIN")
    void adminCanWriteWithoutCsrf() throws Exception {
        mvc.perform(
                        post("/api/admin/events")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(fullEvent))
                .andExpect(status().isCreated());
    }

    /** Kiểm tra luồng tạo nháp, thêm ảnh, công bố, tìm kiếm công khai và lưu trữ tour. */
    @Test
    @WithMockUser(authorities = "ADMIN")
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
    @WithMockUser(authorities = "ADMIN")
    void imageOwnershipAndStorageFailureAreHandled() throws Exception {
        TourEvent e = event(EventStatus.DRAFT);
        when(storage.upload(any()))
                .thenThrow(new TourException(HttpStatus.BAD_GATEWAY, "STORAGE_ERROR", "Failed"));
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
    @WithMockUser(authorities = "ADMIN")
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
    @WithMockUser(authorities = "ADMIN")
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
    @WithMockUser(authorities = "ADMIN")
    void blockedAccountsCannotGetNewProfilesAndAreHidden() throws Exception {
        TourGuide g = new TourGuide(userId, 0, "Test");
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
    @WithMockUser(authorities = "ADMIN")
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
    @WithMockUser(authorities = "ADMIN")
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
        doThrow(new TourException(HttpStatus.BAD_GATEWAY, "STORAGE_ERROR", "Failed"))
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
    @WithMockUser(authorities = "ADMIN")
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

    /**
     * Kiểm tra lưu PostgreSQL array, chuẩn hóa hồ sơ và trả dữ liệu công khai không lộ publicId.
     */
    @Test
    @WithMockUser(authorities = "ADMIN")
    void guideProfilePersistsAndAppearsInPublicListAndDetails() throws Exception {
        long guideId = createProfileGuide();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT languages[1] FROM tour_guides WHERE guide_id=?",
                                String.class,
                                guideId))
                .isEqualTo("Tiếng Việt");
        mvc.perform(get("/api/guides/{id}", guideId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bio").value("Hướng dẫn viên trekking"))
                .andExpect(jsonPath("$.data.languages[0]").value("Tiếng Việt"))
                .andExpect(jsonPath("$.data.languages.length()").value(2))
                .andExpect(jsonPath("$.data.avatarPublicId").doesNotExist())
                .andExpect(jsonPath("$.data.userId").doesNotExist());
        mvc.perform(get("/api/guides").param("specialization", "Profile test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].languages[1]").value("English"));
        mvc.perform(
                        put("/api/admin/guides/{id}", guideId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"experienceYears":5,"specialization":"Profile test",
                                         "bio":"Giới thiệu mới","languages":["English"]}
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bio").value("Giới thiệu mới"))
                .andExpect(jsonPath("$.data.languages.length()").value(1));
        assertThat(guideService.reference(guideId).userId()).isEqualTo(userId);
    }

    /** Tạo hồ sơ mẫu qua API với giới thiệu và các ngôn ngữ cần chuẩn hóa. */
    private long createProfileGuide() throws Exception {
        return id(
                mvc.perform(
                                post("/api/admin/guides")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {"userId":%d,"experienceYears":3,"specialization":"Profile test",
                                                 "bio":" Hướng dẫn viên trekking ",
                                                 "languages":[" Tiếng Việt ","English","English"]}
                                                """
                                                        .formatted(userId)))
                        .andExpect(status().isCreated())
                        .andReturn(),
                "guideId");
    }

    /** Kiểm tra giới hạn giới thiệu và từng ngôn ngữ ở ranh giới request. */
    @Test
    @WithMockUser(authorities = "ADMIN")
    void guideProfileRejectsInvalidFields() throws Exception {
        long guideId = createProfileGuide();
        for (String extra :
                List.of(
                        "\"bio\":\"" + "x".repeat(5001) + "\"",
                        "\"languages\":[\" \"]",
                        "\"languages\":[null]",
                        "\"languages\":[\"" + "x".repeat(51) + "\"]",
                        "\"languages\":["
                                + String.join(",", Collections.nCopies(11, "\"English\""))
                                + "]")) {
            mvc.perform(
                            put("/api/admin/guides/{id}", guideId)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            "{\"experienceYears\":3,\"specialization\":\"Profile"
                                                    + " test\","
                                                    + extra
                                                    + "}"))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(storage);
    }

    /** Kiểm tra upload, đọc công khai và xóa ảnh; dữ liệu lưu trữ nội bộ không lộ ra response. */
    @Test
    @WithMockUser(authorities = "ADMIN")
    void guideAvatarUploadAndDelete() throws Exception {
        long guideId = createProfileGuide();
        when(storage.uploadGuideAvatar(any()))
                .thenReturn(
                        new CloudinaryImageStorage.StoredImage(
                                "https://example.test/guide.png", "terrapeak/guides/test"));
        mvc.perform(
                        multipart("/api/admin/guides/{id}/avatar", guideId)
                                .file(png())
                                .with(
                                        request -> {
                                            request.setMethod("PUT");
                                            return request;
                                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatarUrl").value("https://example.test/guide.png"))
                .andExpect(jsonPath("$.data.avatarPublicId").doesNotExist());
        mvc.perform(get("/api/guides/{id}", guideId))
                .andExpect(jsonPath("$.data.avatarUrl").value("https://example.test/guide.png"));
        mvc.perform(delete("/api/admin/guides/{id}/avatar", guideId))
                .andExpect(status().isNoContent());
        verify(storage).delete("terrapeak/guides/test");
        assertThat(guides.findById(guideId).orElseThrow().getAvatarUrl()).isNull();
        mvc.perform(delete("/api/admin/guides/{id}/avatar", guideId))
                .andExpect(status().isNoContent());
    }

    /** File sai bị chặn trước Cloudinary; lỗi upstream giữ nguyên ảnh đang dùng. */
    @Test
    @WithMockUser(authorities = "ADMIN")
    void guideAvatarValidationAndStorageFailurePreserveExistingImage() throws Exception {
        long guideId = createProfileGuide();
        TourGuide guide = guides.findById(guideId).orElseThrow();
        guide.setAvatarUrl("https://example.test/old.png");
        guide.setAvatarPublicId("terrapeak/guides/old");
        guides.flush();
        mvc.perform(
                        multipart("/api/admin/guides/{id}/avatar", guideId)
                                .file(
                                        new MockMultipartFile(
                                                "file", "bad.png", "image/png", new byte[] {1}))
                                .with(
                                        request -> {
                                            request.setMethod("PUT");
                                            return request;
                                        }))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(storage);
        when(storage.uploadGuideAvatar(any()))
                .thenThrow(
                        new TourException(HttpStatus.BAD_GATEWAY, "STORAGE_ERROR", "Unavailable"));
        mvc.perform(
                        multipart("/api/admin/guides/{id}/avatar", guideId)
                                .file(png())
                                .with(
                                        request -> {
                                            request.setMethod("PUT");
                                            return request;
                                        }))
                .andExpect(status().isBadGateway());
        doThrow(new TourException(HttpStatus.BAD_GATEWAY, "STORAGE_ERROR", "Unavailable"))
                .when(storage)
                .delete("terrapeak/guides/old");
        mvc.perform(delete("/api/admin/guides/{id}/avatar", guideId))
                .andExpect(status().isBadGateway());
        assertThat(guide.getAvatarPublicId()).isEqualTo("terrapeak/guides/old");
        assertThat(guide.getAvatarUrl()).isEqualTo("https://example.test/old.png");
    }

    /** CUSTOMER không được sửa ảnh đại diện; ADMIN nhận 404 với hồ sơ không tồn tại. */
    @Test
    void guideAvatarRequiresAdmin() throws Exception {
        mvc.perform(delete("/api/admin/guides/999999/avatar")).andExpect(status().isUnauthorized());
        mvc.perform(
                        delete("/api/admin/guides/999999/avatar")
                                .with(
                                        user("customer")
                                                .authorities(
                                                        new org.springframework.security.core
                                                                .authority.SimpleGrantedAuthority(
                                                                "CUSTOMER"))))
                .andExpect(status().isForbidden());
        mvc.perform(
                        delete("/api/admin/guides/999999/avatar")
                                .with(
                                        user("admin")
                                                .authorities(
                                                        new org.springframework.security.core
                                                                .authority.SimpleGrantedAuthority(
                                                                "ADMIN"))))
                .andExpect(status().isNotFound());
        verifyNoInteractions(storage);
    }
    /** Tạo fixture ảnh bằng SQL local, không gọi Cloudinary thật trong kiểm thử bố cục. */
    private long layoutImage(long eventId, String suffix, int order) {
        return jdbc.queryForObject(
                "INSERT INTO event_images(event_id,image_url,sort_order) VALUES (?,?,?) RETURNING image_id",
                Long.class, eventId, "https://example.test/" + suffix + ".png", order);
    }

    /** Tạo tour có hai ngày để kiểm ảnh gắn đúng lịch trình, không phải lịch khởi hành. */
    private long layoutTour() throws Exception {
        return id(mvc.perform(post("/api/admin/events").contentType(MediaType.APPLICATION_JSON)
                .content(contentRequest(richDetails))).andExpect(status().isCreated()).andReturn(), "eventId");
    }

    /** Ảnh bìa, thứ tự và ngày phải tồn tại sau khi clear persistence context và đọc công khai. */
    @Test
    @WithMockUser(authorities = "ADMIN")
    void imageLayoutPersistsCoverOrderAndDayForPublicReaders() throws Exception {
        long eventId = layoutTour();
        long first = layoutImage(eventId, "first", 0);
        long second = layoutImage(eventId, "second", 1);
        entityManager.clear();
        mvc.perform(put("/api/admin/events/{id}/images/layout", eventId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"images\":[{\"imageId\":" + second
                                + ",\"dayNumber\":2},{\"imageId\":" + first + ",\"dayNumber\":1}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].imageId").value(second))
                .andExpect(jsonPath("$.data[0].cover").value(true))
                .andExpect(jsonPath("$.data[1].cover").value(false));
        entityManager.clear();
        mvc.perform(patch("/api/admin/events/{id}/status", eventId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"PUBLISHED\"}")).andExpect(status().isOk());
        entityManager.clear();
        mvc.perform(get("/api/events/{id}", eventId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.images[0].imageId").value(second))
                .andExpect(jsonPath("$.data.images[0].sortOrder").value(0))
                .andExpect(jsonPath("$.data.images[0].dayNumber").value(2));
        mvc.perform(get("/api/events").param("keyword", "Tour nội dung"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].coverImageUrl")
                        .value("https://example.test/second.png"));
    }

    /** Request sai không được sửa một phần bố cục; ảnh ngoài tour và ngày không tồn tại bị từ chối. */
    @Test
    @WithMockUser(authorities = "ADMIN")
    void imageLayoutRejectsDuplicatesForeignImagesMissingImagesAndInvalidDays() throws Exception {
        long eventId = layoutTour();
        long first = layoutImage(eventId, "first", 0);
        long second = layoutImage(eventId, "second", 1);
        long foreign = layoutImage(layoutTour(), "foreign", 0);
        entityManager.clear();
        for (String body : List.of(
                "{\"images\":[{\"imageId\":" + second + "},{\"imageId\":" + second + "}]}",
                "{\"images\":[{\"imageId\":" + second + "},{\"imageId\":" + first + ",\"dayNumber\":3}]}",
                "{\"images\":[{\"imageId\":" + second + ",\"dayNumber\":0},{\"imageId\":" + first + "}]}",
                "{\"images\":[null]}", "{}")) {
            mvc.perform(put("/api/admin/events/{id}/images/layout", eventId)
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        mvc.perform(put("/api/admin/events/{id}/images/layout", eventId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"images\":[{\"imageId\":" + first + "}]}"))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/admin/events/{id}/images/layout", eventId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"images\":[{\"imageId\":" + second + "},{\"imageId\":" + foreign + "}]}"))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT sort_order FROM event_images WHERE image_id=?", Integer.class, first)).isZero();
        assertThat(jdbc.queryForObject("SELECT day_number FROM event_images WHERE image_id=?", Integer.class, second)).isNull();
    }

    /** Bỏ lịch trình chỉ gỡ liên kết ngày, không xóa ảnh hoặc làm đổi ảnh bìa. */
    @Test
    @WithMockUser(authorities = "ADMIN")
    void removingItineraryClearsDayAssignmentAndKeepsImages() throws Exception {
        long eventId = layoutTour();
        long image = layoutImage(eventId, "day", 0);
        entityManager.clear();
        mvc.perform(put("/api/admin/events/{id}/images/layout", eventId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"images\":[{\"imageId\":" + image + ",\"dayNumber\":2}]}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/admin/events/{id}", eventId).contentType(MediaType.APPLICATION_JSON)
                .content(fullEvent)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.images.length()").value(1))
                .andExpect(jsonPath("$.data.images[0].dayNumber").doesNotExist())
                .andExpect(jsonPath("$.data.images[0].cover").value(true));
    }

    /** Upload cũ nối cuối gallery; xóa bìa chuyển sang ảnh kế tiếp và không cần upload lại. */
    @Test
    @WithMockUser(authorities = "ADMIN")
    void oldImageEndpointsPreserveChosenCoverAndPromoteNextAfterDeletion() throws Exception {
        long eventId = layoutTour();
        long first = layoutImage(eventId, "first", 0);
        long second = layoutImage(eventId, "second", 1);
        entityManager.clear();
        mvc.perform(put("/api/admin/events/{id}/images/layout", eventId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"images\":[{\"imageId\":" + second + "},{\"imageId\":" + first + "}]}"))
                .andExpect(status().isOk());
        when(storage.upload(any())).thenReturn(new CloudinaryImageStorage.StoredImage("https://example.test/new.png", "new"));
        mvc.perform(multipart("/api/admin/events/{id}/images", eventId).file(png()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.sortOrder").value(2))
                .andExpect(jsonPath("$.data.cover").value(false));
        mvc.perform(delete("/api/admin/events/{id}/images/{image}", eventId, second)).andExpect(status().isNoContent());
        entityManager.clear();
        mvc.perform(get("/api/admin/events/{id}", eventId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.images[0].imageId").value(first))
                .andExpect(jsonPath("$.data.images[0].cover").value(true));
    }

    /** Khách không được sửa ảnh; tour lưu trữ giữ nguyên chính sách chỉ đọc. */
    @Test
    @WithMockUser(authorities = "ADMIN")
    void layoutRequiresAdminAndEditableTour() throws Exception {
        long eventId = layoutTour();
        mvc.perform(put("/api/admin/events/{id}/images/layout", eventId)
                .with(user("customer").authorities(() -> "CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"images\":[]}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/admin/events/{id}/status", eventId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ARCHIVED\"}")).andExpect(status().isOk());
        mvc.perform(put("/api/admin/events/{id}/images/layout", eventId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"images\":[]}")).andExpect(status().isConflict());
    }

}
