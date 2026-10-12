package io.github.omkar210404.gstcheck.rules;

import java.math.BigDecimal;

/** Formatting of computed amounts for messages. Values are never rounded, only padded. */
final class Decimals {

  private Decimals() {}

  /**
   * Shows a computed value with at least two decimal places and no needless trailing zeros beyond
   * that: 10 becomes {@code 10.00}, 0.0075 stays {@code 0.0075}.
   */
  static String show(BigDecimal value) {
    BigDecimal stripped = value.stripTrailingZeros();
    return (stripped.scale() < 2 ? stripped.setScale(2) : stripped).toPlainString();
  }
}
