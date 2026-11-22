package com.jeltechnologies.meseefree.portal.web;

import com.jeltechnologies.meseefree.portal.security.PortalAuthFilter;
import com.jeltechnologies.meseefree.portal.security.PortalCredentials;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Change the portal's username and password. Forced right after the first login with the
 * bootstrap credentials from .env; available any time afterwards (then requiring the current
 * password, so a hijacked session alone can't lock the operator out).
 */
@Controller
public class AccountController {

    static final int MIN_PASSWORD_LENGTH = 10;
    static final int MAX_USERNAME_LENGTH = 64;

    private final PortalCredentials credentials;

    public AccountController(PortalCredentials credentials) {
        this.credentials = credentials;
    }

    @GetMapping(PortalAuthFilter.ACCOUNT_PATH)
    public String form(Model model) {
        populate(model);
        return "account";
    }

    @PostMapping(PortalAuthFilter.ACCOUNT_PATH)
    public String change(@RequestParam(required = false) String currentPassword,
                          @RequestParam String newUsername,
                          @RequestParam String newPassword,
                          @RequestParam String confirmPassword,
                          HttpServletResponse res, Model model) {
        String user = newUsername.strip();
        String error = validate(currentPassword, user, newPassword, confirmPassword);
        if (error != null) {
            populate(model);
            model.addAttribute("newUsername", user);
            model.addAttribute("error", error);
            return "account";
        }
        credentials.change(user, newPassword);
        // The session key rotated with the change, so the old cookie is dead — issue a fresh one.
        LoginController.setSessionCookie(res, credentials.issueSessionToken(user));
        return "redirect:/";
    }

    private String validate(String currentPassword, String user, String newPassword, String confirmPassword) {
        boolean bootstrap = credentials.mustChangeCredentials();
        if (!bootstrap && !credentials.isValid(credentials.currentUsername(), currentPassword)) {
            return "Current password is wrong.";
        }
        if (user.isEmpty()) {
            return "Username must not be blank.";
        }
        if (user.length() > MAX_USERNAME_LENGTH) {
            return "Username must be at most " + MAX_USERNAME_LENGTH + " characters.";
        }
        if (user.indexOf(':') >= 0) {
            // Basic Auth splits user:pass on the first colon, so a colon in the username can't work.
            return "Username must not contain a colon (:).";
        }
        if (newPassword.length() < MIN_PASSWORD_LENGTH) {
            return "Password must be at least " + MIN_PASSWORD_LENGTH + " characters.";
        }
        if (!newPassword.equals(confirmPassword)) {
            return "Passwords do not match.";
        }
        if (credentials.isBootstrapPassword(newPassword)) {
            return "Choose a password different from the initial one in .env.";
        }
        return null;
    }

    private void populate(Model model) {
        model.addAttribute("bootstrap", credentials.mustChangeCredentials());
        model.addAttribute("currentUsername", credentials.currentUsername());
        model.addAttribute("minPasswordLength", MIN_PASSWORD_LENGTH);
    }
}
