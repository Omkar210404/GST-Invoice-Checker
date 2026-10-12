package io.github.omkar210404.gstcheck.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.omkar210404.gstcheck.config.CheckerConfig;
import io.github.omkar210404.gstcheck.config.ConfigLoader;
import io.github.omkar210404.gstcheck.csvio.InvoiceCsvReader;
import io.github.omkar210404.gstcheck.model.InvoiceLine;
import io.github.omkar210404.gstcheck.model.Issue;
import io.github.omkar210404.gstcheck.rules.RuleContext;
import io.github.omkar210404.gstcheck.rules.RuleEngine;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Golden-file tests: a fixed input and config must produce exactly the checked-in reports.
 *
 * <p>The fixture is small enough to read, and each expected file was reviewed by hand when it was
 * created. To regenerate after an intended change, run {@code mvnw test -Dgolden.update=true}, then
 * review the diff before committing it.
 */
class GoldenReportTest {

  private static final Path GOLDEN = Path.of("src", "test", "resources", "golden");
  private static final Path EXPECTED = GOLDEN.resolve("expected");

  private static ReportData report;

  @BeforeAll
  static void runTheFixture() {
    CheckerConfig config = ConfigLoader.load(GOLDEN.resolve("config.yml"));
    List<InvoiceLine> lines = new InvoiceCsvReader(config).read(GOLDEN.resolve("input.csv"));
    List<Issue> issues =
        RuleEngine.standard().run(lines, new RuleContext(config, LocalDate.of(2026, 10, 12)));
    report =
        ReportBuilder.build(
            new ReportBuilder.RunInfo(
                "TEST",
                LocalDate.of(2026, 10, 12),
                "golden/input.csv",
                "golden/config.yml",
                config.financialYear().label()),
            lines,
            issues);
  }

  @Test
  void consoleSummaryMatches() throws IOException {
    assertMatchesGolden("console.txt", ConsoleReport.render(report));
  }

  @Test
  void issuesCsvMatches() throws IOException {
    assertMatchesGolden("issues.csv", IssuesCsvReport.render(report));
  }

  @Test
  void jsonReportMatches() throws IOException {
    assertMatchesGolden("report.json", JsonReport.render(report));
  }

  @Test
  void htmlReportMatches() throws IOException {
    assertMatchesGolden("report.html", HtmlReport.render(report));
  }

  @Test
  void theFixtureCoversBothSeveritiesAndCleanInvoices() {
    // Guards against the fixture quietly becoming trivial.
    assertEquals(9, report.summary().lines());
    assertEquals(8, report.summary().invoices());
    assertTrue(report.summary().errors() > 0);
    assertTrue(report.summary().warnings() > 0);
    assertTrue(report.summary().invoicesWithIssues() < report.summary().invoices());
  }

  private static void assertMatchesGolden(String name, String actual) throws IOException {
    Path file = EXPECTED.resolve(name);
    String normalized = actual.replace("\r\n", "\n");
    if (Boolean.getBoolean("golden.update")) {
      Files.createDirectories(EXPECTED);
      Files.writeString(file, normalized, StandardCharsets.UTF_8);
      return;
    }
    assertTrue(
        Files.exists(file),
        "Missing golden file " + file + ". Create it with: mvnw test -Dgolden.update=true");
    String expected = Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
    assertEquals(expected, normalized, "Report differs from golden file " + file);
  }
}
