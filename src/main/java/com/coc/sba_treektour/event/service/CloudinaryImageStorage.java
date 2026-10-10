package com.coc.sba_treektour.event.service;

import com.coc.sba_treektour.tour.service.TourException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

@Component
// ponytail: chỉ có một nơi lưu ảnh; thêm interface khi cần nhà cung cấp thứ hai.
public class CloudinaryImageStorage {
    public record StoredImage(String secureUrl, String publicId) {}

    private final String cloudName, apiKey, apiSecret;
    private final RestClient client;

    /** Đọc cấu hình Cloudinary và tạo HTTP client có giới hạn thời gian kết nối, phản hồi. */
    public CloudinaryImageStorage(
            @Value("${tour.cloudinary.cloud-name:}") String cloudName,
            @Value("${tour.cloudinary.api-key:}") String apiKey,
            @Value("${tour.cloudinary.api-secret:}") String apiSecret) {
        this.cloudName = cloudName;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        var factory =
                new JdkClientHttpRequestFactory(
                        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        factory.setReadTimeout(Duration.ofSeconds(30));
        client =
                RestClient.builder()
                        .baseUrl("https://api.cloudinary.com/v1_1/" + cloudName + "/image")
                        .requestFactory(factory)
                        .build();
    }

    /** Từ chối thao tác lưu ảnh với lỗi 503 nếu thiếu thông tin cấu hình Cloudinary. */
    private void configured() {
        if (cloudName.isBlank() || apiKey.isBlank() || apiSecret.isBlank())
            throw new TourException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "STORAGE_NOT_CONFIGURED",
                    "Image storage is not configured");
    }

    /** Sắp xếp tham số và ký SHA-1 cùng timestamp; API secret chỉ sử dụng tại backend. */
    private LinkedMultiValueMap<String, Object> signed(TreeMap<String, String> parameters) {
        configured();
        parameters.put("timestamp", Long.toString(Instant.now().getEpochSecond()));
        String data =
                String.join(
                        "&",
                        parameters.entrySet().stream()
                                .map(e -> e.getKey() + "=" + e.getValue())
                                .toList());
        String signature;
        try {
            signature =
                    HexFormat.of()
                            .formatHex(
                                    MessageDigest.getInstance("SHA-1")
                                            .digest(
                                                    (data + apiSecret)
                                                            .getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        var body = new LinkedMultiValueMap<String, Object>();
        parameters.forEach(body::add);
        body.add("api_key", apiKey);
        body.add("signature", signature);
        return body;
    }

    /** Tải ảnh với publicId riêng; chỉ nhận response có URL HTTPS và mã tài sản. */
    public StoredImage upload(byte[] content) {
        var params = new TreeMap<String, String>();
        params.put("public_id", "terrapeak/events/" + UUID.randomUUID());
        var body = signed(params);
        body.add(
                "file",
                new ByteArrayResource(content) {
                    /** Cung cấp tên file cho dữ liệu multipart gửi lên Cloudinary. */
                    @Override
                    public String getFilename() {
                        return "image";
                    }
                });
        Map<?, ?> response = send("/upload", body);
        if (!(response.get("secure_url") instanceof String url)
                || !url.startsWith("https://")
                || !(response.get("public_id") instanceof String id)) throw storageFailure();
        return new StoredImage(url, id);
    }

    /** Xóa tài sản và yêu cầu xóa cache CDN; bỏ qua ảnh cũ không có publicId hoặc tài sản đã mất. */
    public void delete(String publicId) {
        if (publicId == null)
            return; // Ảnh cũ từ schema chung không có tài sản Cloudinary do module quản lý.
        var params = new TreeMap<String, String>();
        params.put("public_id", publicId);
        params.put("invalidate", "true");
        var body = signed(params);
        Map<?, ?> response = send("/destroy", body);
        if (!("ok".equals(response.get("result")) || "not found".equals(response.get("result"))))
            throw storageFailure();
    }

    /** Gửi multipart đến Cloudinary; chuyển lỗi HTTP, lỗi đọc hoặc response rỗng thành lỗi 502. */
    private Map<?, ?> send(String operation, LinkedMultiValueMap<String, Object> body) {
        try {
            Map<?, ?> response =
                    client.post()
                            .uri(operation)
                            .contentType(MediaType.MULTIPART_FORM_DATA)
                            .body(body)
                            .retrieve()
                            .body(Map.class);
            if (response == null) throw storageFailure();
            return response;
        } catch (RuntimeException e) {
            throw storageFailure();
        }
    }

    /** Tạo lỗi lưu trữ thống nhất, không tiết lộ chi tiết nội bộ hoặc thông tin kết nối. */
    private TourException storageFailure() {
        return new TourException(
                HttpStatus.BAD_GATEWAY,
                "STORAGE_ERROR",
                "Image storage request failed; retry later");
    }
}
