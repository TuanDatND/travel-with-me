package com.coc.sba_treektour.tour;

import com.coc.sba_treektour.tour.entity.*;
import com.coc.sba_treektour.tour.repository.*;
import com.coc.sba_treektour.tour.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.*;
import java.util.Optional;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ImageCompensationTests {
    /** Kiểm tra callback rollback xóa tài sản đã upload khi lưu metadata thất bại. */
    @Test
    void databaseRollbackRemovesUploadedAsset() {
        EventRepository events = mock(EventRepository.class);
        ImageRepository images = mock(ImageRepository.class);
        CloudinaryImageStorage storage = mock(CloudinaryImageStorage.class);
        ImageValidator validator = mock(ImageValidator.class);
        when(events.findForUpdate(1L)).thenReturn(Optional.of(new TourEvent()));
        when(validator.validate(any())).thenReturn(new byte[] {1});
        when(storage.upload(any()))
                .thenReturn(
                        new CloudinaryImageStorage.StoredImage(
                                "https://example.test/image.png", "events/orphan"));
        when(images.saveAndFlush(any())).thenThrow(new IllegalStateException("DB write failed"));
        EventService service = new EventService(events, images, storage, validator);
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
}
