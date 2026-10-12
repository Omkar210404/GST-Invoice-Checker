package io.github.omkar210404.gstcheck.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.omkar210404.gstcheck.model.CheckId;
import io.github.omkar210404.gstcheck.model.Column;
import io.github.omkar210404.gstcheck.model.InputProblemException;
import io.github.omkar210404.gstcheck.model.Severity;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigLoaderTest {

  /** The smallest config the loader accepts: the three settings with no safe default. */
  private static final String MINIMAL =
      """
      financial_year: "2026-27"
      rates:
        allowed_slabs: [0, 5, 18]
      states:
        "27": { name: "Maharashtra" }
        "04": { name: "Chandigarh", utgst: true }
      """;

  private static CheckerConfig parse(String yaml) {
    return ConfigLoader.parse(yaml, "test");
  }

  private static String messageOf(String yaml) {
    return assertThrows(InputProblemException.class, () -> parse(yaml)).getMessage();
  }

  @Test
  void theBundledExampleLoads() {
    CheckerConfig config = ConfigLoader.loadExample();
    assertEquals("2026-27", config.financialYear().label());
    assertEquals(Set.of(4, 6, 8), config.hsnAllowedLengths());
    assertTrue(config.isKnownState("27"));
    assertTrue(config.isKnownState("07"), "leading zero must survive");
    assertFalse(config.isKnownState("99"));
    assertTrue(config.chargesUtgst("04"));
    assertFalse(config.chargesUtgst("27"));
    assertEquals(new BigDecimal("0.01"), config.tolerance().taxAmount());
    assertTrue(config.equivalentStateCodes().isEmpty());
  }

  @Test
  void theBundledExampleSaysItsRatesAreOnlyAnExample() {
    String text = ConfigLoader.exampleText();
    assertTrue(text.contains("EXAMPLE ONLY"));
    assertTrue(text.contains("official GST"));
  }

  @Test
  void minimalConfigGetsDefaultsForTheRest() {
    CheckerConfig config = parse(MINIMAL);
    assertEquals(Set.of(4, 6, 8), config.hsnAllowedLengths());
    assertEquals(new BigDecimal("0.01"), config.tolerance().lineTotal());
    assertEquals(3, config.dateFormats().size());
    assertEquals("invoice_no", config.headerFor(Column.INVOICE_NO));
    assertEquals(Severity.ERROR, config.severityOf(CheckId.GSTIN_006));
    assertFalse(config.isDisabled(CheckId.MATH_003));
  }

  @Test
  void slabsCompareByValueNotByScale() {
    CheckerConfig config = parse(MINIMAL);
    assertTrue(config.isSlabAllowed(new BigDecimal("18")));
    assertTrue(config.isSlabAllowed(new BigDecimal("18.00")));
    assertTrue(config.isSlabAllowed(new BigDecimal("0.0")));
    assertFalse(config.isSlabAllowed(new BigDecimal("12")));
  }

  @Test
  void fractionalSlabsAreReadExactly() {
    CheckerConfig config = parse(MINIMAL.replace("[0, 5, 18]", "[0.25, 0.1, 3]"));
    // Read through a double, 0.1 would not equal the decimal 0.1.
    assertTrue(config.isSlabAllowed(new BigDecimal("0.1")));
    assertTrue(config.isSlabAllowed(new BigDecimal("0.25")));
    assertEquals(new BigDecimal("0.1"), config.allowedSlabs().get(1));
  }

  @Test
  void financialYearIsRequired() {
    assertTrue(
        messageOf(MINIMAL.replace("financial_year: \"2026-27\"", "")).contains("financial_year"));
  }

  @Test
  void ratesAreRequiredBecauseTheToolHasNoBuiltInRateList() {
    String yaml =
        """
        financial_year: "2026-27"
        states:
          "27": { name: "Maharashtra" }
        """;
    assertTrue(messageOf(yaml).contains("rates.allowed_slabs is required"));
  }

  @Test
  void statesAreRequired() {
    String yaml =
        """
        financial_year: "2026-27"
        rates:
          allowed_slabs: [18]
        """;
    assertTrue(messageOf(yaml).contains("states is required"));
  }

  @Test
  void unknownKeysAreRejected() {
    assertTrue(messageOf(MINIMAL + "tolerence:\n  tax_amount: \"1\"\n").contains("tolerence"));
  }

  @Test
  void stateKeysMustBeTwoDigits() {
    assertTrue(messageOf(MINIMAL.replace("\"27\":", "\"7\":")).contains("two digits"));
    assertTrue(messageOf(MINIMAL.replace("\"27\":", "\"MH\":")).contains("two digits"));
  }

  @Test
  void slabsMustBePercentages() {
    assertTrue(messageOf(MINIMAL.replace("[0, 5, 18]", "[-1]")).contains("between 0 and 100"));
    assertTrue(messageOf(MINIMAL.replace("[0, 5, 18]", "[101]")).contains("between 0 and 100"));
    assertTrue(messageOf(MINIMAL.replace("[0, 5, 18]", "[]")).contains("required"));
  }

  @Test
  void toleranceCanBeSetAndMustNotBeNegative() {
    CheckerConfig config = parse(MINIMAL + "tolerance:\n  tax_amount: \"0.50\"\n");
    assertEquals(new BigDecimal("0.50"), config.tolerance().taxAmount());
    assertEquals(new BigDecimal("0.01"), config.tolerance().lineTotal(), "unset ones default");
    assertTrue(
        messageOf(MINIMAL + "tolerance:\n  line_total: \"-1\"\n").contains("must not be negative"));
  }

  @Test
  void equivalentStateCodesMustBeKnownStatesInGroupsOfTwoOrMore() {
    CheckerConfig config = parse(MINIMAL + "equivalent_state_codes: [[\"27\", \"04\"]]\n");
    assertTrue(config.sameTerritory("27", "04"));
    assertTrue(config.sameTerritory("04", "27"));
    assertTrue(config.sameTerritory("27", "27"));
    assertFalse(parse(MINIMAL).sameTerritory("27", "04"));
    assertTrue(
        messageOf(MINIMAL + "equivalent_state_codes: [[\"27\", \"99\"]]\n")
            .contains("not listed under states"));
    assertTrue(
        messageOf(MINIMAL + "equivalent_state_codes: [[\"27\"]]\n").contains("at least two"));
  }

  @Test
  void columnsCanBeRenamedButOnlyKnownOnes() {
    CheckerConfig config = parse(MINIMAL + "columns:\n  invoice_no: \"Bill No\"\n");
    assertEquals("Bill No", config.headerFor(Column.INVOICE_NO));
    assertEquals("rate", config.headerFor(Column.RATE));
    assertTrue(
        messageOf(MINIMAL + "columns:\n  bill_number: \"Bill No\"\n")
            .contains("not a column this tool knows"));
  }

  @Test
  void checksCanBeDisabledAndReclassified() {
    CheckerConfig config =
        parse(
            MINIMAL
                + "checks:\n  disabled: [\"MATH-003\"]\n  severity: { \"MATH-005\": \"error\" }\n");
    assertTrue(config.isDisabled(CheckId.MATH_003));
    assertEquals(Severity.ERROR, config.severityOf(CheckId.MATH_005));
    assertEquals(Severity.WARNING, config.severityOf(CheckId.MATH_003));
  }

  @Test
  void unknownCheckIdsAndSeveritiesAreRejected() {
    assertTrue(
        messageOf(MINIMAL + "checks:\n  disabled: [\"NOPE-001\"]\n").contains("not a known check"));
    assertTrue(
        messageOf(MINIMAL + "checks:\n  severity: { \"MATH-005\": \"fatal\" }\n")
            .contains("\"error\" or \"warning\""));
  }

  @Test
  void badDatePatternsAndHsnLengthsAreRejected() {
    assertTrue(messageOf(MINIMAL + "date_formats: [\"{bad\"]\n").contains("date_formats"));
    assertTrue(messageOf(MINIMAL + "hsn:\n  allowed_lengths: [0]\n").contains("between 1 and 20"));
  }

  @Test
  void emptyAndMalformedYamlAreInputProblems() {
    assertThrows(InputProblemException.class, () -> parse(""));
    assertThrows(InputProblemException.class, () -> parse("financial_year: [unclosed"));
    assertThrows(InputProblemException.class, () -> parse("just a string"));
  }

  @Test
  void errorMessagesNameTheSource() {
    String message =
        assertThrows(InputProblemException.class, () -> ConfigLoader.parse("", "my-config.yml"))
            .getMessage();
    assertTrue(message.contains("my-config.yml"));
  }

  @Test
  void loadsFromAFileAndReportsAMissingOne(@TempDir Path dir) throws IOException {
    Path file = dir.resolve("gst-checker.yml");
    Files.writeString(file, MINIMAL);
    assertEquals("2026-27", ConfigLoader.load(file).financialYear().label());

    String message =
        assertThrows(
                InputProblemException.class, () -> ConfigLoader.load(dir.resolve("missing.yml")))
            .getMessage();
    assertTrue(message.contains("not found"));
  }
}
