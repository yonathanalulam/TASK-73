package com.dojostay.auth;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Stateless validator for the dojostay password policy.
 *
 * <p>Pure logic — no Spring dependencies in {@link #validate(String)} except the policy
 * properties wired in via constructor — so it can be unit tested directly.
 */
@Component
public class PasswordPolicyValidator {

    private static final String SPECIAL_CHARS = "!@#$%^&*()-_=+[]{};:'\",.<>/?\\|`~";

    private final PasswordPolicyProperties props;

    public PasswordPolicyValidator(PasswordPolicyProperties props) {
        this.props = props;
    }

    public ValidationResult validate(String password) {
        List<String> failures = new ArrayList<>();
        if (password == null || password.length() < props.getMinLength()) {
            failures.add("Password must be at least " + props.getMinLength() + " characters long");
        }
        if (password != null) {
            if (props.isRequireUppercase() && !password.chars().anyMatch(Character::isUpperCase)) {
                failures.add("Password must contain at least one uppercase letter");
            }
            if (props.isRequireLowercase() && !password.chars().anyMatch(Character::isLowerCase)) {
                failures.add("Password must contain at least one lowercase letter");
            }
            if (props.isRequireDigit() && !password.chars().anyMatch(Character::isDigit)) {
                failures.add("Password must contain at least one digit");
            }
            if (props.isRequireSpecial() && password.chars().noneMatch(c -> SPECIAL_CHARS.indexOf(c) >= 0)) {
                failures.add("Password must contain at least one special character");
            }
        }
        return new ValidationResult(failures.isEmpty(), failures);
    }

    public record ValidationResult(boolean valid, List<String> failures) {
    }
}
