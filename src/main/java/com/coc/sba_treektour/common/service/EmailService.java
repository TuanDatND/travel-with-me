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
            log.warn("JavaMailSender or spring.mail.username is not configured. Skipping email to: {}", toEmail);
            return false;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail, "TerraPeak - Travel With Me");
            helper.setTo(toEmail);
            helper.setSubject("[TerraPeak] Internal Personnel Account Provisioned (" + role + ")");

            String htmlContent = """
                <div style="font-family: Arial, sans-serif; line-height: 1.6; color: #333; max-width: 600px; margin: auto; padding: 24px; border: 1px solid #e0e0e0; border-radius: 12px; background-color: #ffffff;">
                    <div style="text-align: center; margin-bottom: 20px;">
                        <span style="background-color: #e0f2fe; color: #0369a1; padding: 4px 12px; border-radius: 20px; font-size: 12px; font-weight: bold;">TRAVEL WITH ME</span>
                        <h2 style="color: #1e293b; margin-top: 8px;">Internal Personnel Account Provisioned</h2>
                    </div>
                    <p>Hello <strong>%s</strong>,</p>
                    <p>An administrator has provisioned an internal personnel account for you on <strong>Travel With Me (TerraPeak)</strong> with the following credentials:</p>
                    
                    <div style="background-color: #f8fafc; border: 1px solid #e2e8f0; padding: 16px; border-radius: 8px; margin: 20px 0;">
                        <p style="margin: 6px 0;"><strong>Role:</strong> <span style="color: #0284c7; font-weight: bold;">%s</span></p>
                        <p style="margin: 6px 0;"><strong>Login Email:</strong> %s</p>
                        <p style="margin: 6px 0;"><strong>Temporary Password:</strong> <span style="background-color: #fef3c7; color: #b45309; padding: 3px 8px; font-weight: bold; border-radius: 4px; font-family: monospace;">%s</span></p>
                    </div>

                    <p style="color: #475569; font-size: 14px;"><em>* Please use this temporary password to sign in to the system.</em></p>
                    
                    <hr style="border: none; border-top: 1px solid #f1f5f9; margin: 24px 0;">
                    <p style="font-size: 12px; color: #94a3b8; text-align: center;">For security reasons, please change your password upon your first sign-in. This is an automated email, please do not reply.</p>
                </div>
            """.formatted(fullName, role, toEmail, rawPassword);

            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("Sent internal account email to: {}", toEmail);
            return true;
        } catch (Exception e) {
            log.error("Failed to send internal account email to {}: {}", toEmail, e.getMessage());
            return false;
        }
    }

    public boolean sendCustomerGoogleWelcomeEmail(String toEmail, String fullName, String rawPassword) {
        if (mailSender == null || fromEmail == null || fromEmail.isBlank()) {
            log.warn("JavaMailSender or spring.mail.username is not configured. Skipping Google OAuth2 email to: {}", toEmail);
            return false;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail, "TerraPeak - Travel With Me");
            helper.setTo(toEmail);
            helper.setSubject("[TerraPeak] Welcome to TerraPeak & Your Temporary Password");

            String htmlContent = """
                <div style="font-family: Arial, sans-serif; line-height: 1.6; color: #333; max-width: 600px; margin: auto; padding: 24px; border: 1px solid #e0e0e0; border-radius: 12px; background-color: #ffffff;">
                    <div style="text-align: center; margin-bottom: 20px;">
                        <span style="background-color: #152A20; color: #D97845; padding: 4px 12px; border-radius: 20px; font-size: 12px; font-weight: bold;">TERRAPEAK OUTPOST</span>
                        <h2 style="color: #152A20; margin-top: 8px;">Welcome to TerraPeak!</h2>
                    </div>
                    <p>Hello <strong>%s</strong>,</p>
                    <p>Your customer account has been created via <strong>Google Sign-In</strong>. We have generated a temporary password so you can also sign in directly with your Email or update your password in your <strong>Climber Passport (Account Profile)</strong>:</p>
                    
                    <div style="background-color: #f8fafc; border: 1px solid #e2e8f0; padding: 16px; border-radius: 8px; margin: 20px 0;">
                        <p style="margin: 6px 0;"><strong>Role:</strong> <span style="color: #152A20; font-weight: bold;">CUSTOMER (Explorer Member)</span></p>
                        <p style="margin: 6px 0;"><strong>Login Email:</strong> %s</p>
                        <p style="margin: 6px 0;"><strong>Current (Temporary) Password:</strong> <span style="background-color: #fef3c7; color: #b45309; padding: 3px 8px; font-weight: bold; border-radius: 4px; font-family: monospace;">%s</span></p>
                    </div>

                    <p style="color: #475569; font-size: 14px;"><em>* To change your password on your Account Profile page, enter the temporary password above into the <strong>"Current Password"</strong> field.</em></p>
                    
                    <hr style="border: none; border-top: 1px solid #f1f5f9; margin: 24px 0;">
                    <p style="font-size: 12px; color: #94a3b8; text-align: center;">You can continue signing in with Google at any time. This is an automated email, please do not reply.</p>
                </div>
            """.formatted(fullName, toEmail, rawPassword);

            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("Sent Google OAuth2 welcome email to customer: {}", toEmail);
            return true;
        } catch (Exception e) {
            log.error("Failed to send Google OAuth2 welcome email to {}: {}", toEmail, e.getMessage());
            return false;
        }
    }
}
