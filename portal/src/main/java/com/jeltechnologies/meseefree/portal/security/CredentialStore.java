package com.jeltechnologies.meseefree.portal.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.Properties;

/**
 * Persists the operator's own portal credentials once they've replaced the bootstrap ones from
 * .env. The password is never stored: only a salted PBKDF2-HMAC-SHA256 hash. Also holds a random
 * session-signing key, regenerated on every credential change so existing login cookies stop
 * working the moment the password changes.
 */
@Component
public class CredentialStore {

    private static final Logger log = LoggerFactory.getLogger(CredentialStore.class);

    // Lives in tls/ because that's the portal's one writable directory on every install (it's been
    // mounted read-write since the ACME feature) — a dedicated new mount would break anyone who
    // updates the image without also re-downloading docker-compose.yaml.
    private static final Path DIR = Path.of("/etc/xray/tls");
    private static final Path FILE = DIR.resolve("portal-credentials.properties");

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 600_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;

    private final SecureRandom random = new SecureRandom();
    private volatile Stored stored;

    record Stored(String username, byte[] salt, int iterations, byte[] hash, byte[] sessionKey) {
    }

    public CredentialStore() {
        this.stored = load();
        if (stored != null) {
            log.info("Using stored portal credentials from {}; PORTAL_USER_NAME/PORTAL_PASSWORD in .env are ignored.", FILE);
        }
    }

    public boolean hasStoredCredentials() {
        return stored != null;
    }

    public Optional<String> username() {
        Stored s = stored;
        return s == null ? Optional.empty() : Optional.of(s.username());
    }

    public Optional<byte[]> sessionKey() {
        Stored s = stored;
        return s == null ? Optional.empty() : Optional.of(s.sessionKey());
    }

    public boolean verify(String user, String pass) {
        Stored s = stored;
        if (s == null || user == null || pass == null) {
            return false;
        }
        // Always run the hash, even on a username mismatch, so timing doesn't reveal which was wrong.
        byte[] candidate = hash(pass, s.salt(), s.iterations());
        boolean userOk = MessageDigest.isEqual(user.getBytes(StandardCharsets.UTF_8),
                s.username().getBytes(StandardCharsets.UTF_8));
        return MessageDigest.isEqual(candidate, s.hash()) & userOk;
    }

    public synchronized void save(String user, String pass) {
        byte[] salt = randomBytes(SALT_BYTES);
        Stored next = new Stored(user, salt, ITERATIONS, hash(pass, salt, ITERATIONS), randomBytes(32));

        Base64.Encoder b64 = Base64.getEncoder();
        Properties props = new Properties();
        props.setProperty("username", next.username());
        props.setProperty("algorithm", ALGORITHM);
        props.setProperty("iterations", Integer.toString(next.iterations()));
        props.setProperty("salt", b64.encodeToString(next.salt()));
        props.setProperty("hash", b64.encodeToString(next.hash()));
        props.setProperty("sessionKey", b64.encodeToString(next.sessionKey()));

        try {
            Files.createDirectories(DIR);
            Path tmp = Files.createTempFile(DIR, "portal-credentials", ".tmp");
            try {
                setOwnerOnly(tmp);
                try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                    props.store(w, "Me See Free portal credentials (password stored as a salted PBKDF2 hash)");
                }
                // Nothing outside the portal container reads this file, so a rename is fine here
                // (unlike config.json) and guarantees a crash never leaves a half-written file.
                Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write " + FILE, e);
        }
        stored = next;
        log.info("Portal credentials changed; stored hashed in {}.", FILE);
    }

    private static Stored load() {
        if (!Files.exists(FILE)) {
            return null;
        }
        Properties props = new Properties();
        try (Reader r = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
            props.load(r);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + FILE, e);
        }
        Base64.Decoder b64 = Base64.getDecoder();
        try {
            return new Stored(
                    require(props, "username"),
                    b64.decode(require(props, "salt")),
                    Integer.parseInt(require(props, "iterations")),
                    b64.decode(require(props, "hash")),
                    b64.decode(require(props, "sessionKey")));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(FILE + " is corrupt (" + e.getMessage()
                    + "); delete it to fall back to the bootstrap credentials in .env.", e);
        }
    }

    private static String require(Properties props, String key) {
        String value = props.getProperty(key);
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("missing '" + key + "'");
        }
        return value;
    }

    private static byte[] hash(String pass, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(pass.toCharArray(), salt, iterations, HASH_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(ALGORITHM + " unavailable", e);
        } finally {
            spec.clearPassword();
        }
    }

    private byte[] randomBytes(int n) {
        byte[] bytes = new byte[n];
        random.nextBytes(bytes);
        return bytes;
    }

    private static void setOwnerOnly(Path path) {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException e) {
            // Non-POSIX filesystem — the hash is still salted and slow, so this is defense in depth only.
        }
    }
}
