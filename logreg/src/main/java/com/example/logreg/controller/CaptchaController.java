package com.example.logreg.controller;

import com.example.logreg.dto.*;
import com.example.logreg.message.Messages;
import com.fasterxml.jackson.databind.JsonDeserializer;
import jakarta.annotation.Resource;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.web.bind.annotation.*;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api")
public class CaptchaController {
    @Resource
    private JavaMailSender sender;
    @Resource
    private RedisTemplate<String,String> redisTemplate;

    private static final String HEADER = "验证码";
    private static final String CONTENT_PREFIX = "<div style=\"background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); padding: 30px; border-radius: 10px; max-width: 500px; margin: 0 auto; font-family: 'Arial', sans-serif;\">"
            + "    <div style=\"background: white; padding: 30px; border-radius: 8px; box-shadow: 0 4px 15px rgba(0,0,0,0.1); text-align: center;\">"
            + "        <h3 style=\"color: #333; font-size: 24px; margin-bottom: 20px; font-weight: bold;\">邮箱验证码</h3>"
            + "        <p style=\"color: #666; font-size: 16px; margin-bottom: 25px;\">您的验证码是：</p>"
            + "        <h1 style=\"color: #667eea; font-size: 36px; letter-spacing: 5px; margin: 20px 0; font-weight: bold; background: #f8f9fa; padding: 15px; border-radius: 5px; display: inline-block;\">";

    private static final String CONTENT_SUFFIX = "</h1>"
            + "        <p style=\"color: #ff6b6b; font-size: 14px; margin-top: 25px; font-weight: bold;\">验证码5分钟内有效，请尽快使用</p>"
            + "        <div style=\"margin-top: 30px; padding-top: 20px; border-top: 1px solid #eee; color: #999; font-size: 12px;\">"
            + "            <p>如非本人操作，请忽略此邮件</p>"
            + "        </div>"
            + "    </div>"
            + "</div>";

    @Value("${spring.mail.username}")
    private String from;

    @PostMapping("/user/captcha")
    public MessageDTO getCaptcha(@RequestBody CaptchaDTO captchaDTO) {
        String email = captchaDTO.getEmail();
        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message);
        try {
            helper.setSubject(HEADER);
            Random random = new Random();
            int code = random.nextInt(900000) + 100000;
            helper.setText(CONTENT_PREFIX + code + CONTENT_SUFFIX, true);
            helper.setTo(email);
            helper.setFrom(from);
            sender.send(message);
            redisTemplate.opsForValue().set(email,String.valueOf(code), 5000, TimeUnit.MINUTES);
            return new SuccessDTO<>(Messages.SUCCESS, code);
        } catch (Exception e) {
            e.printStackTrace();
            return new FailureDTO<>(Messages.CONFLICT, null);
        }
    }

    @PostMapping("/user/captcha/verify")
    public MessageDTO verifyCaptcha(@RequestBody VerifyDTO verifyDTO) {
        String email = verifyDTO.getEmail();
        String code = verifyDTO.getCode();
        String redisCode = redisTemplate.opsForValue().get(email);
        if (redisCode == null) {
            return new FailureDTO<>(Messages.NOT_FOUND, "请先发送验证码");
        }
        if (redisCode.equals(code)) {
            redisTemplate.delete(email);
            return new SuccessDTO<>(Messages.SUCCESS, null);
        }
        return new FailureDTO<>(Messages.CONFLICT, "验证码错误");
    }
}
