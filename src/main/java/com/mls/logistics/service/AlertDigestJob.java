package com.mls.logistics.service;

import com.mls.logistics.config.MailProperties;
import com.mls.logistics.dto.response.DashboardResponse;
import com.mls.logistics.security.domain.AppUser;
import com.mls.logistics.security.domain.Role;
import com.mls.logistics.security.repository.AppUserRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Sprint 19: a scheduled job that emails every enabled ADMIN with an email
 * on file the same low-stock/stale-order alerts the Dashboard already
 * computes — {@link DashboardService#lowStockAlerts()} and
 * {@link DashboardService#staleOrderAlerts()} are reused directly rather
 * than duplicating that query/threshold logic here.
 */
@Component
public class AlertDigestJob {

    private final DashboardService dashboardService;
    private final AppUserRepository appUserRepository;
    private final NotificationMailService mailService;
    private final MailProperties mailProperties;

    public AlertDigestJob(DashboardService dashboardService,
                          AppUserRepository appUserRepository,
                          NotificationMailService mailService,
                          MailProperties mailProperties) {
        this.dashboardService = dashboardService;
        this.appUserRepository = appUserRepository;
        this.mailService = mailService;
        this.mailProperties = mailProperties;
    }

    @Scheduled(cron = "${mols.mail.digest-cron}")
    @Transactional(readOnly = true)
    public void sendDailyDigest() {
        if (!mailProperties.isEnabled()) {
            return;
        }

        List<DashboardResponse.LowStockAlert> lowStock = dashboardService.lowStockAlerts();
        List<DashboardResponse.StaleOrderAlert> staleOrders = dashboardService.staleOrderAlerts();
        if (lowStock.isEmpty() && staleOrders.isEmpty()) {
            return;
        }

        for (AppUser admin : appUserRepository.findAllByRoleAndEnabledTrue(Role.ADMIN)) {
            if (admin.getEmail() != null && !admin.getEmail().isBlank()) {
                mailService.sendDigest(admin, lowStock, staleOrders);
            }
        }
    }
}
