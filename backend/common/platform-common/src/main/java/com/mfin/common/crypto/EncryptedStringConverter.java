package com.mfin.common.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Transparently encrypts a {@code String} column: annotate the field with
 * {@code @Convert(converter = EncryptedStringConverter.class)} and the value is ciphertext at
 * rest while staying plaintext in the domain model.
 *
 * <p>Hibernate instantiates converters itself in some bootstrap paths, so the encryptor is
 * resolved from a static context holder rather than constructor injection.</p>
 */
@Converter
@Component
public class EncryptedStringConverter implements AttributeConverter<String, String>,
        ApplicationContextAware {

    private static volatile FieldEncryptor encryptor;

    @Override
    public void setApplicationContext(ApplicationContext context) throws BeansException {
        setEncryptor(context.getBean(FieldEncryptor.class));
    }

    static void setEncryptor(FieldEncryptor fieldEncryptor) {
        encryptor = fieldEncryptor;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return require().encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return require().decrypt(dbData);
    }

    private FieldEncryptor require() {
        FieldEncryptor current = encryptor;
        if (current == null) {
            // Failing closed is essential: silently persisting PII in the clear is worse
            // than refusing to start.
            throw new IllegalStateException(
                    "FieldEncryptor is not initialised; encrypted columns cannot be read or written");
        }
        return current;
    }
}
