package com.coc.sba_treektour.modules.tour.service;

import com.coc.sba_treektour.modules.tour.dto.request.GuideRegistrationInput;
import com.coc.sba_treektour.modules.tour.dto.response.GuideRegistrationResponse;

public interface GuideRegistrationService {
    /** Tạo user role GUIDE và liên kết guide bằng ID tự sinh; lỗi DB rollback cả hai bản ghi. */
    GuideRegistrationResponse create(GuideRegistrationInput input);
}
