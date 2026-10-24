package com.coc.sba_treektour.modules.tour.service;

import com.coc.sba_treektour.modules.account.dto.CreateInternalUserRequest;
import com.coc.sba_treektour.modules.account.service.UserService;
import com.coc.sba_treektour.modules.tour.dto.TourDtos.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GuideRegistrationService {
    private final UserService users;
    private final GuideService guides;

    /** Tái sử dụng contract tạo tài khoản của member 1, không tự ghi bảng users hoặc role. */
    public GuideRegistrationService(UserService users, GuideService guides) {
        this.users = users;
        this.guides = guides;
    }

    /** Tạo user role GUIDE và liên kết guide bằng ID tự sinh; lỗi DB rollback cả hai bản ghi. */
    @Transactional
    public GuideRegistrationResponse create(GuideRegistrationInput input) {
        var account =
                users.createInternalUser(
                        CreateInternalUserRequest.builder()
                                .fullName(input.fullName().trim())
                                .email(input.email().trim())
                                .phone(
                                        input.phone() == null || input.phone().isBlank()
                                                ? null
                                                : input.phone().trim())
                                .role("GUIDE")
                                .build());
        var guide =
                guides.create(
                        new GuideCreateInput(
                                account.getId(),
                                input.experienceYears(),
                                input.specialization(),
                                input.bio(),
                                input.languages()));
        return new GuideRegistrationResponse(
                guide, account.getEmail(), account.getTemporaryPassword(), account.isEmailSent());
    }
}
