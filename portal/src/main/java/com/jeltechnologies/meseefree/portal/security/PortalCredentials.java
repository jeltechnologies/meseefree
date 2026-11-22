package com.jeltechnologies.meseefree.portal.security;

import com.jeltechnologies.meseefree.portal.config.PortalProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * The single credential check behind every auth path (Basic Auth header, login form, session
 * cookie). Two modes:
 * <ul>
 *   <li><b>Bootstrap</b> — no stored credentials yet: PORTAL_USER_NAME/PORTAL_PASSWORD from .env
 *       are accepted, but only to reach the page where the operator must replace them.</li>
 *   <li><b>Stored</b> — after that change: only the hashed credentials in {@link CredentialStore}
 *       are accepted, and the .env values are ignored.</li>
 * </ul>
 */
@Component
public class PortalCredentials {

    public static final Duration SESSION_LIFETIME = Duration.ofDays(30);

    private final PortalProperties properties;
    private final CredentialStore store;
    /** Signs bootstrap-mode sessions only; they die on restart, which is fine for a one-time step. */
    private final byte[] bootstrapSessionKey = new byte[32];

    public PortalCredentials(PortalProperties properties, CredentialStore store) {
        this.properties = properties;
        this.store = store;
        new SecureRandom().nextBytes(bootstrapSessionKey);
    }

    /** True when some way of logging in exists (stored credentials, or bootstrap ones in .env). */
    public boolean authConfigured() {
        return store.hasStoredCredentials() || properties.bootstrapCredentialsConfigured();
    }

    /** True until the operator has replaced the bootstrap credentials from .env. */
    public boolean mustChangeCredentials() {
        return !store.hasStoredCredentials();
    }

    public String currentUsername() {
        return store.username().orElse(properties.portalUserName());
    }

    public boolean isValid(String user, String pass) {
        if (user == null || pass == null) {
            return false;
        }
        if (store.hasStoredCredentials()) {
            return store.verify(user, pass);
        }
        return properties.bootstrapCredentialsConfigured()
                && safeEqual(user, properties.portalUserName())
                & safeEqual(pass, properties.portalPassword());
    }

    public boolean isBootstrapPassword(String pass) {
        return properties.bootstrapCredentialsConfigured() && safeEqual(pass, properties.portalPassword());
    }

    public void change(String user, String pass) {
        store.save(user, pass);
    }

    /** Validates a raw {@code user:pass} value, base64-encoded exactly like a Basic Auth header. */
    public boolean isValidBase64(String base64Value) {
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(base64Value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return false;
        }
        int idx = decoded.indexOf(':');
        if (idx == -1) {
            return false;
        }
        return isValid(decoded.substring(0, idx), decoded.substring(idx + 1));
    }

    /**
     * Issues a login-cookie value: {@code base64url(user).expiryEpochSeconds.base64url(hmac)}.
     * Contains no password, and is signed with a key that rotates on every credential change.
     */
    public String issueSessionToken(String user) {
        long expiry = Instant.now().plus(SESSION_LIFETIME).getEpochSecond();
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(user.getBytes(StandardCharsets.UTF_8)) + "." + expiry;
        return payload + "." + sign(payload);
    }

    public boolean isValidSessionToken(String token) {
        if (token == null) {
            return false;
        }
        int lastDot = token.lastIndexOf('.');
        if (lastDot <= 0) {
            return false;
        }
        String payload = token.substring(0, lastDot);
        if (!safeEqual(token.substring(lastDot + 1), sign(payload))) {
            return false;
        }
        String[] parts = payload.split("\\.", -1);
        if (parts.length != 2) {
            return false;
        }
        try {
            String user = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            long expiry = Long.parseLong(parts[1]);
            return Instant.now().getEpochSecond() < expiry && safeEqual(user, currentUsername());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private String sign(String payload) {
        byte[] key = store.sessionKey().orElse(bootstrapSessionKey);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    private static boolean safeEqual(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
