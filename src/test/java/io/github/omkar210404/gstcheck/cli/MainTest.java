package io.github.omkar210404.gstcheck.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.omkar210404.gstcheck.testutil.Fixtures;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** End-to-end tests of the command line, mainly the exit codes scripts and CI rely on. */
class MainTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-12T06:00:00Z"), ZoneOffset.UTC);

  private static final String HEADER =
      "invoice_no,invoice_date,supplier_gstin,buyer_gstin,place_of_supply_state_code,hsn_or_sac,"
          + "description,quantity,unit,unit_price,taxable_value,rate,cgst,sgst,igst,cess,line_total\n";

  private static final String CLEAN_ROW =
      "INV/1,2026-07-15,"
          + Fixtures.SUPPLIER_27
          + ","
          + Fixtures.BUYER_27
          + ",27,123456,Item,2,NOS,500.00,1000.00,18,90.00,90.00,0.00,0.00,1180.00\n";

  @TempDir Path dir;

  private StringWriter out;
  private StringWriter err;
  private Path config;

  @BeforeEach
  void setUp() throws IOException {
    out = new StringWriter();
    err = new StringWriter();
    // The sample command writes the example config; use that as the config for every test.
    assertEquals(0, run("sample", "--dir", dir.resolve("sample").toString()));
    config = dir.resolve("sample").resolve("gst-checker.yml");
    out.getBuffer().setLength(0);
  }

  private int run(String... args) {
    return Main.run(args, new PrintWriter(out, true), new PrintWriter(err, true), CLOCK);
  }

  private Path csv(String name, String content) throws IOException {
    Path file = dir.resolve(name);
    Files.writeString(file, content, StandardCharsets.UTF_8);
    return file;
  }

  private int check(Path file, String... extra) {
    String[] args = new String[6 + extra.length];
    args[0] = "check";
    args[1] = file.toString();
    args[2] = "--config";
    args[3] = config.toString();
    args[4] = "--out-dir";
    args[5] = dir.resolve("reports").toString();
    System.arraycopy(extra, 0, args, 6, extra.length);
    return run(args);
  }

  // --- check: exit codes ---

  @Test
  void aCleanFileExitsZeroAndWritesAllThreeReports() throws IOException {
    assertEquals(0, check(csv("clean.csv", HEADER + CLEAN_ROW)));
    assertTrue(out.toString().contains("Result: PASSED - no errors"));
    assertTrue(Files.size(dir.resolve("reports/issues.csv")) > 0);
    assertTrue(Files.readString(dir.resolve("reports/report.json")).contains("\"errors\" : 0"));
    assertTrue(Files.readString(dir.resolve("reports/report.html")).contains("<!doctype html>"));
    assertEquals("", err.toString());
  }

  @Test
  void aFileWithErrorsExitsOne() throws IOException {
    assertEquals(1, check(csv("bad.csv", HEADER + CLEAN_ROW.replace(",123456,", ",12,"))));
    assertTrue(out.toString().contains("Result: FAILED - 1 error(s)"));
    assertTrue(out.toString().contains("HSN-002"));
  }

  @Test
  void warningsAloneExitZeroUnlessStrict() throws IOException {
    Path file =
        csv(
            "warn.csv",
            HEADER + CLEAN_ROW.replace(Fixtures.BUYER_27, Fixtures.BUYER_27.toLowerCase()));
    assertEquals(0, check(file));
    assertTrue(out.toString().contains("PASSED - no errors, 1 warning(s)"));
    assertFalse(out.toString().contains("Strict mode"));
    assertEquals(1, check(file, "--strict"));
    assertTrue(
        out.toString().contains("Strict mode: warnings count as failures"),
        "the console must explain why a run with no errors exits 1");
  }

  @Test
  void theSampleFileExitsOneAndEveryCheckFires() {
    assertEquals(1, check(dir.resolve("sample").resolve("sample-invoices.csv")));
    String text = out.toString();
    for (String id : new String[] {"DATA-001", "GSTIN-006", "INV-003", "TAX-001", "RATE-001"}) {
      assertTrue(text.contains(id), id);
    }
  }

  @Test
  void aHeaderOnlyFileExitsZeroAndSaysNothingWasChecked() throws IOException {
    assertEquals(0, check(csv("empty.csv", HEADER)));
    assertTrue(out.toString().contains("no data rows"));
  }

  // --- check: usage and input problems are exit code 2 ---

  @Test
  void aMissingInputFileExitsTwo() {
    assertEquals(2, check(dir.resolve("nope.csv")));
    assertTrue(err.toString().contains("Input file not found"));
  }

  @Test
  void aMissingConfigFileExitsTwo() throws IOException {
    Path file = csv("clean.csv", HEADER + CLEAN_ROW);
    assertEquals(2, run("check", file.toString(), "--config", dir.resolve("nope.yml").toString()));
    assertTrue(err.toString().contains("Config file not found"));
  }

  @Test
  void anInvalidConfigExitsTwoAndNamesTheProblem() throws IOException {
    Path bad = dir.resolve("bad.yml");
    Files.writeString(bad, "financial_year: \"2026-27\"\n");
    Path file = csv("clean.csv", HEADER + CLEAN_ROW);
    assertEquals(2, run("check", file.toString(), "--config", bad.toString()));
    assertTrue(err.toString().contains("rates.allowed_slabs is required"));
  }

  @Test
  void aMissingRequiredColumnExitsTwo() throws IOException {
    assertEquals(2, check(csv("short.csv", "invoice_no,invoice_date\nINV/1,2026-07-15\n")));
    assertTrue(err.toString().contains("missing required column"));
  }

  @Test
  void badArgumentsExitTwo() throws IOException {
    Path file = csv("clean.csv", HEADER + CLEAN_ROW);
    assertEquals(2, run());
    assertEquals(2, run("check"));
    assertEquals(2, run("nonsense"));
    assertEquals(2, check(file, "--no-such-option"));
    assertEquals(2, check(file, "--as-of", "12/10/2026"));
  }

  // --- check: options ---

  @Test
  void asOfControlsWhatCountsAsTheFuture() throws IOException {
    Path file = csv("later.csv", HEADER + CLEAN_ROW.replace("2026-07-15", "2026-12-25"));
    // The fixed test clock says 2026-10-12, so 25 December is in the future.
    assertEquals(1, check(file));
    assertTrue(out.toString().contains("DATE-002"));
    assertEquals(0, check(file, "--as-of", "2026-12-25"));
  }

  @Test
  void maxIssuesLimitsTheConsoleList() {
    Path sample = dir.resolve("sample").resolve("sample-invoices.csv");
    assertEquals(1, check(sample, "--max-issues", "3"));
    assertTrue(out.toString().contains("First 3 of 30 issues"));
  }

  @Test
  void theConsoleOutputAlwaysCarriesBothNotices() throws IOException {
    check(csv("clean.csv", HEADER + CLEAN_ROW));
    String text = out.toString().replace("\n", " ").replace("\r", "");
    assertTrue(text.contains("does not prove the GSTIN is registered or active"));
    assertTrue(text.contains("not tax advice"));
  }

  // --- gstin ---

  @Test
  void gstinExitsZeroForAStructurallyValidNumberAndSaysWhatThatMeans() {
    assertEquals(0, run("gstin", Fixtures.SUPPLIER_27));
    String text = out.toString();
    assertTrue(text.contains("Result: STRUCTURALLY VALID"));
    assertTrue(text.contains("27 Maharashtra"));
    assertTrue(text.contains("[PASS] GSTIN-006"));
    assertTrue(text.contains("does not prove the GSTIN is registered or active"));
  }

  @Test
  void gstinExitsOneAndExplainsAFailure() {
    String wrong =
        Fixtures.SUPPLIER_27.substring(0, 14)
            + (Fixtures.SUPPLIER_27.charAt(14) == 'A' ? 'B' : 'A');
    assertEquals(1, run("gstin", wrong));
    String text = out.toString();
    assertTrue(text.contains("Result: NOT VALID"));
    assertTrue(text.contains("This GSTIN is malformed"));
    assertTrue(text.contains("does not prove a GSTIN is registered or active"));
    assertTrue(text.contains("[FAIL] GSTIN-006"));
    assertTrue(text.contains("Fix:"));
    assertTrue(text.contains("[PASS] GSTIN-002"));
  }

  @Test
  void gstinSkipsPositionalChecksWhenTheLengthIsWrong() {
    assertEquals(1, run("gstin", "27ABC"));
    String text = out.toString();
    assertTrue(text.contains("[FAIL] GSTIN-001"));
    assertTrue(text.contains("[SKIP] GSTIN-006"));
  }

  @Test
  void gstinTreatsLowerCaseAsAWarningNotAFailure() {
    assertEquals(0, run("gstin", Fixtures.SUPPLIER_27.toLowerCase()));
    assertTrue(out.toString().contains("[WARN] GSTIN-007"));
  }

  @Test
  void gstinTakesStateCodesFromAConfigWhenGiven() throws IOException {
    Path only24 = dir.resolve("only24.yml");
    Files.writeString(
        only24,
        "financial_year: \"2026-27\"\nrates:\n  allowed_slabs: [18]\n"
            + "states:\n  \"24\": { name: \"Gujarat\" }\n");
    assertEquals(1, run("gstin", Fixtures.SUPPLIER_27, "--config", only24.toString()));
    assertTrue(out.toString().contains("[FAIL] GSTIN-002"));
    assertEquals(0, run("gstin", Fixtures.BUYER_24, "--config", only24.toString()));
  }

  @Test
  void gstinWithoutAnArgumentExitsTwo() {
    assertEquals(2, run("gstin"));
  }

  // --- sample ---

  @Test
  void sampleWritesThreeFilesAndRefusesToOverwriteThem() {
    Path target = dir.resolve("fresh");
    assertEquals(0, run("sample", "--dir", target.toString()));
    assertTrue(Files.exists(target.resolve("sample-invoices.csv")));
    assertTrue(Files.exists(target.resolve("gst-checker.yml")));
    assertTrue(Files.exists(target.resolve("SAMPLE-README.txt")));
    assertTrue(out.toString().contains("SYNTHETIC"));

    assertEquals(2, run("sample", "--dir", target.toString()));
    assertTrue(err.toString().contains("already exists"));
    assertEquals(0, run("sample", "--dir", target.toString(), "--force"));
  }

  @Test
  void sampleIsRepeatableForASeedAndDiffersBetweenSeeds() throws IOException {
    run("sample", "--dir", dir.resolve("a").toString(), "--seed", "7");
    run("sample", "--dir", dir.resolve("b").toString(), "--seed", "7");
    run("sample", "--dir", dir.resolve("c").toString(), "--seed", "8");
    String a = Files.readString(dir.resolve("a/sample-invoices.csv"));
    assertEquals(a, Files.readString(dir.resolve("b/sample-invoices.csv")));
    assertFalse(a.equals(Files.readString(dir.resolve("c/sample-invoices.csv"))));
  }

  // --- top level ---

  @Test
  void helpAndVersionExitZero() {
    assertEquals(0, run("--help"));
    assertTrue(out.toString().contains("check"));
    assertTrue(out.toString().contains("not tax advice"));
    assertEquals(0, run("--version"));
    assertTrue(out.toString().contains("gst-invoice-checker "));
    assertEquals(0, run("check", "--help"));
  }
}
