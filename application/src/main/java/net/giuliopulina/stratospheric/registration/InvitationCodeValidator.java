package net.giuliopulina.stratospheric.registration;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Value;

import java.util.Set;

public class InvitationCodeValidator implements ConstraintValidator<ValidInvitationCode, String> {

  private final Set<String> validInvitationCodes;

  public InvitationCodeValidator(@Value("${custom.invitation-codes:none}") Set<String> validInvitationCodes) {
    this.validInvitationCodes = validInvitationCodes;
  }

  @Override
  public void initialize(ValidInvitationCode constraintAnnotation) {
    // intentionally left empty
  }

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {

    if (value == null || value.isBlank()) {
      return true;
    }

    return validInvitationCodes.contains(value);
  }
}
