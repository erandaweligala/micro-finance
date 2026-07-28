package com.mfin.customer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Postal/physical address. Embedded rather than a separate table: an address has no identity
 * of its own and is always read with its customer.
 */
@Embeddable
public class Address {

    @Column(name = "address_line1", length = 160)
    private String line1;

    @Column(name = "address_line2", length = 160)
    private String line2;

    @Column(name = "city", length = 96)
    private String city;

    @Column(name = "state_province", length = 96)
    private String stateProvince;

    @Column(name = "postal_code", length = 24)
    private String postalCode;

    @Column(name = "country_code", length = 2)
    private String countryCode;

    protected Address() {
    }

    public Address(String line1, String line2, String city, String stateProvince,
                   String postalCode, String countryCode) {
        this.line1 = line1;
        this.line2 = line2;
        this.city = city;
        this.stateProvince = stateProvince;
        this.postalCode = postalCode;
        this.countryCode = countryCode;
    }

    public String getLine1() {
        return line1;
    }

    public String getLine2() {
        return line2;
    }

    public String getCity() {
        return city;
    }

    public String getStateProvince() {
        return stateProvince;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getCountryCode() {
        return countryCode;
    }
}
