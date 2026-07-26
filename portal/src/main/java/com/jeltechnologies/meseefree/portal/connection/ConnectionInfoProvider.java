package com.jeltechnologies.meseefree.portal.connection;

import com.jeltechnologies.meseefree.portal.config.PortalProperties;
import com.jeltechnologies.meseefree.portal.identity.Identity;
import com.jeltechnologies.meseefree.portal.identity.IdentityLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The identity/address fields are resolved once at startup and never change for the life of the
 * process, mirroring the old Node portal's one-shot resolution. The connection label is the one
 * exception — it can be changed at runtime via {@link #rename(String)}, so it's held in a mutable
 * {@link AtomicReference} rather than baked into a single immutable {@link ConnectionInfo}.
 */
@Component
public class ConnectionInfoProvider {

    private static final Logger log = LoggerFactory.getLogger(ConnectionInfoProvider.class);

    private final XrayClientLabelStore labelStore;
    private final Identity identity;
    private final String publicIp;
    private final String vlessHost;
    private final int port;
    private final GeoLocation geoLocation;
    private final AtomicReference<ConnectionInfo> connectionInfo = new AtomicReference<>();

    public ConnectionInfoProvider(IdentityLoader identityLoader, PublicIpClient publicIpClient,
                                   XrayClientLabelStore labelStore, PortalProperties properties)
            throws IOException, InterruptedException {
        this.labelStore = labelStore;
        this.identity = identityLoader.load();
        this.publicIp = publicIpClient.fetchPublicIp();
        this.geoLocation = publicIpClient.fetchGeoLocation(publicIp);
        this.port = Integer.parseInt(properties.xrayPort());
        this.vlessHost = properties.domainConfigured() ? properties.domain() : publicIp;

        String label = labelStore.readLabel().orElse("Me See Free - " + geoLocation.display());
        connectionInfo.set(buildConnectionInfo(label));

        log.info("Public IP: {}", publicIp);
        log.info("VLESS address: {}", vlessHost);
        log.info("VLESS link: {}", connectionInfo.get().vlessLink());
        log.info("Portal: https://{}:{}", vlessHost, properties.portalPort());
    }

    public ConnectionInfo connectionInfo() {
        return connectionInfo.get();
    }

    public String publicIp() {
        return publicIp;
    }

    /**
     * Renames the connection label, taking effect immediately in memory and persisted into the
     * xray client's "email" field in config.json (see {@link XrayClientLabelStore}) so it
     * survives an xray restart too.
     */
    public synchronized void rename(String newLabel) {
        String trimmed = newLabel == null ? "" : newLabel.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Label must not be blank");
        }
        connectionInfo.set(buildConnectionInfo(trimmed));
        labelStore.writeLabel(trimmed);
        log.info("Connection label renamed to: {}", trimmed);
    }

    private ConnectionInfo buildConnectionInfo(String label) {
        String vlessLink = "vless://" + identity.uuid() + "@" + vlessHost + ":" + port
                + "?encryption=none&flow=xtls-rprx-vision&security=reality&sni=" + identity.sniDomain()
                + "&fp=chrome&pbk=" + identity.publicKey() + "&sid=" + identity.shortId()
                + "&type=tcp#" + encodeURIComponent(label);

        return new ConnectionInfo(
                "vless", identity.uuid(), vlessHost, port, "xtls-rprx-vision", "reality",
                identity.sniDomain(), identity.publicKey(), identity.shortId(), "chrome", "tcp",
                geoLocation.city(), geoLocation.country(), label, vlessLink);
    }

    /**
     * Reproduces JS {@code encodeURIComponent}, which leaves {@code ! ' ( ) ~} unescaped and
     * encodes space as {@code %20} — both differ from {@link URLEncoder}'s form-encoding rules.
     */
    private static String encodeURIComponent(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("%21", "!")
                .replace("%27", "'")
                .replace("%28", "(")
                .replace("%29", ")")
                .replace("%7E", "~");
    }
}
