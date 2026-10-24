package com.coc.sba_treektour.modules.tour;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.coc.sba_treektour.modules.event.service.CloudinaryImageStorage;
import com.coc.sba_treektour.modules.event.service.ImageValidator;
import com.coc.sba_treektour.modules.tour.entity.TourGuide;
import com.coc.sba_treektour.modules.tour.repository.GuideRepository;
import com.coc.sba_treektour.modules.tour.service.*;

import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.*;

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
        var accounts = mock(AccountReader.class);
        var validator = mock(ImageValidator.class);
        guide = new TourGuide();
        guide.setId(1L);
        guide.setUserId(2L);
        guide.setAvatarUrl("https://example.test/old.png");
        guide.setAvatarPublicId("guides/old");
        when(guides.findForUpdate(1L)).thenReturn(Optional.of(guide));
        when(accounts.find(2L)).thenReturn(new AccountReader.Account(2L, "Guide", true));
        when(validator.validate(any())).thenReturn(new byte[] {1});
        when(storage.uploadGuideAvatar(any()))
                .thenReturn(
                        new CloudinaryImageStorage.StoredImage(
                                "https://example.test/new.png", "guides/new"));
        service = new GuideService(guides, accounts, storage, validator);
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
}
