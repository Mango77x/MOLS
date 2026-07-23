package com.mls.logistics.service;

import com.mls.logistics.config.MailProperties;
import com.mls.logistics.dto.response.DashboardResponse;
import com.mls.logistics.security.domain.AppUser;
import com.mls.logistics.security.domain.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationMailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private MailProperties mailProperties;
    private NotificationMailService service;
    private AppUser recipient;

    @BeforeEach
    void setUp() {
        mailProperties = new MailProperties();
        mailProperties.setFrom("mols-noreply@example.com");
        mailProperties.setAppBaseUrl("http://localhost:8080");
        service = new NotificationMailService(mailSender, mailProperties);

        recipient = new AppUser("admin", "hash", Role.ADMIN);
        recipient.setId(1L);
        recipient.setEmail("admin@example.com");
    }

    @Test
    void sendDigest_WhenMailDisabled_ShouldNotSend() {
        mailProperties.setEnabled(false);
        List<DashboardResponse.LowStockAlert> lowStock =
                List.of(new DashboardResponse.LowStockAlert(1L, "Fuel", "Main Warehouse", 2, true));

        service.sendDigest(recipient, lowStock, List.of());

        verify(mailSender, never()).send((SimpleMailMessage) org.mockito.ArgumentMatchers.any());
    }

    @Test
    void sendDigest_WhenBothAlertListsAreEmpty_ShouldNotSend() {
        mailProperties.setEnabled(true);

        service.sendDigest(recipient, List.of(), List.of());

        verify(mailSender, never()).send((SimpleMailMessage) org.mockito.ArgumentMatchers.any());
    }

    @Test
    void sendDigest_WhenEnabledWithAlerts_ShouldSendToRecipient() {
        mailProperties.setEnabled(true);
        List<DashboardResponse.LowStockAlert> lowStock =
                List.of(new DashboardResponse.LowStockAlert(1L, "Fuel", "Main Warehouse", 2, true));

        service.sendDigest(recipient, lowStock, List.of());

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage sent = captor.getValue();
        assertThat(sent.getTo()).containsExactly("admin@example.com");
        assertThat(sent.getFrom()).isEqualTo("mols-noreply@example.com");
        assertThat(sent.getText()).contains("Fuel").contains("Main Warehouse");
    }

    @Test
    void sendDigest_RecipientWithNoEmail_ShouldNotSend() {
        mailProperties.setEnabled(true);
        recipient.setEmail(null);
        List<DashboardResponse.LowStockAlert> lowStock =
                List.of(new DashboardResponse.LowStockAlert(1L, "Fuel", "Main Warehouse", 2, true));

        service.sendDigest(recipient, lowStock, List.of());

        verify(mailSender, never()).send((SimpleMailMessage) org.mockito.ArgumentMatchers.any());
    }

    @Test
    void sendPasswordResetEmail_WhenEnabled_ShouldIncludeTheLinkWithToken() {
        mailProperties.setEnabled(true);

        service.sendPasswordResetEmail(recipient, "abc.def.ghi");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getText()).contains("http://localhost:8080/app/reset-password?token=abc.def.ghi");
    }

    @Test
    void sendPasswordResetEmail_WhenDisabled_ShouldNotSend() {
        mailProperties.setEnabled(false);

        service.sendPasswordResetEmail(recipient, "abc.def.ghi");

        verify(mailSender, never()).send((SimpleMailMessage) org.mockito.ArgumentMatchers.any());
    }
}
