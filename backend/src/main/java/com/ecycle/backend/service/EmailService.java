package com.ecycle.backend.service;

import com.ecycle.backend.config.AppProps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private final ObjectProvider<JavaMailSender> sender; private final AppProps props;
    public EmailService(ObjectProvider<JavaMailSender> sender, AppProps props) { this.sender = sender; this.props = props; }

    /** Sends best-effort email. Failures are logged (never swallowed silently) and never break the business transaction. */
    public void send(String to, String subject, String body) {
        if (!props.isMailEnabled()) { log.debug("Mail disabled; skipped email to {} ({})", to, subject); return; }
        JavaMailSender s = sender.getIfAvailable();
        if (s == null) { log.warn("Mail enabled but no JavaMailSender configured; email to {} not sent", to); return; }
        try {
            SimpleMailMessage m = new SimpleMailMessage();
            m.setFrom(props.getMailFrom()); m.setTo(to); m.setSubject(subject); m.setText(body);
            s.send(m);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }
}
