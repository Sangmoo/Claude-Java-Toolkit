package io.github.claudetoolkit.ui.email;

import io.github.claudetoolkit.ui.config.ToolkitSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import javax.mail.internet.MimeMessage;
import java.util.Properties;

/**
 * Sends email notifications for scheduled job results.
 * Uses settings from {@link ToolkitSettings.Email} at send-time
 * (no restart required after SMTP config change).
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final ToolkitSettings settings;

    public EmailService(ToolkitSettings settings) {
        this.settings = settings;
    }

    /**
     * Send a plain HTML email. No-op if email is not configured.
     *
     * @param to      recipient address
     * @param subject email subject
     * @param body    plain text body
     */
    public void sendJobResult(String to, String subject, String body) {
        if (!settings.isEmailConfigured()) return;
        if (to == null || to.trim().isEmpty()) return;

        ToolkitSettings.Email cfg = settings.getEmail();

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(cfg.getHost());
        sender.setPort(cfg.getPort());
        sender.setUsername(cfg.getUsername());
        sender.setPassword(cfg.getPassword());

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        if (cfg.isTls()) {
            props.put("mail.smtp.starttls.enable", "true");
        }
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.connectiontimeout", "10000");

        try {
            MimeMessage msg = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, false, "UTF-8");
            String from = (cfg.getFrom() != null && !cfg.getFrom().isEmpty())
                    ? cfg.getFrom() : cfg.getUsername();
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);
            sender.send(msg);
        } catch (Exception e) {
            log.error("[EmailService] 이메일 발송 실패 → " + to + ": " + e.getMessage());
        }
    }
}
