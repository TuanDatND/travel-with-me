package com.coc.sba_treektour.modules.tour;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.coc.sba_treektour.modules.tour.storage.CloudinaryImageStorage;
import com.coc.sba_treektour.modules.tour.validator.ImageValidator;
import com.coc.sba_treektour.modules.tour.entity.TourGuide;
import com.coc.sba_treektour.modules.tour.enums.GuideStatus;
import com.coc.sba_treektour.modules.tour.exception.TourException;
import com.coc.sba_treektour.modules.tour.repository.AccountReadRepository;
import com.coc.sba_treektour.modules.tour.repository.GuideRepository;
import com.coc.sba_treektour.modules.tour.service.GuideService;
import com.coc.sba_treektour.modules.tour.service.impl.GuideServiceImpl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.Optional;

class GuideAvatarTransactionTests {
    private GuideRepository guides;
    private CloudinaryImageStorage storage;
    private GuideService service;
    private TourGuide guide;

    /** Tạo hồ sơ có ảnh cũ và mô phỏng giao dịch để kiểm tra chính xác thời điểm dọn ảnh. */
    @BeforeEach
    void setup() {
        guides = mock(GuideRepository.class);
        storage = mock(CloudinaryImageStorage.class);
        var accounts = mock(AccountReadRepository.class);
        var validator = mock(ImageValidator.class);
        guide = new TourGuide(2L, 0, "Test");
        ReflectionTestUtils.setField(guide, "id", 1L);
        guide.setAvatarUrl("https://example.test/old.png");
        guide.setAvatarPublicId("guides/old");
        when(guides.findForUpdate(1L)).thenReturn(Optional.of(guide));
        when(accounts.findAll(List.of(2L)))
                .thenReturn(Map.of(2L, new AccountReadRepository.Account(2L, "Guide", true)));
        when(validator.validate(any())).thenReturn(new byte[] {1});
        when(storage.uploadGuideAvatar(any()))
                .thenReturn(
                        new CloudinaryImageStorage.StoredImage(
                                "https://example.test/new.png", "guides/new"));
        service = new GuideServiceImpl(guides, accounts, storage, validator);
        TransactionSynchronizationManager.initSynchronization();
    }

    /** Không để trạng thái mô phỏng giao dịch ảnh hưởng bài kiểm thử tiếp theo. */
    @AfterEach
    void cleanup() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    /** DB lỗi sau upload: dọn ảnh mới, giữ tài sản cũ trên Cloudinary. */
    @Test
    void rollbackCleansNewAssetWithoutDeletingOldAsset() {
        doThrow(new IllegalStateException("DB failure")).when(guides).flush();
        assertThatThrownBy(
                        () ->
                                service.uploadAvatar(
                                         1L, new MockMultipartFile("file", new byte[] {1})))
                .isInstanceOf(IllegalStateException.class);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(storage).delete("guides/new");
        verify(storage, never()).delete("guides/old");
    }

    /** Chỉ xóa ảnh cũ sau commit, không xóa ảnh mới khi giao dịch đã thành công. */
    @Test
    void commitCleansOldAssetAfterMetadataIsSaved() {
        service.uploadAvatar(1L, new MockMultipartFile("file", new byte[] {1}));
        verify(storage, never()).delete(any());
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(
                        s -> {
                            s.afterCommit();
                            s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
                        });
        verify(storage).delete("guides/old");
        verify(storage, never()).delete("guides/new");
        assertThat(guide.getAvatarPublicId()).isEqualTo("guides/new");
    }

    /** Lỗi dọn ảnh cũ không biến giao dịch đã commit thành lỗi giả; publicId được ghi log. */
    @Test
    void cleanupFailureDoesNotUndoCommittedProfile() {
        doThrow(new IllegalStateException("Cloudinary unavailable"))
                .when(storage)
                .delete("guides/old");
        service.uploadAvatar(1L, new MockMultipartFile("file", new byte[] {1}));
        assertThatCode(
                        () ->
                                TransactionSynchronizationManager.getSynchronizations()
                                        .forEach(TransactionSynchronization::afterCommit))
                .doesNotThrowAnyException();
        assertThat(guide.getAvatarPublicId()).isEqualTo("guides/new");
    }

    @Test
    void activateGuideFailsWhenAccountIsActiveStaff() {
        var accounts = mock(AccountReadRepository.class);
        when(accounts.findAll(List.of(2L)))
                .thenReturn(Map.of(2L, new AccountReadRepository.Account(2L, "Staff User", true, true, false)));
        var svc = new GuideServiceImpl(guides, accounts, storage, mock(ImageValidator.class));
        guide.setStatus(GuideStatus.INACTIVE);

        assertThatThrownBy(() -> svc.status(1L, GuideStatus.ACTIVE))
                .isInstanceOf(TourException.class)
                .hasMessageContaining("Staff (STAFF)");
    }

    @Test
    void activateGuideFailsWhenAccountIsBlocked() {
        var accounts = mock(AccountReadRepository.class);
        when(accounts.findAll(List.of(2L)))
                .thenReturn(Map.of(2L, new AccountReadRepository.Account(2L, "Blocked User", false, false, true)));
        var svc = new GuideServiceImpl(guides, accounts, storage, mock(ImageValidator.class));
        guide.setStatus(GuideStatus.INACTIVE);

        assertThatThrownBy(() -> svc.status(1L, GuideStatus.ACTIVE))
                .isInstanceOf(TourException.class)
                .hasMessageContaining("blocked");
    }

    @Test
    void activateGuideSucceedsAndSyncsRoleEvenIfAccountWasInactive() {
        var accounts = mock(AccountReadRepository.class);
        when(accounts.findAll(List.of(2L)))
                .thenReturn(Map.of(2L, new AccountReadRepository.Account(2L, "Inactive Guide User", false, false, false)));
        var svc = new GuideServiceImpl(guides, accounts, storage, mock(ImageValidator.class));
        guide.setStatus(GuideStatus.INACTIVE);

        svc.status(1L, GuideStatus.ACTIVE);
        verify(accounts).syncGuideRole(2L);
        assertThat(guide.getStatus()).isEqualTo(GuideStatus.ACTIVE);
    }

    @Test
    void deactivateGuideCallsDeactivateGuideUser() {
        var accounts = mock(AccountReadRepository.class);
        when(accounts.findAll(List.of(2L)))
                .thenReturn(Map.of(2L, new AccountReadRepository.Account(2L, "Guide User", true, false, false)));
        var svc = new GuideServiceImpl(guides, accounts, storage, mock(ImageValidator.class));

        svc.status(1L, GuideStatus.INACTIVE);
        verify(accounts).deactivateGuideUser(2L);
        assertThat(guide.getStatus()).isEqualTo(GuideStatus.INACTIVE);
    }
}
