package com.jeltechnologies.meseefree.portal.connection;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

/**
 * Reads/writes the xray client's "email" field in config.json — repurposed to persist a
 * user-renamed connection label so it survives an xray restart (xray reads config.json only at
 * its own startup). Not used by the REALITY handshake itself, only xray's own access-log lines.
 */
@Component
public class XrayClientLabelStore {

    private static final Path CONFIG_FILE = Path.of("/etc/xray/config.json");

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Optional<String> readLabel() {
        String email = clientNode(readRoot()).path("email").asText("");
        return email.isBlank() ? Optional.empty() : Optional.of(email);
    }

    public synchronized void writeLabel(String label) {
        JsonNode root = readRoot();
        clientNode(root).put("email", label);
        byte[] json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(root);
        // Write into the existing inode (not a temp-file-then-rename) so the host-mounted file
        // keeps its owner/permissions — same invariant generate-identity.sh follows for this file.
        try (var out = Files.newOutputStream(CONFIG_FILE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            out.write(json);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write " + CONFIG_FILE, e);
        }
    }

    private JsonNode readRoot() {
        try {
            return objectMapper.readTree(Files.readAllBytes(CONFIG_FILE));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + CONFIG_FILE, e);
        }
    }

    private static ObjectNode clientNode(JsonNode root) {
        JsonNode client = root.path("inbounds").path(0).path("settings").path("clients").path(0);
        if (!(client instanceof ObjectNode objectNode)) {
            throw new IllegalStateException("Missing inbounds[0].settings.clients[0] in " + CONFIG_FILE);
        }
        return objectNode;
    }
}
