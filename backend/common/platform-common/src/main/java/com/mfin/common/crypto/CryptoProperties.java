package com.mfin.common.crypto;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Keys for field-level encryption. Supplied from the environment (a Kubernetes secret backed
 * by the cloud KMS or Vault) and never committed.
 */
@Validated
@ConfigurationProperties(prefix = "mfin.crypto")
public class CryptoProperties {

    /** Base64-encoded 256-bit AES key used to encrypt PII columns. */
    @NotBlank
    private String dataKey;

    /**
     * Base64-encoded HMAC key used to build blind indexes. Must differ from {@link #dataKey}:
     * reusing one key for both encryption and search defeats the point of the index.
     */
    @NotBlank
    private String indexKey;

    /** Identifies the key generation, so rotation can re-encrypt lazily without downtime. */
    private String keyVersion = "v1";

    public String getDataKey() {
        return dataKey;
    }

    public void setDataKey(String dataKey) {
        this.dataKey = dataKey;
    }

    public String getIndexKey() {
        return indexKey;
    }

    public void setIndexKey(String indexKey) {
        this.indexKey = indexKey;
    }

    public String getKeyVersion() {
        return keyVersion;
    }

    public void setKeyVersion(String keyVersion) {
        this.keyVersion = keyVersion;
    }
}
