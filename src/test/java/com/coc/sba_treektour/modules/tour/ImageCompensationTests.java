package com.coc.sba_treektour.modules.tour;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.coc.sba_treektour.modules.tour.entity.TourEvent;
import com.coc.sba_treektour.modules.tour.enums.EventType;
import com.coc.sba_treektour.modules.tour.repository.EventRepository;
import com.coc.sba_treektour.modules.tour.repository.ImageRepository;
import com.coc.sba_treektour.modules.tour.service.EventService;
import com.coc.sba_treektour.modules.tour.service.impl.EventServiceImpl;
import com.coc.sba_treektour.modules.tour.storage.CloudinaryImageStorage;
import com.coc.sba_treektour.modules.tour.validator.ImageValidator;
import com.coc.sba_treektour.modules.tour.exception.TourException;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Optional;

class ImageCompensationTests {
    /** Kiểm tra callback rollback xóa tài sản đã upload khi lưu metadata thất bại. */
    @Test
    void databaseRollbackRemovesUploadedAsset() {
        EventRepository events = mock(EventRepository.class);
        ImageRepository images = mock(ImageRepository.class);
        CloudinaryImageStorage storage = mock(CloudinaryImageStorage.class);
        ImageValidator validator = mock(ImageValidator.class);
        when(events.findForUpdate(1L))
                .thenReturn(Optional.of(new TourEvent("Test", EventType.TREKKING, BigDecimal.TEN)));
        when(validator.validate(any())).thenReturn(new byte[] {1});
        when(storage.upload(any()))
                .thenReturn(
                        new CloudinaryImageStorage.StoredImage(
                                "https://example.test/image.png", "events/orphan"));
        when(images.saveAndFlush(any())).thenThrow(new IllegalStateException("DB write failed"));
        EventService service = new EventServiceImpl(events, images, storage, validator);
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThatThrownBy(
                            () ->
                                    service.upload(
                                            1L,
                                            new MockMultipartFile("file", new byte[] {1}),
                                            null))
                    .isInstanceOf(IllegalStateException.class);
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            verify(storage).delete("events/orphan");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /** Mô phỏng HTTP Cloudinary để kiểm tra URL HTTPS, xóa lặp và chuyển lỗi thành HTTP 502. */
    @Test
    void cloudinaryRequestsKeepResponseValidationAndErrorMapping() {
        var storage = new CloudinaryImageStorage("demo", "key", "test-secret");
        var builder = RestClient.builder().baseUrl("https://api.cloudinary.com/v1_1/demo/image");
        var server = MockRestServiceServer.bindTo(builder).build();
        ReflectionTestUtils.setField(storage, "client", builder.build());
        server.expect(requestTo("https://api.cloudinary.com/v1_1/demo/image/upload"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(
                        withSuccess(
                                "{\"secure_url\":\"https://example.test/a.png\",\"public_id\":\"events/a\"}",
                                MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.cloudinary.com/v1_1/demo/image/destroy"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"result\":\"not found\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.cloudinary.com/v1_1/demo/image/upload"))
                .andRespond(
                        withSuccess(
                                "{\"secure_url\":\"http://insecure.test/a.png\",\"public_id\":\"events/a\"}",
                                MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.cloudinary.com/v1_1/demo/image/destroy"))
                .andRespond(withServerError());
        assertThat(storage.upload(new byte[] {1}).publicId()).isEqualTo("events/a");
        storage.delete("events/a");
        assertThatThrownBy(() -> storage.upload(new byte[] {1}))
                .isInstanceOfSatisfying(
                        TourException.class,
                        e -> assertThat(e.status()).isEqualTo(HttpStatus.BAD_GATEWAY));
        assertThatThrownBy(() -> storage.delete("events/a"))
                .isInstanceOfSatisfying(
                        TourException.class,
                        e -> assertThat(e.status()).isEqualTo(HttpStatus.BAD_GATEWAY));
        server.verify();
    }

    /**
     * Ảnh guide dùng thư mục riêng nhưng giữ định dạng multipart và xử lý response Cloudinary
     * chung.
     */
    @Test
    void guideAvatarUsesItsOwnCloudinaryFolder() {
        var storage = new CloudinaryImageStorage("demo", "key", "test-secret");
        var builder = RestClient.builder().baseUrl("https://api.cloudinary.com/v1_1/demo/image");
        var server = MockRestServiceServer.bindTo(builder).build();
        ReflectionTestUtils.setField(storage, "client", builder.build());
        server.expect(requestTo("https://api.cloudinary.com/v1_1/demo/image/upload"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(Matchers.containsString("terrapeak/guides/")))
                .andRespond(
                        withSuccess(
                                "{\"secure_url\":\"https://example.test/guide.png\",\"public_id\":\"terrapeak/guides/a\"}",
                                MediaType.APPLICATION_JSON));
        assertThat(storage.uploadGuideAvatar(new byte[] {1}).publicId())
                .isEqualTo("terrapeak/guides/a");
        server.verify();
    }
}
