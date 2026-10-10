package com.coc.sba_treektour.schedule.service;

import com.coc.sba_treektour.schedule.dto.*;
import com.coc.sba_treektour.schedule.entity.CheckInStatus;
import com.coc.sba_treektour.schedule.entity.EventParticipant;
import com.coc.sba_treektour.schedule.entity.EventSchedule;
import com.coc.sba_treektour.schedule.entity.EventScheduleHistory;
import com.coc.sba_treektour.schedule.entity.ScheduleStatus;
import com.coc.sba_treektour.schedule.repository.EventParticipantRepository;
import com.coc.sba_treektour.schedule.repository.EventScheduleHistoryRepository;
import com.coc.sba_treektour.schedule.repository.EventScheduleRepository;
import com.coc.sba_treektour.event.entity.EventStatus;
import com.coc.sba_treektour.tour.entity.GuideStatus;
import com.coc.sba_treektour.event.service.EventService;
import com.coc.sba_treektour.tour.service.GuideService;
import com.coc.sba_treektour.tour.service.TourException;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class ScheduleService {

    private final EventScheduleRepository scheduleRepository;
    private final EventScheduleHistoryRepository historyRepository;
    private final EventParticipantRepository participantRepository;
    private final EventService events;
    private final GuideService guides;
    private final ScheduleAccess access;
    private final JdbcTemplate jdbc;

    /** Khởi tạo dịch vụ lịch và các điểm nối tour, guide, phân quyền. */
    public ScheduleService(
            EventScheduleRepository scheduleRepository,
            EventScheduleHistoryRepository historyRepository,
            EventParticipantRepository participantRepository,
            EventService events,
            GuideService guides,
            ScheduleAccess access,
            JdbcTemplate jdbc) {
        this.scheduleRepository = scheduleRepository;
        this.historyRepository = historyRepository;
        this.participantRepository = participantRepository;
        this.events = events;
        this.guides = guides;
        this.access = access;
        this.jdbc = jdbc;
    }

    /** Tạo lịch tương lai cho tour công bố, chi nhánh hoạt động và guide không trùng lịch. */
    @Transactional
    public ScheduleResponse create(CreateScheduleRequest request) {
        if (!request.endDatetime().isAfter(request.startDatetime())) {
            throw new InvalidScheduleOperationException("endDatetime must be after startDatetime");
        }

        requirePublished(request.eventId());
        validateAssignment(request.guideId(), request.startDatetime(), request.endDatetime(), null);
        if (!Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM branches WHERE branch_id=? AND"
                                + " status='ACTIVE')",
                        Boolean.class,
                        request.branchId())))
            throw new InvalidScheduleOperationException(
                    "Chi nhánh không tồn tại hoặc đã ngừng hoạt động");
        EventSchedule schedule =
                new EventSchedule(
                        request.eventId(),
                        request.branchId(),
                        request.guideId(),
                        request.startDatetime(),
                        request.endDatetime(),
                        request.maxParticipants());
        return toResponse(scheduleRepository.save(schedule));
    }

    /** Đọc lịch; người dùng công khai chỉ thấy lịch của tour PUBLISHED. */
    @Transactional(readOnly = true)
    public ScheduleResponse findById(Long id) {
        EventSchedule schedule = findEntity(id);
        if (!access.manager()
                && events.reference(schedule.getEventId()).status() != EventStatus.PUBLISHED)
            throw new ScheduleNotFoundException(id);
        return toResponse(schedule);
    }

    /** Lọc đồng thời eventId, branchId và status; giới hạn tour theo quyền truy cập. */
    @Transactional(readOnly = true)
    public List<ScheduleResponse> findAll(Long eventId, Long branchId, ScheduleStatus status) {
        return scheduleRepository
                .search(eventId, branchId, status == null ? null : status.name(), access.manager())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /** Đổi guide/sức chứa của lịch chưa khởi hành sau khi kiểm tra phân công. */
    @Transactional
    public ScheduleResponse update(Long id, UpdateScheduleRequest request) {
        EventSchedule schedule = findForUpdate(id);
        if (request.maxParticipants() < schedule.getCurrentParticipants()) {
            throw new InvalidScheduleOperationException(
                    "maxParticipants cannot be less than current participants ("
                            + schedule.getCurrentParticipants()
                            + ")");
        }
        requireEditable(schedule);
        requirePublished(schedule.getEventId());
        validateAssignment(
                request.guideId(), schedule.getStartDatetime(), schedule.getEndDatetime(), id);
        schedule.updateDetails(request.guideId(), request.maxParticipants());
        return toResponse(schedule);
    }

    /** Dời lịch tương lai, kiểm tra guide và lưu lịch sử trong cùng giao dịch. */
    @Transactional
    public ScheduleResponse reschedule(Long id, RescheduleRequest request) {
        if (!request.newEndDatetime().isAfter(request.newStartDatetime())) {
            throw new InvalidScheduleOperationException(
                    "newEndDatetime must be after newStartDatetime");
        }
        EventSchedule schedule = findForUpdate(id);

        requireEditable(schedule);
        if (!request.newStartDatetime().isAfter(OffsetDateTime.now()))
            throw new InvalidScheduleOperationException("Ngày khởi hành mới phải ở tương lai");
        requirePublished(schedule.getEventId());
        validateAssignment(
                schedule.getGuideId(), request.newStartDatetime(), request.newEndDatetime(), id);
        EventScheduleHistory history =
                new EventScheduleHistory(
                        schedule,
                        schedule.getStartDatetime(),
                        request.newStartDatetime(),
                        schedule.getEndDatetime(),
                        request.newEndDatetime(),
                        schedule.getStatus().name(),
                        schedule.getStatus().name(),
                        request.changeReason());
        historyRepository.save(history);

        schedule.reschedule(request.newStartDatetime(), request.newEndDatetime());
        return toResponse(schedule);
    }

    /** Đổi trạng thái hợp lệ, kiểm tra sức chứa và hoàn tất các vé đã điểm danh. */
    @Transactional
    public ScheduleResponse updateStatus(Long id, UpdateScheduleStatusRequest request) {
        EventSchedule schedule = findForUpdate(id);
        ScheduleStatus oldStatus = schedule.getStatus();
        ScheduleStatus newStatus = request.status();

        if (oldStatus == newStatus) {
            return toResponse(schedule);
        }

        if (oldStatus == ScheduleStatus.COMPLETED || oldStatus == ScheduleStatus.CANCELLED)
            throw new InvalidScheduleOperationException(
                    "Lịch đã kết thúc hoặc bị hủy không thể mở lại");
        if (newStatus == ScheduleStatus.OPEN
                || newStatus == ScheduleStatus.FULL
                || newStatus == ScheduleStatus.IN_PROGRESS) {
            requirePublished(schedule.getEventId());
            validateAssignment(
                    schedule.getGuideId(),
                    schedule.getStartDatetime(),
                    schedule.getEndDatetime(),
                    id);
        }
        if (newStatus == ScheduleStatus.OPEN
                        && schedule.getCurrentParticipants() >= schedule.getMaxParticipants()
                || newStatus == ScheduleStatus.FULL
                        && schedule.getCurrentParticipants() < schedule.getMaxParticipants())
            throw new InvalidScheduleOperationException(
                    "Trạng thái OPEN/FULL phải khớp sức chứa hiện tại");
        EventScheduleHistory history =
                new EventScheduleHistory(
                        schedule,
                        null,
                        null,
                        null,
                        null,
                        oldStatus.name(),
                        newStatus.name(),
                        request.changeReason() == null
                                ? "Status changed from " + oldStatus + " to " + newStatus
                                : request.changeReason());
        historyRepository.save(history);

        schedule.updateStatus(newStatus);

        // When a schedule is COMPLETED, all checked-in participants transition to COMPLETED
        if (newStatus == ScheduleStatus.COMPLETED) {
            List<EventParticipant> participants = participantRepository.findByScheduleId(id);
            for (EventParticipant p : participants) {
                if (p.getCheckInStatus() == CheckInStatus.CHECKED_IN) {
                    p.complete();
                }
            }
        }

        return toResponse(schedule);
    }

    /** Đọc lịch sử thay đổi của một lịch tồn tại. */
    @Transactional(readOnly = true)
    public List<ScheduleHistoryResponse> getHistories(Long scheduleId) {
        findEntity(scheduleId);
        return historyRepository.findByScheduleIdOrderByChangedAtDesc(scheduleId).stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    /** Yêu cầu tour đã công bố trước khi mở lịch hoặc phân công. */
    private void requirePublished(Long eventId) {
        if (events.reference(eventId).status() != EventStatus.PUBLISHED)
            throw TourException.conflict("Tour phải ở trạng thái PUBLISHED");
    }

    /** Lịch đã bắt đầu hoặc kết thúc không được đổi ngày/phân công. */
    private void requireEditable(EventSchedule schedule) {
        if (!schedule.getStartDatetime().isAfter(OffsetDateTime.now())
                || schedule.getStatus() == ScheduleStatus.COMPLETED
                || schedule.getStatus() == ScheduleStatus.CANCELLED
                || schedule.getStatus() == ScheduleStatus.IN_PROGRESS)
            throw new InvalidScheduleOperationException(
                    "Chỉ được sửa lịch chưa khởi hành và chưa kết thúc/hủy");
    }

    /**
     * Khóa theo userId trong PostgreSQL để hai hồ sơ của cùng người không được xếp trùng đồng thời.
     */
    private void validateAssignment(
            Long guideId, OffsetDateTime start, OffsetDateTime end, Long excludeId) {
        if (excludeId == null && !start.isAfter(OffsetDateTime.now()))
            throw new InvalidScheduleOperationException("Ngày khởi hành phải ở tương lai");
        if (guideId == null) return;
        var guide = guides.reference(guideId);
        // ponytail: dùng advisory lock theo người, không thêm bảng khóa hay migration.
        jdbc.queryForList(
                "SELECT pg_advisory_xact_lock(hashtextextended('tour-guide:' || CAST(? AS text),"
                        + " 0))",
                guide.userId());
        if (guide.status() != GuideStatus.ACTIVE || !guide.accountActive())
            throw TourException.conflict("Hướng dẫn viên hoặc tài khoản đã ngừng hoạt động");
        if (scheduleRepository.overlaps(guide.userId(), excludeId, start, end))
            throw TourException.conflict("Hướng dẫn viên đã có lịch trùng thời gian");
    }

    /** Khóa lịch trước các thao tác ghi, cùng quy tắc với đăng ký/hủy vé. */
    private EventSchedule findForUpdate(Long id) {
        return scheduleRepository
                .findForUpdate(id)
                .orElseThrow(() -> new ScheduleNotFoundException(id));
    }

    /** Tìm lịch theo ID hoặc trả 404. */
    public EventSchedule findEntity(Long id) {
        return scheduleRepository.findById(id).orElseThrow(() -> new ScheduleNotFoundException(id));
    }

    /** Chuyển lịch sang dữ liệu response chung. */
    public ScheduleResponse toResponse(EventSchedule schedule) {
        return new ScheduleResponse(
                schedule.getId(),
                schedule.getEventId(),
                schedule.getBranchId(),
                schedule.getGuideId(),
                schedule.getStartDatetime(),
                schedule.getEndDatetime(),
                schedule.getMaxParticipants(),
                schedule.getCurrentParticipants(),
                schedule.getStatus(),
                schedule.getCreatedAt());
    }

    /** Chuyển bản ghi lịch sử sang dữ liệu response. */
    private ScheduleHistoryResponse toHistoryResponse(EventScheduleHistory h) {
        return new ScheduleHistoryResponse(
                h.getId(),
                h.getSchedule().getId(),
                h.getOldStartDatetime(),
                h.getNewStartDatetime(),
                h.getOldEndDatetime(),
                h.getNewEndDatetime(),
                h.getOldStatus(),
                h.getNewStatus(),
                h.getChangeReason(),
                h.getChangedAt());
    }
}
