package com.coc.sba_treektour.event.service;

import com.coc.sba_treektour.tour.service.TourException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.Set;

@Component
public class ImageValidator {
    public static final long MAX_SIZE = 5L * 1024 * 1024;

    /** Kiểm tra file không rỗng, tối đa 5 MiB và MIME khớp chữ ký JPEG/PNG/WebP trước khi upload. */
    public byte[] validate(MultipartFile file) {
        if (file.isEmpty()) throw TourException.invalid("Image must not be empty");
        if (file.getSize() > MAX_SIZE)
            throw new TourException(
                    HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "Maximum image size is 5 MB");
        if (!Set.of("image/jpeg", "image/png", "image/webp")
                .contains(String.valueOf(file.getContentType())))
            throw TourException.invalid("Only JPEG, PNG and WebP images are supported");
        try {
            byte[] data = file.getBytes();
            // Nhận dạng định dạng từ dữ liệu, không chỉ tin MIME do client gửi.
            String detected = detect(data);
            if (!detected.equals(file.getContentType()))
                throw TourException.invalid("Image content does not match its MIME type");
            // Cloudinary giải mã toàn bộ ảnh; bước này chặn dữ liệu sai chữ ký trước khi gửi.
            return data;
        } catch (IOException e) {
            throw TourException.invalid("Cannot read uploaded image");
        }
    }

    /** Nhận dạng định dạng từ phần đầu dữ liệu; Cloudinary giải mã và kiểm tra toàn bộ ảnh. */
    private String detect(byte[] d) {
        if (d.length >= 12 && d[0] == (byte) 0xff && d[1] == (byte) 0xd8 && d[2] == (byte) 0xff)
            return "image/jpeg";
        if (d.length >= 24
                && d[0] == (byte) 0x89
                && d[1] == 80
                && d[2] == 78
                && d[3] == 71
                && d[4] == 13
                && d[5] == 10
                && d[6] == 26
                && d[7] == 10) return "image/png";
        if (d.length >= 20
                && d[0] == 'R'
                && d[1] == 'I'
                && d[2] == 'F'
                && d[3] == 'F'
                && d[8] == 'W'
                && d[9] == 'E'
                && d[10] == 'B'
                && d[11] == 'P') return "image/webp";
        throw TourException.invalid("Invalid image content");
    }
}
