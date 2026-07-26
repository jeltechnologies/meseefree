package com.jeltechnologies.meseefree.portal.connection;

/** City/country resolved from the public IP; either may be blank if lookup failed. */
public record GeoLocation(String city, String country) {

    public String display() {
        return city.isEmpty() ? country : city + ", " + country;
    }
}
