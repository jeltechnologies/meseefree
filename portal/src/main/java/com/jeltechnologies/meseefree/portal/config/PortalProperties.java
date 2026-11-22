package com.jeltechnologies.meseefree.portal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties
public record PortalProperties(String xrayPort, String portalPort, String portalUserName, String portalPassword, String domain) {

    /**
     * Whether the bootstrap credentials from .env are set. They're only honored until the operator
     * changes them on first login; after that the stored (hashed) credentials replace them.
     */
    public boolean bootstrapCredentialsConfigured() {
        return portalUserName != null && !portalUserName.isEmpty()
                && portalPassword != null && !portalPassword.isEmpty();
    }

    public boolean domainConfigured() {
        return domain != null && !domain.isEmpty();
    }
}
