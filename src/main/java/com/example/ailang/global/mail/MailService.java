package com.example.ailang.global.mail;

public interface MailService {
    void sendVerificationCode(String to, String code);
}
