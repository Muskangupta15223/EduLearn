package com.olp.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Async
    public void sendWelcomeEmail(String to, String name) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("Welcome to EduLearn LMS!");
        message.setText("Hello " + name + ",\n\n" +
                "Welcome to EduLearn! We're excited to have you on board.\n" +
                "Start exploring courses and enhancing your skills today.\n\n" +
                "Best regards,\n" +
                "The EduLearn Team");

        try {
            mailSender.send(message);
            log.info("Welcome email sent to {}", to);
        } catch (Exception e) {
            log.warn("Failed to send welcome email to {}", to, e);
        }
    }

    @Async
    public void sendPasswordResetEmail(String to, String name, String resetLink) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("EduLearn - Reset Your Password");
        message.setText("Hello " + name + ",\n\n" +
                "We received a request to reset your password.\n\n" +
                "Click the link below to set a new password:\n" +
                resetLink + "\n\n" +
                "This link will expire in 1 hour.\n" +
                "If you didn't request this, you can safely ignore this email.\n\n" +
                "Best regards,\n" +
                "The EduLearn Team");

        try {
            mailSender.send(message);
            log.info("Password reset email sent to {}", to);
        } catch (Exception e) {
            log.warn("Failed to send password reset email to {}", to, e);
        }
    }
}
