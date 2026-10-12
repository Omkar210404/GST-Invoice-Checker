package io.github.omkar210404.gstcheck.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.omkar210404.gstcheck.model.InputProblemException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FinancialYearTest {

  @Test
  void parsesALabel() {
    FinancialYear year = FinancialYear.parse(" 2026-27 ");
    assertEquals(LocalDate.of(2026, 4, 1), year.start());
    assertEquals(LocalDate.of(2027, 3, 31), year.end());
    assertEquals("2026-27", year.label());
  }

  @Test
  void handlesTheCenturyBoundary() {
    assertEquals("2099-00", FinancialYear.parse("2099-00").label());
    assertEquals(LocalDate.of(2100, 3, 31), FinancialYear.parse("2099-00").end());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"", "2026", "2026-2027", "26-27", "2026/27", "2026-28", "2026-26", "abcd-ef"})
  void rejectsMalformedOrNonConsecutiveLabels(String label) {
    assertThrows(InputProblemException.class, () -> FinancialYear.parse(label));
  }

  @Test
  void rejectsNull() {
    assertThrows(InputProblemException.class, () -> FinancialYear.parse(null));
  }

  @Test
  void containsIsInclusiveAtBothEnds() {
    FinancialYear year = new FinancialYear(2026);
    assertFalse(year.contains(LocalDate.of(2026, 3, 31)));
    assertTrue(year.contains(LocalDate.of(2026, 4, 1)));
    assertTrue(year.contains(LocalDate.of(2027, 3, 31)));
    assertFalse(year.contains(LocalDate.of(2027, 4, 1)));
  }

  @Test
  void findsTheYearADateFallsIn() {
    assertEquals(new FinancialYear(2025), FinancialYear.containing(LocalDate.of(2026, 3, 31)));
    assertEquals(new FinancialYear(2026), FinancialYear.containing(LocalDate.of(2026, 4, 1)));
    assertEquals(new FinancialYear(2026), FinancialYear.containing(LocalDate.of(2027, 1, 15)));
  }
}
