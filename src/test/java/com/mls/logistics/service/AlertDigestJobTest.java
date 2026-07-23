package com.mls.logistics.service;

import com.mls.logistics.config.MailProperties;
import com.mls.logistics.dto.response.DashboardResponse;
import com.mls.logistics.security.domain.AppUser;
import com.mls.logistics.security.domain.Role;
import com.mls.logistics.security.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertDigestJobTest {

    @Mock
    private DashboardService dashboardService;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private NotificationMailService mailService;

    private MailProperties mailProperties;
    private AlertDigestJob job;

    private AppUser adminWithEmail;
    private AppUser adminWithoutEmail;

    @BeforeEach
    void setUp() {
        mailProperties = new MailProperties();
        job = new AlertDigestJob(dashboardService, appUserRepository, mailService, mailProperties);

        adminWithEmail = new AppUser("admin1", "hash", Role.ADMIN);
        adminWithEmail.setId(1L);
        adminWithEmail.setEmail("admin1@example.com");

        adminWithoutEmail = new AppUser("admin2", "hash", Role.ADMIN);
        adminWithoutEmail.setId(2L);
    }

    @Test
    void sendDailyDigest_WhenMailDisabled_ShouldSkipEntirely() {
        mailProperties.setEnabled(false);

        job.sendDailyDigest();

        verify(dashboardService, never()).lowStockAlerts();
        verify(appUserRepository, never()).findAllByRoleAndEnabledTrue(Role.ADMIN);
    }

    @Test
    void sendDailyDigest_WhenNoAlerts_ShouldSkipQueryingRecipients() {
        mailProperties.setEnabled(true);
        when(dashboardService.lowStockAlerts()).thenReturn(List.of());
        when(dashboardService.staleOrderAlerts()).thenReturn(List.of());

        job.sendDailyDigest();

        verify(appUserRepository, never()).findAllByRoleAndEnabledTrue(Role.ADMIN);
    }

    @Test
    void sendDailyDigest_OnlyEmailsAdminsWithAnEmailSet() {
        mailProperties.setEnabled(true);
        List<DashboardResponse.LowStockAlert> lowStock =
                List.of(new DashboardResponse.LowStockAlert(1L, "Fuel", "Main Warehouse", 2, true));
        when(dashboardService.lowStockAlerts()).thenReturn(lowStock);
        when(dashboardService.staleOrderAlerts()).thenReturn(List.of());
        when(appUserRepository.findAllByRoleAndEnabledTrue(Role.ADMIN))
                .thenReturn(List.of(adminWithEmail, adminWithoutEmail));

        job.sendDailyDigest();

        verify(mailService).sendDigest(eq(adminWithEmail), eq(lowStock), eq(List.of()));
        verify(mailService, never()).sendDigest(eq(adminWithoutEmail), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
