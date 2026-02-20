package com.example.ailang.global.mail;

import com.example.ailang.global.exception.ApplicationException;
import com.example.ailang.global.exception.ErrorCode;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailServiceImpl implements MailService {

    private final JavaMailSender javaMailSender;

    @Override
    public void sendVerificationCode(String to, String code) {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setTo(to);
            helper.setSubject("[AILang] 이메일 인증 코드");
            helper.setText(buildEmailContent(code), true);
            javaMailSender.send(message);
        } catch (Exception e) {
            log.error("이메일 발송 실패: {}", to, e);
            throw new ApplicationException(ErrorCode.EMAIL_SENDING_FAILED);
        }
    }

    private String buildEmailContent(String code) {
        return "<div style='font-family: Arial, sans-serif;'>" +
            "<h2>AILang 이메일 인증</h2>" +
            "<p>아래 인증 코드를 입력해 주세요. (5분 유효)</p>" +
            "<h1 style='color:#4F46E5; letter-spacing:8px;'>" + code + "</h1>" +
            "</div>";
    }
}
