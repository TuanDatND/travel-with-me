package com.coc.sba_treektour.modules.tour.service.impl;

import com.coc.sba_treektour.modules.account.dto.CreateInternalUserRequest;
import com.coc.sba_treektour.modules.account.service.UserService;
import com.coc.sba_treektour.modules.tour.dto.request.GuideCreateInput;
import com.coc.sba_treektour.modules.tour.dto.request.GuideRegistrationInput;
import com.coc.sba_treektour.modules.tour.dto.response.GuideRegistrationResponse;
import com.coc.sba_treektour.modules.tour.service.GuideRegistrationService;
import com.coc.sba_treektour.modules.tour.service.GuideService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GuideRegistrationServiceImpl implements GuideRegistrationService {
    private final UserService users;
    private final GuideService guides;

    /** Tạo user role GUIDE và liên kết guide bằng ID tự sinh; lỗi DB rollback cả hai bản ghi. */
    @Transactional
    @Override
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
