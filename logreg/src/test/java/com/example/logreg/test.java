package com.example.logreg;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@SpringBootTest
public class test {
    @Autowired
    private JavaMailSender sender;
    private String header = "Test";
    private String content = "<h1>This is a test</h1>";
    private String to = "18734770952@163.com";
    private String from = "mingtops@163.com";

    @Test
    public void testSend(){
        SimpleMailMessage message = new SimpleMailMessage();
        message.setSubject( header);
        message.setText(content);
        message.setTo(to);
        message.setFrom(from);
        sender.send(message);
    }

}
