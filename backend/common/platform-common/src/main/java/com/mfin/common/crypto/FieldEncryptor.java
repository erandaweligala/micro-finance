package com.mfin.common.crypto;

import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * AES-256-GCM encryption for personally identifiable columns (national id numbers, phone
 * numbers, bank account details).
 *
 * <p>GCM is authenticated, so a tampered ciphertext fails to decrypt rather than silently
 * yielding garbage. A fresh 96-bit IV is generated per value and prefixed to the ciphertext;
 * the stored form is {@code <keyVersion>:<base64(iv||ciphertext||tag)>} so that a future key
 * rotation can tell which key a row was written with.</p>
 *
 * <p>Because ciphertext is non-deterministic you cannot search it. Use
 * {@link #blindIndex(String)} for exact-match lookups (e.g. "is this national id already
 * registered?"): it is a keyed HMAC, so equal inputs give equal indexes without revealing
 * the value.</p>
 */
@Component
public class FieldEncryptor {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final String SEPARATOR = ":";

    private final SecretKey dataKey;
    private final SecretKey indexKey;
    private final String keyVersion;
    private final SecureRandom random = new SecureRandom();

    public FieldEncryptor(CryptoProperties properties) {
        this.dataKey = toKey(properties.getDataKey(), "AES");
        this.indexKey = toKey(properties.getIndexKey(), HMAC_ALGORITHM);
        this.keyVersion = properties.getKeyVersion();
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, dataKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] envelope = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, envelope, 0, iv.length);
            System.arraycopy(ciphertext, 0, envelope, iv.length, ciphertext.length);
            return keyVersion + SEPARATOR + Base64.getEncoder().encodeToString(envelope);
        } catch (GeneralSecurityException ex) {
            // The message deliberately omits the value being encrypted.
            throw new IllegalStateException("Unable to encrypt field value", ex);
        }
    }

    public String decrypt(String stored) {
        if (stored == null) {
            return null;
        }
        int separator = stored.indexOf(SEPARATOR);
        if (separator < 0) {
            // Written before encryption was switched on; return as-is so a rollout can be gradual.
            return stored;
        }
        try {
            byte[] envelope = Base64.getDecoder().decode(stored.substring(separator + 1));
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(envelope, 0, iv, 0, IV_LENGTH);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, dataKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] plaintext = cipher.doFinal(envelope, IV_LENGTH, envelope.length - IV_LENGTH);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("Unable to decrypt field value", ex);
        }
    }

    /**
     * Deterministic, keyed digest for equality search over an encrypted column.
     * Values are normalised first so "+254 700 000 000" and "+254700000000" collide as intended.
     */
    public String blindIndex(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(indexKey);
            String normalised = plaintext.trim().toLowerCase().replaceAll("\\s+", "");
            return HexFormat.of().formatHex(mac.doFinal(normalised.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to derive blind index", ex);
        }
    }

    /** Last four characters, for display and support ("ID ending 4821"). */
    public static String mask(String plaintext) {
        if (plaintext == null || plaintext.length() <= 4) {
            return "****";
        }
        return "****" + plaintext.substring(plaintext.length() - 4);
    }

    private SecretKey toKey(String base64, String algorithm) {
        byte[] material = Base64.getDecoder().decode(base64);
        if (material.length < 32) {
            throw new IllegalStateException(
                    "Encryption keys must be at least 256 bits; check mfin.crypto configuration");
        }
        return new SecretKeySpec(material, algorithm);
    }
}
