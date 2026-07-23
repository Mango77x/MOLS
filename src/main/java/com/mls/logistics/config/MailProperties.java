package com.mls.logistics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Application-level email config (Sprint 19).
 *
 * SMTP connection details (host/port/username/password) are Spring Boot's
 * own {@code spring.mail.*} properties — {@code spring-boot-starter-mail}
 * auto-configures a {@code JavaMailSender} bean from those regardless of
 * whether they're set. What lives here is the app's own on/off switch:
 * actual sending only happens when {@link #isEnabled()} is true, so the app
 * keeps booting cleanly (and every other feature keeps working) on a
 * machine with no mail server configured at all.
 */
@ConfigurationProperties(prefix = "mols.mail")
public class MailProperties {

    private boolean enabled;

    /** The From address on outgoing mail. */
    private String from;

    /** Cron expression for the low-stock/stale-order digest job. */
    private String digestCron;

    /**
     * Base URL the SPA is reachable at, used to build the link inside the
     * password-reset email (e.g. {@code https://mols.example.com}). No
     * trailing slash.
     */
    private String appBaseUrl;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getDigestCron() {
        return digestCron;
    }

    public void setDigestCron(String digestCron) {
        this.digestCron = digestCron;
    }

    public String getAppBaseUrl() {
        return appBaseUrl;
    }

    public void setAppBaseUrl(String appBaseUrl) {
        this.appBaseUrl = appBaseUrl;
    }
}
