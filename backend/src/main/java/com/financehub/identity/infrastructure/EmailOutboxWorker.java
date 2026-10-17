package com.financehub.identity.infrastructure;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** At-least-once SMTP delivery. A crash after send may duplicate an email, never a valid challenge. */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="financehub.mail.enabled",havingValue="true",matchIfMissing=true)
public class EmailOutboxWorker {
    private final JdbcTemplate jdbc;
    private final JavaMailSender mail;
    private final Clock clock;
    private final String sender;
    public EmailOutboxWorker(JdbcTemplate jdbc,JavaMailSender mail,Clock clock,@Value("${financehub.mail.from}") String sender) {
        this.jdbc=jdbc; this.mail=mail; this.clock=clock; this.sender=sender;
    }
    @Scheduled(fixedDelayString="${financehub.mail.poll-ms:5000}")
    @Transactional
    public void deliverOne() {
        jdbc.update("DELETE FROM email_outbox WHERE expires_at<=?",Timestamp.from(clock.instant()));
        var rows=jdbc.query("SELECT * FROM email_outbox WHERE next_attempt_at<=? ORDER BY next_attempt_at,id LIMIT 1 FOR UPDATE SKIP LOCKED",
            (rs,n)->new Pending(rs.getObject("id",UUID.class),rs.getString("recipient"),rs.getString("subject"),rs.getString("body"),rs.getInt("attempts")),
            Timestamp.from(clock.instant()));
        if(rows.isEmpty()) return;
        var pending=rows.getFirst();
        var message=new SimpleMailMessage();
        message.setFrom(sender); message.setTo(pending.recipient()); message.setSubject(pending.subject()); message.setText(pending.body());
        try {
            mail.send(message);
            jdbc.update("DELETE FROM email_outbox WHERE id=?",pending.id());
        } catch(MailException failure) {
            long delay=Math.min(3600,30L << Math.min(pending.attempts(),7));
            jdbc.update("UPDATE email_outbox SET attempts=attempts+1,next_attempt_at=? WHERE id=?",
                Timestamp.from(clock.instant().plus(Duration.ofSeconds(delay))),pending.id());
            // SMTP exception messages may contain addresses or message bodies; do not log them.
        }
    }
    private record Pending(UUID id,String recipient,String subject,String body,int attempts) {}
}
