package io.github.omkar210404.gstcheck.sample;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.omkar210404.gstcheck.config.CheckerConfig;
import io.github.omkar210404.gstcheck.config.ConfigLoader;
import io.github.omkar210404.gstcheck.csvio.InvoiceCsvReader;
import io.github.omkar210404.gstcheck.gstin.GstinValidator;
import io.github.omkar210404.gstcheck.model.CheckId;
import io.github.omkar210404.gstcheck.model.InvoiceLine;
import io.github.omkar210404.gstcheck.model.Issue;
import io.github.omkar210404.gstcheck.rules.RuleContext;
import io.github.omkar210404.gstcheck.rules.RuleEngine;
import io.github.omkar210404.gstcheck.sample.SampleDataGenerator.Sample;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class SampleDataGeneratorTest {

  private static final Sample SAMPLE =
      SampleDataGenerator.generate(SampleDataGenerator.DEFAULT_SEED);
  private static final CheckerConfig CONFIG = ConfigLoader.parse(SAMPLE.configYaml(), "sample");
  private static final List<InvoiceLine> LINES =
      new InvoiceCsvReader(CONFIG).read(new StringReader(SAMPLE.invoicesCsv()), "sample");
  private static final List<Issue> ISSUES =
      RuleEngine.standard().run(LINES, new RuleContext(CONFIG, LocalDate.of(2026, 10, 12)));

  private static boolean isFaultInvoice(String invoiceNo) {
    return invoiceNo.regionMatches(true, 0, "ERR", 0, 3);
  }

  @Test
  void theSameSeedAlwaysGivesTheSameFiles() {
    assertEquals(SAMPLE, SampleDataGenerator.generate(SampleDataGenerator.DEFAULT_SEED));
  }

  @Test
  void theSampleConfigIsTheBundledExample() {
    assertEquals(ConfigLoader.exampleText(), SAMPLE.configYaml());
  }

  @Test
  void theCleanInvoicesRaiseNoIssuesAtAll() {
    List<Issue> onCleanInvoices =
        ISSUES.stream().filter(issue -> !isFaultInvoice(issue.invoiceNo())).toList();
    assertTrue(onCleanInvoices.isEmpty(), onCleanInvoices.toString());
    long cleanLines = LINES.stream().filter(line -> !isFaultInvoice(line.invoiceNo())).count();
    assertEquals(17, cleanLines);
  }

  @Test
  void everyCheckInTheCatalogueFiresAtLeastOnce() {
    Set<CheckId> fired = ISSUES.stream().map(Issue::check).collect(Collectors.toSet());
    assertEquals(EnumSet.allOf(CheckId.class), fired);
  }

  @Test
  void eachBrokenRowTripsExactlyTheCheckItsDescriptionNames() {
    for (InvoiceLine line : LINES) {
      if (!line.description().startsWith("SYNTHETIC FAULT:")) {
        continue;
      }
      // The description ends with the intended check ID in brackets, e.g. "(GSTIN-006)".
      String description = line.description();
      String intended =
          description.substring(description.lastIndexOf('(') + 1, description.lastIndexOf(')'));
      Set<String> raised =
          ISSUES.stream()
              .filter(issue -> issue.lineNo() == line.lineNo())
              .map(issue -> issue.check().id())
              .collect(Collectors.toSet());
      assertEquals(Set.of(intended), raised, "row " + line.lineNo() + ": " + description);
    }
  }

  @Test
  void setUpRowsAreCleanThemselves() {
    for (InvoiceLine line : LINES) {
      if (line.description().startsWith("Synthetic setup row")) {
        assertTrue(
            ISSUES.stream().noneMatch(issue -> issue.lineNo() == line.lineNo()),
            "row " + line.lineNo());
      }
    }
  }

  @Test
  void everyGstinOnACleanInvoiceIsStructurallyValid() {
    GstinValidator validator = new GstinValidator(CONFIG.stateCodes());
    for (InvoiceLine line : LINES) {
      if (isFaultInvoice(line.invoiceNo())) {
        continue;
      }
      assertTrue(validator.validate(line.supplierGstin()).structurallyValid());
      if (!line.buyerGstin().isEmpty()) {
        assertTrue(validator.validate(line.buyerGstin()).structurallyValid());
      }
    }
  }

  @Test
  void everyPartyIsLabelledFictionalAndEveryItemSynthetic() {
    for (InvoiceLine line : LINES) {
      assertTrue(line.supplierName().contains("(FICTIONAL)"), line.supplierName());
      assertTrue(line.buyerName().contains("(FICTIONAL)"), line.buyerName());
      assertTrue(line.description().toLowerCase().contains("synthetic"), line.description());
    }
  }

  @Test
  void theSampleIncludesAnUnregisteredBuyerAndAUtgstTerritory() {
    assertTrue(LINES.stream().anyMatch(line -> line.buyerGstin().isEmpty()));
    assertTrue(LINES.stream().anyMatch(line -> CONFIG.chargesUtgst(line.placeOfSupply())));
  }

  @Test
  void theReadmeSaysTheDataIsSyntheticAndGstinsMustNotBeUsedAsIdentities() {
    assertTrue(SAMPLE.readme().contains("SYNTHETIC"));
    assertTrue(SAMPLE.readme().contains("fictional"));
    assertTrue(SAMPLE.readme().contains("never use one as a real identity"));
    assertFalse(SAMPLE.readme().startsWith(" "), "text block indentation was stripped");
  }

  @Test
  void theSampleCommittedToTheRepoMatchesTheGenerator() throws IOException {
    Path dir = Path.of("sample");
    assertEquals(SAMPLE.invoicesCsv(), read(dir.resolve("sample-invoices.csv")));
    assertEquals(SAMPLE.configYaml(), read(dir.resolve("gst-checker.yml")));
    assertEquals(SAMPLE.readme(), read(dir.resolve("SAMPLE-README.txt")));
  }

  private static String read(Path file) throws IOException {
    assertTrue(Files.exists(file), file + " is missing. Run: gst-invoice-checker sample --force");
    return Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
  }
}
