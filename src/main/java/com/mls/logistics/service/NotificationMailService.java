package com.mls.logistics.service;

import com.mls.logistics.config.MailProperties;
import com.mls.logistics.dto.response.DashboardResponse;
import com.mls.logistics.security.domain.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Sends the app's two kinds of outgoing email: the low-stock/stale-order
 * digest and the self-service password-reset link.
 *
 * <p>Plain-text {@link SimpleMailMessage} rather than an HTML template
 * engine — keeps this sprint's scope to "an email channel exists" rather
 * than also standing up templating infrastructure nobody asked for yet.</p>
 *
 * <p>Every send is gated behind {@link MailProperties#isEnabled()}: with it
 * off (the default), this service logs and returns instead of touching
 * {@link JavaMailSender} at all, so the app works normally — including every
 * other feature — with no SMTP server configured.</p>
 */
@Service
public class NotificationMailService {

    private static final Logger log = LoggerFactory.getLogger(NotificationMailService.class);

    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;

    public NotificationMailService(JavaMailSender mailSender, MailProperties mailProperties) {
        this.mailSender = mailSender;
        this.mailProperties = mailProperties;
    }

    /**
     * Sends the daily low-stock/stale-order digest to one recipient. Skips
     * silently (no email sent) if both alert lists are empty — nothing
     * actionable to report.
     */
    public void sendDigest(AppUser recipient,
                            List<DashboardResponse.LowStockAlert> lowStock,
                            List<DashboardResponse.StaleOrderAlert> staleOrders) {
        if (lowStock.isEmpty() && staleOrders.isEmpty()) {
            return;
        }
        send(recipient.getEmail(), "MOLS — daily alert digest", digestBody(lowStock, staleOrders));
    }

    /**
     * Sends the password-reset link. {@code token} is opaque to this
     * method — validity/expiry/single-use are all enforced by
     * {@code JwtService.isPasswordResetTokenValid} when the link is
     * redeemed, not here.
     */
    public void sendPasswordResetEmail(AppUser user, String token) {
        String link = mailProperties.getAppBaseUrl() + "/app/reset-password?token=" + token;
        String body = "Hello " + user.getUsername() + ",\n\n"
                + "A password reset was requested for your MOLS account. "
                + "Use the link below to set a new password. "
                + "If you didn't request this, you can safely ignore this email.\n\n"
                + link + "\n\n"
                + "This link expires shortly and can only be used once.";
        send(user.getEmail(), "MOLS — reset your password", body);
    }

    private void send(String to, String subject, String body) {
        if (!mailProperties.isEnabled()) {
            log.debug("Mail disabled (mols.mail.enabled=false) — skipping send to {}", to);
            return;
        }
        if (to == null || to.isBlank()) {
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailProperties.getFrom());
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (MailException ex) {
            // A down/misconfigured SMTP server must never break the caller
            // (the scheduled digest job, or a user-facing forgot-password
            // request that always answers 200 regardless) — log and move on.
            log.warn("Failed to send email to {}: {}", to, ex.getMessage());
        }
    }

    private String digestBody(List<DashboardResponse.LowStockAlert> lowStock,
                               List<DashboardResponse.StaleOrderAlert> staleOrders) {
        StringBuilder body = new StringBuilder("MOLS daily alert digest\n\n");

        if (!lowStock.isEmpty()) {
            body.append("Low stock (").append(lowStock.size()).append("):\n");
            for (DashboardResponse.LowStockAlert alert : lowStock) {
                body.append("- ")
                        .append(alert.resourceName() != null ? alert.resourceName() : "Resource #" + alert.stockId())
                        .append(" @ ")
                        .append(alert.warehouseName() != null ? alert.warehouseName() : "unknown warehouse")
                        .append(": ")
                        .append(alert.quantity())
                        .append(alert.critical() ? " (critical)" : "")
                        .append("\n");
            }
            body.append("\n");
        }

        if (!staleOrders.isEmpty()) {
            body.append("Stale orders (").append(staleOrders.size()).append("):\n");
            for (DashboardResponse.StaleOrderAlert alert : staleOrders) {
                body.append("- Order #")
                        .append(alert.orderId())
                        .append(" (")
                        .append(alert.unitName() != null ? alert.unitName() : "unknown unit")
                        .append("): pending ")
                        .append(alert.daysPending())
                        .append(" day(s)\n");
            }
        }

        return body.toString();
    }
}
