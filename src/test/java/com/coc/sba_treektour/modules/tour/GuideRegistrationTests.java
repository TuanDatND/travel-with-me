package com.coc.sba_treektour.modules.tour;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.coc.sba_treektour.common.service.EmailService;
import com.coc.sba_treektour.modules.account.repository.UserRepository;
import com.coc.sba_treektour.modules.tour.repository.GuideRepository;
import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GuideRegistrationTests {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @MockitoSpyBean GuideRepository guides;
    @MockitoBean EmailService emails;

    /** Request không có userId; role luôn do backend gán GUIDE dù client gửi role khác. */
    private String input(String email) {
        return """
        {"fullName":" New guide ","email":"%s","phone":"0900000000",
         "experienceYears":3,"specialization":"Trekking","bio":"Giới thiệu",
         "languages":["Tiếng Việt","English"],"role":"ADMIN"}
        """
                .formatted(email);
    }

    /** Gọi đúng endpoint tạo tài khoản và hồ sơ dành cho ADMIN. */
    private ResultActions create(String email) throws Exception {
        return mvc.perform(
                post("/api/admin/guides/with-account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(input(email)));
    }

    /** Tạo hai ID tự sinh, mật khẩu được hash; SMTP thất bại không làm mất hồ sơ đã tạo. */
    @Test
    @WithMockUser(authorities = "ADMIN")
    void createsAccountAndGuideTogetherWithoutUserId() throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        var result =
                create(email)
                        .andExpect(status().isCreated())
                        .andExpect(header().string("Cache-Control", "no-store"))
                        .andExpect(jsonPath("$.data.guide.guideId").isNumber())
                        .andExpect(jsonPath("$.data.guide.userId").isNumber())
                        .andExpect(jsonPath("$.data.emailSent").value(false))
                        .andReturn();
        String json = result.getResponse().getContentAsString();
        String temporary = JsonPath.read(json, "$.data.temporaryPassword");
        var account = users.findByEmail(email).orElseThrow();
        assertThat(account.getRole().getName()).isEqualTo("GUIDE");
        assertThat(account.getFullName()).isEqualTo("New guide");
        assertThat(account.getPassword()).isNotEqualTo(temporary);
        assertThat(passwords.matches(temporary, account.getPassword())).isTrue();
        long guideId = ((Number) JsonPath.read(json, "$.data.guide.guideId")).longValue();
        assertThat(guides.findById(guideId).orElseThrow().getUserId()).isEqualTo(account.getId());
        mvc.perform(get("/api/guides/{id}", guideId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.temporaryPassword").doesNotExist())
                .andExpect(jsonPath("$.data.email").doesNotExist());
        verify(emails)
                .sendInternalAccountEmail(eq(email), eq("New guide"), eq("GUIDE"), anyString());
    }

    /** Email đã dùng bị từ chối, không tạo thêm user hoặc hồ sơ guide. */
    @Test
    @WithMockUser(authorities = "ADMIN")
    void duplicateEmailDoesNotCreateAnotherGuide() throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        create(email).andExpect(status().isCreated());
        long before = guides.count();
        create(email).andExpect(status().isBadRequest());
        assertThat(guides.count()).isEqualTo(before);
        verify(emails, times(1))
                .sendInternalAccountEmail(eq(email), anyString(), anyString(), anyString());
    }

    /** Validation và phân quyền chặn request trước khi gọi dịch vụ tài khoản. */
    @Test
    void registrationRequiresAdminAndValidEmail() throws Exception {
        create("bad-email").andExpect(status().isUnauthorized());
        mvc.perform(
                        post("/api/admin/guides/with-account")
                                .with(
                                        org.springframework.security.test.web.servlet.request
                                                .SecurityMockMvcRequestPostProcessors.user(
                                                        "customer")
                                                .roles("CUSTOMER"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(input("bad-email")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(emails);
    }

    /** ADMIN gửi email sai không tạo tài khoản; email service được mock nên không gửi thư thật. */
    @Test
    @WithMockUser(authorities = "ADMIN")
    void invalidEmailDoesNotCreateAccount() throws Exception {
        create("bad-email").andExpect(status().isBadRequest());
        verifyNoInteractions(emails);
    }

    /**
     * Giao dịch mới rollback cả user vừa tạo nếu ghi guide thất bại; không tác động DB dùng chung.
     */
    @Test
    @WithMockUser(authorities = "ADMIN")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void profileFailureRollsBackNewAccount() throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        doThrow(new DataIntegrityViolationException("Simulated guide failure"))
                .when(guides)
                .saveAndFlush(any());
        create(email).andExpect(status().isConflict());
        assertThat(users.existsByEmail(email)).isFalse();
    }
}
