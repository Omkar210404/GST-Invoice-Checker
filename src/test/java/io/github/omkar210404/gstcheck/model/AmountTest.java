package io.github.omkar210404.gstcheck.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AmountTest {

  @Test
  void parsesPlainDecimalsExactly() {
    assertEquals(new BigDecimal("1250.50"), Amount.parse("1250.50").value());
    assertEquals(new BigDecimal("0.1"), Amount.parse("0.1").value());
    assertEquals(new BigDecimal("18"), Amount.parse(" 18 ").value());
    assertEquals(new BigDecimal("-5.00"), Amount.parse("-5.00").value());
    assertEquals(new BigDecimal("5"), Amount.parse("+5").value());
  }

  @Test
  void keepsTheScaleSoNoPrecisionIsInvented() {
    // 0.1 + 0.2 is exactly 0.3 with BigDecimal; with double it is 0.30000000000000004.
    BigDecimal sum = Amount.parse("0.1").value().add(Amount.parse("0.2").value());
    assertEquals(new BigDecimal("0.3"), sum);
  }

  @Test
  void blankAndNullAreBlankNotInvalid() {
    for (String text : new String[] {null, "", "   "}) {
      Amount amount = Amount.parse(text);
      assertTrue(amount.isBlank());
      assertFalse(amount.isInvalid());
      assertFalse(amount.isPresent());
      assertEquals(BigDecimal.ZERO, amount.orZero());
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "1,200.00",
        "Rs 100",
        "100%",
        "1.2E+05",
        "abc",
        ".5",
        "5.",
        "1 000",
        "--5",
        "0x10",
        "NaN",
        "Infinity",
        "١٢٣"
      })
  void anythingElseIsInvalidAndKeepsItsText(String text) {
    Amount amount = Amount.parse(text);
    assertTrue(amount.isInvalid(), text);
    assertNull(amount.value());
    assertEquals(text.strip(), amount.text());
  }

  @Test
  void veryLongDigitStringsAreRejectedRatherThanParsed() {
    assertTrue(Amount.parse("9".repeat(41)).isInvalid());
    assertTrue(Amount.parse("9".repeat(40)).isPresent());
  }

  @Test
  void signHelpers() {
    assertTrue(Amount.parse("-0.01").isNegative());
    assertFalse(Amount.parse("0").isNegative());
    assertTrue(Amount.parse("0.00").isZero());
    assertFalse(Amount.parse("0.01").isZero());
    assertFalse(Amount.blank().isZero());
    assertFalse(Amount.parse("abc").isNegative());
  }
}
