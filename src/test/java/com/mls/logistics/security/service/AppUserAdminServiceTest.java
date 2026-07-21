package com.mls.logistics.security.service;

import com.mls.logistics.exception.InvalidRequestException;
import com.mls.logistics.exception.ResourceNotFoundException;
import com.mls.logistics.security.domain.AppUser;
import com.mls.logistics.security.domain.Role;
import com.mls.logistics.security.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AppUserAdminService, focused on the "last enabled ADMIN"
 * protection rules that had zero coverage before Sprint 6.
 */
@ExtendWith(MockitoExtension.class)
class AppUserAdminServiceTest {

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AppUserAdminService appUserAdminService;

    private AppUser admin;

    @BeforeEach
    void setUp() {
        admin = new AppUser("admin", "encoded-hash", Role.ADMIN);
        admin.setId(1L);
    }

    @Test
    void createUser_WithValidData_ShouldCreateAndEncodePassword() {
        when(appUserRepository.existsByUsername("newuser")).thenReturn(false);
        when(passwordEncoder.encode("a-long-enough-password")).thenReturn("hashed");
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        AppUser result = appUserAdminService.createUser("newuser", "a-long-enough-password", Role.OPERATOR);

        assertThat(result.getUsername()).isEqualTo("newuser");
        assertThat(result.getPassword()).isEqualTo("hashed");
        assertThat(result.getRole()).isEqualTo(Role.OPERATOR);
        assertThat(result.isEnabledFlag()).isTrue();
    }

    @Test
    void createUser_WithDuplicateUsername_ShouldThrow() {
        when(appUserRepository.existsByUsername("taken")).thenReturn(true);

        assertThatThrownBy(() -> appUserAdminService.createUser("taken", "a-long-enough-password", Role.OPERATOR))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("already exists");

        verify(appUserRepository, never()).save(any());
    }

    @Test
    void createUser_ShorterThanTwelveButAtLeastSix_ShouldThrow() {
        assertThatThrownBy(() -> appUserAdminService.createUser("newuser", "eightchr", Role.OPERATOR))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("12 characters");

        verify(appUserRepository, never()).save(any());
    }

    @Test
    void createUser_WithBlankUsername_ShouldThrow() {
        assertThatThrownBy(() -> appUserAdminService.createUser("  ", "a-long-enough-password", Role.OPERATOR))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Username is required");
    }

    @Test
    void updateRole_DemotingTheOnlyEnabledAdmin_ShouldThrow() {
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(appUserRepository.countByRoleAndEnabledTrue(Role.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> appUserAdminService.updateRole(1L, Role.OPERATOR))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("last enabled ADMIN");

        verify(appUserRepository, never()).save(any());
    }

    @Test
    void updateRole_DemotingAnAdmin_WhenAnotherAdminRemains_ShouldSucceed() {
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(appUserRepository.countByRoleAndEnabledTrue(Role.ADMIN)).thenReturn(2L);
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        AppUser result = appUserAdminService.updateRole(1L, Role.AUDITOR);

        assertThat(result.getRole()).isEqualTo(Role.AUDITOR);
    }

    @Test
    void updateRole_PromotingToAdmin_NeverChecksTheLastAdminCount() {
        AppUser operator = new AppUser("op", "hash", Role.OPERATOR);
        operator.setId(2L);
        when(appUserRepository.findById(2L)).thenReturn(Optional.of(operator));
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        AppUser result = appUserAdminService.updateRole(2L, Role.ADMIN);

        assertThat(result.getRole()).isEqualTo(Role.ADMIN);
        verify(appUserRepository, never()).countByRoleAndEnabledTrue(any());
    }

    @Test
    void updateRole_WhenUserNotFound_ShouldThrow() {
        when(appUserRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appUserAdminService.updateRole(999L, Role.OPERATOR))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void resetPassword_WithValidPassword_ShouldEncodeAndSave() {
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(passwordEncoder.encode("a-new-long-password")).thenReturn("new-hash");
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        AppUser result = appUserAdminService.resetPassword(1L, "a-new-long-password");

        assertThat(result.getPassword()).isEqualTo("new-hash");
    }

    @Test
    void resetPassword_TooShort_ShouldThrow() {
        assertThatThrownBy(() -> appUserAdminService.resetPassword(1L, "short"))
                .isInstanceOf(InvalidRequestException.class);

        verify(appUserRepository, never()).findById(any());
    }

    @Test
    void resetPassword_ShorterThanTwelveButAtLeastSix_ShouldThrow() {
        // This used to pass under a stale, unreachable-from-the-API 6-char
        // floor here — confirms the service now matches the 12-character
        // policy enforced everywhere else (CreateUserRequest/ResetPasswordRequest).
        assertThatThrownBy(() -> appUserAdminService.resetPassword(1L, "eightchr"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("12 characters");

        verify(appUserRepository, never()).findById(any());
    }

    @Test
    void resetPassword_ShouldBumpPasswordVersion_ToRevokeExistingTokens() {
        // Given: admin starts at passwordVersion 0 (set by the constructor),
        // as if a token had already been issued carrying that version.
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(passwordEncoder.encode("a-new-long-password")).thenReturn("new-hash");
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        AppUser result = appUserAdminService.resetPassword(1L, "a-new-long-password");

        // JwtAuthFilter rejects any token whose embedded pwdVersion no longer
        // matches — bumping it is what actually revokes tokens minted under
        // the old password. An incrementing counter (not a timestamp) so two
        // resets in quick succession can never produce an indistinguishable
        // value, however fast they happen.
        assertThat(result.getPasswordVersion()).isEqualTo(1);
    }

    @Test
    void resetPassword_CalledTwice_KeepsIncrementingPasswordVersion() {
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(passwordEncoder.encode(any())).thenReturn("new-hash");
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        appUserAdminService.resetPassword(1L, "a-new-long-password");
        AppUser result = appUserAdminService.resetPassword(1L, "yet-another-long-password");

        assertThat(result.getPasswordVersion()).isEqualTo(2);
    }

    @Test
    void setEnabled_DisablingTheOnlyEnabledAdmin_ShouldThrow() {
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(appUserRepository.countByRoleAndEnabledTrue(Role.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> appUserAdminService.setEnabled(1L, false))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("last enabled ADMIN");

        verify(appUserRepository, never()).save(any());
    }

    @Test
    void setEnabled_DisablingAnAdmin_WhenAnotherAdminRemains_ShouldSucceed() {
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(appUserRepository.countByRoleAndEnabledTrue(Role.ADMIN)).thenReturn(2L);
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        AppUser result = appUserAdminService.setEnabled(1L, false);

        assertThat(result.isEnabledFlag()).isFalse();
    }

    @Test
    void setEnabled_ReEnablingAnAdmin_NeverChecksTheLastAdminCount() {
        admin.setEnabledFlag(false);
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        AppUser result = appUserAdminService.setEnabled(1L, true);

        assertThat(result.isEnabledFlag()).isTrue();
        verify(appUserRepository, never()).countByRoleAndEnabledTrue(any());
    }

    @Test
    void getUserById_WhenExists_ShouldReturnUser() {
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(admin));

        Optional<AppUser> result = appUserAdminService.getUserById(1L);

        assertThat(result).contains(admin);
    }

    @Test
    void getUserById_WhenNotExists_ShouldReturnEmpty() {
        when(appUserRepository.findById(999L)).thenReturn(Optional.empty());

        assertThat(appUserAdminService.getUserById(999L)).isEmpty();
    }

    @Test
    void getAllUsers_ReturnsRepositoryResult() {
        when(appUserRepository.findAll(any(org.springframework.data.domain.Sort.class)))
                .thenReturn(java.util.List.of(admin));

        var result = appUserAdminService.getAllUsers(org.springframework.data.domain.Sort.by("id"));

        assertThat(result).containsExactly(admin);
    }
}
