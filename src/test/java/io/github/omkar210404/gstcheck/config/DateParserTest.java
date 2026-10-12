package io.github.omkar210404.gstcheck.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.omkar210404.gstcheck.model.InputProblemException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DateParserTest {

  private final DateParser parser =
      new DateParser(List.of("yyyy-MM-dd", "dd-MM-yyyy", "dd/MM/yyyy"));

  @Test
  void parsesEachConfiguredFormat() {
    Optional<LocalDate> expected = Optional.of(LocalDate.of(2026, 7, 15));
    assertEquals(expected, parser.parse("2026-07-15"));
    assertEquals(expected, parser.parse("15-07-2026"));
    assertEquals(expected, parser.parse("15/07/2026"));
    assertEquals(expected, parser.parse("  2026-07-15  "));
  }

  @Test
  void rejectsDatesThatDoNotExist() {
    assertEquals(Optional.empty(), parser.parse("2027-02-30"));
    assertEquals(Optional.empty(), parser.parse("31-04-2026"));
    assertEquals(Optional.empty(), parser.parse("2026-13-01"));
    assertEquals(Optional.empty(), parser.parse("00/01/2026"));
  }

  @Test
  void leapDayIsOnlyValidInALeapYear() {
    assertEquals(Optional.of(LocalDate.of(2028, 2, 29)), parser.parse("29-02-2028"));
    assertEquals(Optional.empty(), parser.parse("29-02-2027"));
  }

  @Test
  void rejectsTextThatMatchesNoFormat() {
    assertEquals(Optional.empty(), parser.parse("15 July 2026"));
    assertEquals(Optional.empty(), parser.parse("2026-7-15"));
    assertEquals(Optional.empty(), parser.parse("46218"));
    assertEquals(Optional.empty(), parser.parse("2026-07-15T10:00"));
  }

  @Test
  void blankAndNullAreEmpty() {
    assertEquals(Optional.empty(), parser.parse(""));
    assertEquals(Optional.empty(), parser.parse("   "));
    assertEquals(Optional.empty(), parser.parse(null));
  }

  @Test
  void monthNamesWorkInAnyCase() {
    DateParser named = new DateParser(List.of("dd-MMM-yyyy"));
    assertEquals(Optional.of(LocalDate.of(2026, 10, 12)), named.parse("12-Oct-2026"));
    assertEquals(Optional.of(LocalDate.of(2026, 10, 12)), named.parse("12-OCT-2026"));
  }

  @Test
  void anInvalidPatternIsAConfigProblem() {
    assertThrows(InputProblemException.class, () -> new DateParser(List.of("not a pattern {")));
  }
}
