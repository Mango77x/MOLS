package com.mls.logistics.security.domain;

import jakarta.persistence.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Application user entity.
 *
 * Implements UserDetails so Spring Security can use it directly
 * without any additional adapters.
 *
 * Stored in the 'app_users' table to avoid conflict with
 * PostgreSQL reserved word 'users'.
 */
@Entity
@Table(name = "app_users")
public class AppUser implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    /**
     * Account enabled flag.
     *
     * Nullable on purpose for upgrade compatibility: when introducing this column
     * into an existing database, adding a NOT NULL column can fail.
     * Treat null as enabled and backfill to true on startup.
     */
    @Column
    private Boolean enabled;

    /**
     * Incremented every time this user's password is set (creation counts as
     * the first "set"). A JWT embeds the version current at login; JwtAuthFilter
     * rejects a token whose embedded version no longer matches the current
     * one, so a reset actually revokes any token issued under the old
     * password instead of leaving it valid until it naturally expires.
     *
     * <p>Deliberately an incrementing counter rather than a "changed at"
     * timestamp: a timestamp can only be compared at whatever precision it's
     * stored/embedded at (a JWT's {@code iat} is whole-seconds), so two
     * distinct password-set events landing in the same second would produce
     * indistinguishable values — an integer bump can never collide like
     * that, however fast the events happen.</p>
     */
    @Column(nullable = false)
    private int passwordVersion;

    public AppUser() {
    }

    public AppUser(String username, String password, Role role) {
        this.username = username;
        this.password = password;
        this.role = role;
        this.enabled = Boolean.TRUE;
        this.passwordVersion = 0;
    }

    // UserDetails implementation

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled == null || enabled;
    }

    // Getters and setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public boolean isEnabledFlag() {
        return enabled == null || enabled;
    }

    public void setEnabledFlag(boolean enabled) {
        this.enabled = enabled;
    }

    public int getPasswordVersion() {
        return passwordVersion;
    }

    public void setPasswordVersion(int passwordVersion) {
        this.passwordVersion = passwordVersion;
    }
}