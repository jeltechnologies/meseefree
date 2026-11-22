package com.jeltechnologies.meseefree.portal.security;

import com.jeltechnologies.meseefree.portal.config.AcmeProperties;
import com.jeltechnologies.meseefree.portal.tls.AcmeChallengeController;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class PortalAuthFilter extends HttpFilter {

    public static final String AUTH_COOKIE_NAME = "portal_auth";
    private static final String LOGIN_PATH = "/login";
    public static final String ACCOUNT_PATH = "/account";

    private final AcmeProperties acmeProperties;
    private final PortalCredentials credentials;

    public PortalAuthFilter(AcmeProperties acmeProperties, PortalCredentials credentials) {
        this.acmeProperties = acmeProperties;
        this.credentials = credentials;
    }

    @Override
    protected void doFilter(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        // The ACME HTTP-01 challenge must be reachable without auth, on any connector.
        if (req.getRequestURI().startsWith(AcmeChallengeController.CHALLENGE_PATH_PREFIX)) {
            chain.doFilter(req, res);
            return;
        }

        // The plain-HTTP connector exists only to answer ACME challenges — never let it serve
        // real portal content or accept credentials over cleartext HTTP.
        if (req.getLocalPort() == acmeProperties.httpPort()) {
            res.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        if (!credentials.authConfigured()) {
            res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            res.setContentType("text/plain");
            res.getWriter().write("Portal authentication is not configured. Set PORTAL_USER_NAME and "
                    + "PORTAL_PASSWORD in .env and recreate the portal (docker compose up -d portal).");
            return;
        }

        // The login page (and its form submission) must stay reachable while unauthenticated —
        // it's the only way to become authenticated in the first place.
        if (LOGIN_PATH.equals(req.getRequestURI())) {
            chain.doFilter(req, res);
            return;
        }

        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Basic ")) {
            // Presented explicitly (curl -u, scripts) — honor real Basic Auth semantics here,
            // no redirect to the HTML login page, so existing scripted/API usage keeps working.
            if (credentials.isValidBase64(header.substring(6))) {
                if (credentials.mustChangeCredentials()) {
                    // The bootstrap credentials from .env only unlock the change-credentials page.
                    res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    res.setContentType("text/plain");
                    res.getWriter().write("The initial portal credentials must be changed first. "
                            + "Sign in to the portal in a browser to set your own username and password.");
                    return;
                }
                chain.doFilter(req, res);
            } else {
                res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                res.setContentType("text/plain");
                res.getWriter().write("Authentication required");
            }
            return;
        }

        String cookieValue = readCookie(req);
        if (credentials.isValidSessionToken(cookieValue)) {
            // Signed in with the bootstrap credentials: everything except changing them is off-limits.
            if (credentials.mustChangeCredentials() && !ACCOUNT_PATH.equals(req.getRequestURI())) {
                res.sendRedirect(ACCOUNT_PATH);
                return;
            }
            chain.doFilter(req, res);
            return;
        }

        // No native WWW-Authenticate challenge here on purpose — that's what triggers the
        // browser's own ugly login popup. Send browsers to our own login page instead.
        res.sendRedirect(LOGIN_PATH);
    }

    private static String readCookie(HttpServletRequest req) {
        Cookie[] cookies = req.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (AUTH_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
