package com.jeltechnologies.meseefree.portal.web;

import com.jeltechnologies.meseefree.portal.security.PortalAuthFilter;
import com.jeltechnologies.meseefree.portal.security.PortalCredentials;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class LoginController {

    private final PortalCredentials credentials;

    public LoginController(PortalCredentials credentials) {
        this.credentials = credentials;
    }

    @GetMapping("/login")
    public String loginForm() {
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String username, @RequestParam String password,
                         HttpServletResponse res, Model model) {
        if (!credentials.isValid(username, password)) {
            model.addAttribute("error", true);
            return "login";
        }
        setSessionCookie(res, credentials.issueSessionToken(username));
        return credentials.mustChangeCredentials() ? "redirect:" + PortalAuthFilter.ACCOUNT_PATH : "redirect:/";
    }

    /** The login cookie carries a signed session token, never the password itself. */
    static void setSessionCookie(HttpServletResponse res, String token) {
        ResponseCookie cookie = ResponseCookie.from(PortalAuthFilter.AUTH_COOKIE_NAME, token)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(PortalCredentials.SESSION_LIFETIME)
                .build();
        res.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
