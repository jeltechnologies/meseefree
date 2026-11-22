package com.jeltechnologies.meseefree.portal.connection;

import com.jeltechnologies.meseefree.portal.config.PortalProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Warns when {@code DOMAIN} doesn't resolve to this server's public IP — the same precondition
 * the VLESS address (see {@link ConnectionInfoProvider}) and Let's Encrypt's HTTP-01 challenge
 * both silently depend on. A no-op whenever no domain is configured, and never allowed to crash
 * the app; mirrors {@code AcmeCertificateService}'s startup-check-plus-periodic-recheck shape so
 * the warning clears itself once DNS propagates, without needing a portal restart.
 */
@Component
public class DomainDnsCheckService {

    private static final Logger log = LoggerFactory.getLogger(DomainDnsCheckService.class);

    private final PortalProperties properties;
    private final ConnectionInfoProvider connectionInfoProvider;
    private final AtomicBoolean dnsMismatch = new AtomicBoolean(false);

    public DomainDnsCheckService(PortalProperties properties, ConnectionInfoProvider connectionInfoProvider) {
        this.properties = properties;
        this.connectionInfoProvider = connectionInfoProvider;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        Thread thread = new Thread(this::check, "dns-initial-check");
        thread.setDaemon(true);
        thread.start();
    }

    @Scheduled(fixedRate = 10, timeUnit = TimeUnit.MINUTES)
    public void scheduledCheck() {
        check();
    }

    public void check() {
        if (!properties.domainConfigured()) {
            dnsMismatch.set(false);
            return;
        }
        String domain = properties.domain();
        String publicIp = connectionInfoProvider.publicIp();

        try {
            String[] resolved = Arrays.stream(InetAddress.getAllByName(domain))
                    .filter(Inet4Address.class::isInstance)
                    .map(InetAddress::getHostAddress)
                    .toArray(String[]::new);
            boolean matches = Arrays.asList(resolved).contains(publicIp);
            dnsMismatch.set(!matches);
            if (!matches) {
                log.warn("DOMAIN {} does not resolve to this server's public IP ({}) — it currently "
                                + "resolves to [{}]. VLESS clients and Let's Encrypt renewal may fail "
                                + "until DNS is updated to point at this server.",
                        domain, publicIp, String.join(", ", resolved));
            } else {
                log.debug("DOMAIN {} correctly resolves to this server's public IP ({}).", domain, publicIp);
            }
        } catch (UnknownHostException e) {
            dnsMismatch.set(true);
            log.warn("DOMAIN {} could not be resolved at all: {}", domain, e.getMessage());
        }
    }

    /**
     * Whether {@code host} currently resolves (IPv4) to this server's public IP. Used to decide
     * whether {@code www.<DOMAIN>} can be added to the certificate: an ACME order fails as a whole
     * if any one name in it doesn't validate, so only names that point here are included.
     */
    public boolean resolvesToThisServer(String host) {
        try {
            return Arrays.stream(InetAddress.getAllByName(host))
                    .filter(Inet4Address.class::isInstance)
                    .map(InetAddress::getHostAddress)
                    .anyMatch(connectionInfoProvider.publicIp()::equals);
        } catch (UnknownHostException e) {
            return false;
        }
    }

    /** True when a domain is configured but doesn't currently resolve to this server's public IP. */
    public boolean dnsMismatch() {
        return dnsMismatch.get();
    }
}
