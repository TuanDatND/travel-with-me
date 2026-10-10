package com.coc.sba_treektour.schedule.dto;

import jakarta.validation.constraints.Size;

/** Người đăng ký lấy từ JWT; giá chốt từ tour tại thời điểm giữ chỗ. */
public record RegisterParticipantRequest(
        @Size(max = 2000, message = "note tối đa 2000 ký tự") String note) {}
