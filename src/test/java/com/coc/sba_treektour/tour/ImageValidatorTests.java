package com.coc.sba_treektour.tour;

import com.coc.sba_treektour.tour.service.*;
import com.coc.sba_treektour.event.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.HttpStatus;
import static org.assertj.core.api.Assertions.*;

class ImageValidatorTests {
    private final ImageValidator validator = new ImageValidator();

    /** Kiểm tra file lớn hơn 5 MiB bị từ chối với HTTP 413 trước khi upload. */
    @Test
    void rejectsLargeFileBeforeStorage() {
        var file =
                new MockMultipartFile("file", "a.png", "image/png", new byte[5 * 1024 * 1024 + 1]);
        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOfSatisfying(
                        TourException.class,
                        e -> assertThat(e.status()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE));
    }

    /** Kiểm tra file rỗng và định dạng không hỗ trợ bị từ chối. */
    @Test
    void rejectsEmptyAndUnsupportedFiles() {
        assertThatThrownBy(() -> validator.validate(new MockMultipartFile("file", new byte[0])))
                .isInstanceOf(TourException.class);
        assertThatThrownBy(
                        () ->
                                validator.validate(
                                        new MockMultipartFile(
                                                "file",
                                                "a.svg",
                                                "image/svg+xml",
                                                "<svg/>".getBytes())))
                .isInstanceOf(TourException.class);
    }
}
