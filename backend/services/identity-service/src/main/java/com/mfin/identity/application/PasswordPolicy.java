package com.mfin.identity.application;

import com.mfin.common.error.ApiExceptions;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Password rules, applied on registration, change and reset.
 *
 * <p>Follows NIST SP 800-63B: length matters far more than composition gymnastics, and a
 * blocklist of trivially guessable values catches what entropy rules miss. Composition is
 * still required here because financial regulators generally expect it.</p>
 */
@Component
public class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;

    /** A short illustrative blocklist; production loads the full breached-password corpus. */
    private static final Set<String> BLOCKLIST = Set.of(
            "password", "password123", "qwerty", "letmein", "welcome", "admin", "changeme",
            "microfinance", "loan1234", "12345678", "iloveyou");

    public void validate(String password, String username, String email) {
        List<String> failures = new java.util.ArrayList<>();
        if (password == null || password.length() < MIN_LENGTH) {
            failures.add("be at least " + MIN_LENGTH + " characters long");
        }
        if (password != null && password.length() > MAX_LENGTH) {
            // Bounded so an enormous input cannot be used to burn CPU in BCrypt.
            failures.add("be at most " + MAX_LENGTH + " characters long");
        }
        if (password != null) {
            if (password.chars().noneMatch(Character::isUpperCase)) {
                failures.add("contain an upper-case letter");
            }
            if (password.chars().noneMatch(Character::isLowerCase)) {
                failures.add("contain a lower-case letter");
            }
            if (password.chars().noneMatch(Character::isDigit)) {
                failures.add("contain a digit");
            }
            if (password.chars().allMatch(Character::isLetterOrDigit)) {
                failures.add("contain a symbol");
            }
            String lower = password.toLowerCase();
            if (BLOCKLIST.contains(lower)) {
                failures.add("not be a commonly used password");
            }
            if (username != null && !username.isBlank() && lower.contains(username.toLowerCase())) {
                failures.add("not contain your username");
            }
            if (email != null && email.contains("@")) {
                String localPart = email.substring(0, email.indexOf('@')).toLowerCase();
                if (localPart.length() > 2 && lower.contains(localPart)) {
                    failures.add("not contain your email address");
                }
            }
        }
        if (!failures.isEmpty()) {
            // The message lists every unmet rule at once so the user is not forced to guess twice.
            throw new ApiExceptions.BusinessRuleException(
                    "Password must " + String.join(", ", failures));
        }
    }
}
