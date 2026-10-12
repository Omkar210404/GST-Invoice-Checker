package io.github.omkar210404.gstcheck.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DecimalsTest {

  @Test
  void padsToTwoDecimalPlaces() {
    assertEquals("10.00", Decimals.show(new BigDecimal("10")));
    assertEquals("10.00", Decimals.show(new BigDecimal("1E+1")));
    assertEquals("0.50", Decimals.show(new BigDecimal("0.5")));
    assertEquals("0.00", Decimals.show(BigDecimal.ZERO));
  }

  @Test
  void keepsExtraPrecisionButDropsTrailingZeros() {
    assertEquals("0.0075", Decimals.show(new BigDecimal("0.0075")));
    assertEquals("0.02", Decimals.show(new BigDecimal("0.0200")));
    assertEquals("10.00", Decimals.show(new BigDecimal("10.0000")));
  }
}
