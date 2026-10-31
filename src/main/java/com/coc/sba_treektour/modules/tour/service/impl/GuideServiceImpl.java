package com.coc.sba_treektour.modules.tour.service.impl;

import com.coc.sba_treektour.modules.tour.storage.CloudinaryImageStorage;
import com.coc.sba_treektour.modules.tour.validator.ImageValidator;
import com.coc.sba_treektour.modules.tour.dto.request.GuideCreateInput;
import com.coc.sba_treektour.modules.tour.dto.request.GuideUpdateInput;
import com.coc.sba_treektour.modules.tour.dto.response.AdminGuideResponse;
import com.coc.sba_treektour.modules.tour.dto.response.GuideReference;
import com.coc.sba_treektour.modules.tour.dto.response.PageResponse;
import com.coc.sba_treektour.modules.tour.dto.response.PublicGuideResponse;
import com.coc.sba_treektour.modules.tour.entity.TourGuide;
import com.coc.sba_treektour.modules.tour.enums.GuideStatus;
import com.coc.sba_treektour.modules.tour.exception.TourException;
import com.coc.sba_treektour.modules.tour.repository.AccountReadRepository;
import com.coc.sba_treektour.modules.tour.repository.GuideRepository;
import com.coc.sba_treektour.modules.tour.service.GuideService;
import com.coc.sba_treektour.modules.tour.util.TourQueries;

import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GuideServiceImpl implements GuideService {
    private static final Logger log = LoggerFactory.getLogger(GuideServiceImpl.class);
    private final GuideRepository guides;
    private final AccountReadRepository accounts;
    private final CloudinaryImageStorage storage;
    private final ImageValidator validator;

    /**
     * Phân trang hồ sơ ACTIVE có tài khoản ACTIVE; đọc tên tài khoản theo lô để tránh truy vấn lặp.
     */
    @Override
    public PageResponse<PublicGuideResponse> listPublic(String specialization, int page, int size) {
        var result =
                guides.findPublic(
                        TourQueries.contains(specialization),
                        TourQueries.page(page, size, Sort.by(Sort.Direction.DESC, "guide_id")));
        var users = accounts.findAll(result.stream().map(TourGuide::getUserId).distinct().toList());
        return PageResponse.of(result.map(g -> publicResponse(g, users.get(g.getUserId()))));
    }

    /** Lọc hồ sơ quản trị theo chuyên môn, trạng thái hoặc userId. */
    @Override
    public PageResponse<AdminGuideResponse> listAdmin(
            String specialization, GuideStatus status, Long userId, int page, int size) {
        String pattern = TourQueries.contains(specialization);
        var result =
                guides.findFiltered(
                        pattern,
                        status,
                        userId,
                        TourQueries.page(page, size, Sort.by(Sort.Direction.DESC, "id")));
        var users = accounts.findAll(result.stream().map(TourGuide::getUserId).distinct().toList());
        return PageResponse.of(result.map(g -> adminResponse(g, users.get(g.getUserId()))));
    }

    /** Lấy hồ sơ công khai; trả 404 nếu hồ sơ hoặc tài khoản đã ngừng hoạt động. */
    @Override
    public PublicGuideResponse getPublic(Long id) {
        TourGuide g = find(id);
        var user = account(g.getUserId());
        if (g.getStatus() != GuideStatus.ACTIVE || !user.active()) throw TourException.missing();
        return publicResponse(g, user);
    }

    /**
     * Lấy hồ sơ quản trị, gồm userId và trạng thái nhưng không chứa thông tin tài khoản nhạy cảm.
     */
    @Override
    public AdminGuideResponse getAdmin(Long id) {
        TourGuide g = find(id);
        return adminResponse(g, account(g.getUserId()));
    }

    /** Trả guideId, userId và trạng thái để module lịch kiểm tra phân công theo cùng một người. */
    @Override
    public GuideReference reference(Long id) {
        TourGuide g = find(id);
        return new GuideReference(
                g.getId(), g.getUserId(), g.getStatus(), account(g.getUserId()).active());
    }

    /**
     * Tạo hồ sơ cho tài khoản ACTIVE; unique index của DB chặn chuyên môn trùng trên cùng userId.
     */
    @Transactional
    @Override
    public AdminGuideResponse create(GuideCreateInput input) {
        var user = requireActiveAccount(input.userId());
        accounts.syncGuideRole(input.userId());
        // ponytail: unique index chặn chuyên môn trùng, kể cả khi ghi đồng thời.
        TourGuide g =
                new TourGuide(
                        input.userId(), input.experienceYears(), input.specialization().trim());
        applyProfile(g, input.bio(), input.languages());
        return adminResponse(guides.saveAndFlush(g), user);
    }

    /** Cập nhật kinh nghiệm và chuyên môn; giữ nguyên userId và để DB chặn chuyên môn trùng. */
    @Transactional
    @Override
    public AdminGuideResponse update(Long id, GuideUpdateInput input) {
        TourGuide g = locked(id);
        g.setExperienceYears(input.experienceYears());
        g.setSpecialization(input.specialization().trim());
        applyProfile(g, input.bio(), input.languages());
        guides.flush();
        return adminResponse(g, account(g.getUserId()));
    }

    /** Kích hoạt hoặc ngừng hồ sơ; chỉ kích hoạt khi tài khoản liên kết đang hoạt động. */
    @Transactional
    @Override
    public AdminGuideResponse status(Long id, GuideStatus status) {
        TourGuide g = locked(id);
        var user =
                status == GuideStatus.ACTIVE
                        ? requireActiveAccount(g.getUserId())
                        : account(g.getUserId());
        if (status == GuideStatus.ACTIVE) {
            accounts.syncGuideRole(g.getUserId());
        }
        g.setStatus(status);
        guides.flush();
        return adminResponse(g, user);
    }

    /** Đọc thông tin tài khoản tối thiểu; userId không tồn tại trả lỗi nghiệp vụ 400. */
    private AccountReadRepository.Account account(Long id) {
        var account = accounts.findAll(List.of(id)).get(id);
        if (account == null) throw TourException.invalid("Account does not exist");
        return account;
    }

    /** Chỉ cho tạo hoặc kích hoạt hồ sơ khi tài khoản đang hoạt động và không phải nhân viên chi nhánh đang hoạt động. */
    private AccountReadRepository.Account requireActiveAccount(Long id) {
        var account = account(id);
        if (!account.active()) throw TourException.conflict("Account is inactive");
        if (account.activeStaff()) {
            throw TourException.conflict(
                    "This account is currently an active branch Staff (STAFF) member! Please remove or lock the staff member before activating the Tour Guide profile.");
        }
        return account;
    }

    /** Chuẩn hóa giới thiệu và ngôn ngữ; request cũ không gửi trường mới vẫn được chấp nhận. */
    private void applyProfile(TourGuide guide, String bio, List<String> languages) {
        guide.setBio(bio == null || bio.isBlank() ? null : bio.trim());
        guide.setLanguages(
                languages == null
                        ? new String[0]
                        : languages.stream().map(String::trim).distinct().toArray(String[]::new));
    }

    /** Khóa hồ sơ trước khi cập nhật hoặc thay ảnh đại diện. */
    private TourGuide locked(Long id) {
        return guides.findForUpdate(id).orElseThrow(TourException::missing);
    }

    /** Upload ảnh mới; chỉ xóa ảnh cũ sau commit, dọn ảnh mới nếu giao dịch rollback. */
    @Transactional
    @Override
    public AdminGuideResponse uploadAvatar(Long id, MultipartFile file) {
        TourGuide guide = locked(id);
        var account = account(guide.getUserId());
        var uploaded = storage.uploadGuideAvatar(validator.validate(file));
        String previous = guide.getAvatarPublicId();
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    /** Chỉ bỏ ảnh cũ khi DB đã lưu ảnh thay thế thành công. */
                    @Override
                    public void afterCommit() {
                        cleanupAvatar(previous);
                    }

                    /** Dọn tài sản mới nếu DB không commit, kể cả lỗi tại thời điểm commit. */
                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) cleanupAvatar(uploaded.publicId());
                    }
                });
        guide.setAvatarUrl(uploaded.secureUrl());
        guide.setAvatarPublicId(uploaded.publicId());
        guides.flush();
        return adminResponse(guide, account);
    }

    /** Xóa tài sản trước metadata; nếu Cloudinary lỗi thì giữ nguyên thông tin ảnh trong DB. */
    @Transactional
    @Override
    public void deleteAvatar(Long id) {
        TourGuide guide = locked(id);
        storage.delete(guide.getAvatarPublicId());
        guide.setAvatarUrl(null);
        guide.setAvatarPublicId(null);
        guides.flush();
    }

    /** Ghi publicId để dọn thủ công nếu Cloudinary lỗi sau commit hoặc trong lúc rollback. */
    private void cleanupAvatar(String publicId) {
        if (publicId == null) return;
        try {
            storage.delete(publicId);
        } catch (RuntimeException exception) {
            log.error("Cloudinary cleanup required for guide avatar publicId={}", publicId);
        }
    }

    /** Tìm hồ sơ theo guideId hoặc trả lỗi 404. */
    private TourGuide find(Long id) {
        return guides.findById(id).orElseThrow(TourException::missing);
    }

    /** Tạo hồ sơ công khai chỉ gồm tên hiển thị và thông tin nghề nghiệp. */
    private PublicGuideResponse publicResponse(TourGuide g, AccountReadRepository.Account u) {
        return new PublicGuideResponse(
                g.getId(),
                u.displayName(),
                g.getExperienceYears(),
                g.getSpecialization(),
                g.getBio(),
                List.copyOf(Arrays.asList(g.getLanguages())),
                g.getAvatarUrl());
    }

    /** Tạo hồ sơ quản trị với userId và trạng thái; không trả dữ liệu riêng tư của tài khoản. */
    private AdminGuideResponse adminResponse(TourGuide g, AccountReadRepository.Account u) {
        return new AdminGuideResponse(
                g.getId(),
                g.getUserId(),
                u.displayName(),
                g.getExperienceYears(),
                g.getSpecialization(),
                g.getStatus(),
                g.getBio(),
                List.copyOf(Arrays.asList(g.getLanguages())),
                g.getAvatarUrl());
    }
}
