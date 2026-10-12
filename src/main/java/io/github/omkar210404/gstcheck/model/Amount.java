package io.github.omkar210404.gstcheck.model;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * A number read from the CSV. It keeps the original text and, when that text is a plain decimal,
 * the exact {@link BigDecimal} value. Money is never held in a {@code double}.
 *
 * <p>Three states: blank (no text), invalid (text that is not a plain decimal) and present.
 */
public record Amount(String text, BigDecimal value) {

  /** Digits with an optional sign and fraction. No thousands separators, symbols or exponents. */
  private static final Pattern PLAIN_DECIMAL = Pattern.compile("[+-]?\\d+(\\.\\d+)?");

  private static final Amount BLANK = new Amount("", null);

  public Amount {
    text = text == null ? "" : text;
  }

  /** Parses CSV text. Never throws: unparseable text gives an invalid amount. */
  public static Amount parse(String raw) {
    if (raw == null) {
      return BLANK;
    }
    String trimmed = raw.strip();
    if (trimmed.isEmpty()) {
      return BLANK;
    }
    // A cap on length keeps BigDecimal parsing cheap for hostile input.
    if (trimmed.length() <= 40 && PLAIN_DECIMAL.matcher(trimmed).matches()) {
      return new Amount(trimmed, new BigDecimal(trimmed));
    }
    return new Amount(trimmed, null);
  }

  public static Amount blank() {
    return BLANK;
  }

  public boolean isBlank() {
    return text.isEmpty();
  }

  /** True when there is text but it is not a plain decimal number. */
  public boolean isInvalid() {
    return !text.isEmpty() && value == null;
  }

  public boolean isPresent() {
    return value != null;
  }

  /** The value, or zero when the cell was blank. Only call this after checking for invalid. */
  public BigDecimal orZero() {
    return value == null ? BigDecimal.ZERO : value;
  }

  public boolean isNegative() {
    return value != null && value.signum() < 0;
  }

  public boolean isZero() {
    return value != null && value.signum() == 0;
  }
}
