package com.coc.sba_treektour.common.service;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    public EmailService(@Autowired(required = false) JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public boolean sendInternalAccountEmail(String toEmail, String fullName, String role, String rawPassword) {
        if (mailSender == null || fromEmail == null || fromEmail.isBlank()) {
            log.warn("JavaMailSender hoặc spring.mail.username chưa được cấu hình. Bỏ qua gửi email tới: {}", toEmail);
            return false;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail, "Travel With Me - Hệ thống quản lý");
            helper.setTo(toEmail);
            helper.setSubject("[Travel With Me] Cấp tài khoản nhân sự nội bộ (" + role + ")");

            String htmlContent = """
                <div style="font-family: Arial, sans-serif; line-height: 1.6; color: #333; max-width: 600px; margin: auto; padding: 24px; border: 1px solid #e0e0e0; border-radius: 12px; background-color: #ffffff;">
                    <div style="text-align: center; margin-bottom: 20px;">
                        <span style="background-color: #e0f2fe; color: #0369a1; padding: 4px 12px; border-radius: 20px; font-size: 12px; font-weight: bold;">TRAVEL WITH ME</span>
                        <h2 style="color: #1e293b; margin-top: 8px;">Cấp Tài Khoản Nhân Sự Mới</h2>
                    </div>
                    <p>Xin chào <strong>%s</strong>,</p>
                    <p>Bạn vừa được Quản trị viên cấp tài khoản nhân sự trên hệ thống <strong>Travel With Me</strong> với thông tin sau:</p>
                    
                    <div style="background-color: #f8fafc; border: 1px solid #e2e8f0; padding: 16px; border-radius: 8px; margin: 20px 0;">
                        <p style="margin: 6px 0;"><strong>Vai trò (Role):</strong> <span style="color: #0284c7; font-weight: bold;">%s</span></p>
                        <p style="margin: 6px 0;"><strong>Tên đăng nhập (Email):</strong> %s</p>
                        <p style="margin: 6px 0;"><strong>Mật khẩu tạm thời:</strong> <span style="background-color: #fef3c7; color: #b45309; padding: 3px 8px; font-weight: bold; border-radius: 4px; font-family: monospace;">%s</span></p>
                    </div>

                    <p style="color: #475569; font-size: 14px;"><em>* Vui lòng sử dụng mật khẩu này để đăng nhập vào hệ thống.</em></p>
                    
                    <hr style="border: none; border-top: 1px solid #f1f5f9; margin: 24px 0;">
                    <p style="font-size: 12px; color: #94a3b8; text-align: center;">Vì lý do an toàn, bạn nên đổi mật khẩu ngay trong lần đầu tiên sử dụng. Thư này được gửi tự động, vui lòng không trả lời.</p>
                </div>
            """.formatted(fullName, role, toEmail, rawPassword);

            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("Đã gửi email cấp tài khoản nội bộ tới: {}", toEmail);
            return true;
        } catch (Exception e) {
            log.error("Gửi email cấp tài khoản thất bại tới {}: {}", toEmail, e.getMessage());
            return false;
        }
    }
}
