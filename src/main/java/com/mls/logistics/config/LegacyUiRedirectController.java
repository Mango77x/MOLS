package com.mls.logistics.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Courtesy redirects for old {@code /ui/**} bookmarks/links now that the
 * Thymeleaf admin UI has been fully replaced by the React SPA at
 * {@code /app/**} (Sprint 6 cutover). Uses a plain 302 {@code Location}
 * header rather than Spring MVC's {@code "redirect:"} view convention, since
 * that relies on view-resolution machinery this app no longer configures now
 * that Thymeleaf is gone.
 *
 * <p>Every migrated page kept the same path suffix in {@code /app} as it had
 * in {@code /ui} (e.g. {@code /ui/warehouses/5/edit} -&gt;
 * {@code /app/warehouses/5/edit}), so a single prefix swap covers all of
 * them. The handful of session-draft-only POST routes the old order wizard
 * used (e.g. {@code /ui/orders/draft/items}) have no GET equivalent and
 * simply 404 now, which is fine — nothing links to them directly.</p>
 */
@RestController
public class LegacyUiRedirectController {

    @GetMapping("/ui")
    public ResponseEntity<Void> redirectRoot() {
        return redirectTo("/app");
    }

    @GetMapping("/ui/**")
    public ResponseEntity<Void> redirectToApp(HttpServletRequest request) {
        String suffix = request.getRequestURI().substring("/ui".length());
        return redirectTo("/app" + suffix);
    }

    private ResponseEntity<Void> redirectTo(String path) {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(path)).build();
    }
}
