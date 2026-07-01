package com.mls.logistics.web;

import com.mls.logistics.security.domain.AppUser;
import com.mls.logistics.security.domain.Role;
import com.mls.logistics.security.repository.AppUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * First-run setup page.
 *
 * <p>If there are no users in the database, allows creating the first ADMIN user.
 * Once a user exists, this endpoint redirects to the normal login page.</p>
 */
@Controller
public class UiSetupController {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    public UiSetupController(AppUserRepository appUserRepository,
                             PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/ui/setup")
    public String setup(Model model) {
        if (appUserRepository.count() > 0) {
            return "redirect:/ui/login";
        }
        return "ui/setup";
    }

    @PostMapping("/ui/setup")
    public String setupSubmit(
            @RequestParam("username") String username,
            @RequestParam("password") String password,
            Model model) {

        if (appUserRepository.count() > 0) {
            return "redirect:/ui/login";
        }

        String normalizedUsername = username == null ? "" : username.trim();
        if (normalizedUsername.isBlank() || password == null || password.isBlank()) {
            model.addAttribute("errorMessage", "Username and password are required.");
            return "ui/setup";
        }

        if (password.length() < 12) {
            model.addAttribute("errorMessage", "Password must be at least 12 characters.");
            return "ui/setup";
        }

        if (appUserRepository.existsByUsername(normalizedUsername)) {
            model.addAttribute("errorMessage", "Username already exists.");
            return "ui/setup";
        }

        AppUser user = new AppUser(
                normalizedUsername,
                passwordEncoder.encode(password),
                Role.ADMIN);
        appUserRepository.save(user);

        return "redirect:/ui/login?created";
    }
}
