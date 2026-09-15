package com.insurancebilling.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;

/**
 * What this platform knows about the bank account a pre-authorised debit is drawn from.
 *
 * <p>It knows the holder's name and the last three digits of the account number. That is all. There is
 * no field here for a full account number, a full transit number or a full institution number, because
 * billing never needs one — the payment processor holds the mandate and this application only has to
 * render enough for a policyholder to recognise which of their accounts is being debited.
 *
 * <p>That is a deliberate design choice and it is the reason the masking tests are worth anything. A
 * platform that stores the full number and masks it on the way out is one forgotten {@code toString},
 * one debug log line or one new endpoint away from leaking it. A platform that never stores it cannot
 * leak it, and no amount of careless code downstream can change that.
 *
 * <p>{@link #of} refuses anything longer than three characters rather than truncating it. Truncating
 * would mean a caller that mistakenly passed a full account number got a working object back and never
 * found out; refusing means the mistake surfaces at the point it was made.
 */
@Embeddable
public class BankAccountReference {

  private static final int LAST_DIGITS_KEPT = 3;

  @Column(name = "bank_account_holder")
  private String accountHolder;

  @Column(name = "bank_account_last_digits", length = LAST_DIGITS_KEPT)
  private String accountLastDigits;

  protected BankAccountReference() {
    // required by JPA
  }

  private BankAccountReference(String accountHolder, String accountLastDigits) {
    this.accountHolder = accountHolder;
    this.accountLastDigits = accountLastDigits;
  }

  /**
   * Creates a reference from the holder's name and the last three digits of their account number.
   *
   * @throws IllegalArgumentException if the holder is blank, or if the digits are not exactly three
   *     numeric characters — including the case where a caller passed a whole account number
   */
  public static BankAccountReference of(String accountHolder, String accountLastDigits) {
    if (accountHolder == null || accountHolder.isBlank()) {
      throw new IllegalArgumentException("A bank account reference needs the account holder's name");
    }
    if (accountLastDigits == null || accountLastDigits.length() != LAST_DIGITS_KEPT) {
      throw new IllegalArgumentException(
          "A bank account reference holds exactly "
              + LAST_DIGITS_KEPT
              + " digits, never a whole account number");
    }
    if (!accountLastDigits.chars().allMatch(Character::isDigit)) {
      throw new IllegalArgumentException("The last digits of an account number must be numeric");
    }
    return new BankAccountReference(accountHolder, accountLastDigits);
  }

  public String getAccountHolder() {
    return accountHolder;
  }

  /** The three digits this platform holds. Never a whole account number. */
  public String getAccountLastDigits() {
    return accountLastDigits;
  }

  /** The account number as it is shown on screen and returned by the API. */
  public String getMaskedAccountNumber() {
    return "****" + accountLastDigits;
  }

  /** The institution number placeholder. This platform never holds the real one. */
  public String getMaskedInstitutionNumber() {
    return "***";
  }

  /** The branch number placeholder. This platform never holds the real one. */
  public String getMaskedBranchNumber() {
    return "*****";
  }

  /**
   * Renders the masked account number, never the digits on their own.
   *
   * <p>Overridden so that a stray log statement or a debugger view shows the same masked form every
   * other caller sees.
   */
  @Override
  public String toString() {
    return getMaskedAccountNumber();
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof BankAccountReference that)) {
      return false;
    }
    return Objects.equals(accountHolder, that.accountHolder)
        && Objects.equals(accountLastDigits, that.accountLastDigits);
  }

  @Override
  public int hashCode() {
    return Objects.hash(accountHolder, accountLastDigits);
  }
}
