package io.github.omkar210404.gstcheck.report;

import static io.github.omkar210404.gstcheck.testutil.Fixtures.validLine;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.omkar210404.gstcheck.model.InvoiceLine;
import io.github.omkar210404.gstcheck.model.Issue;
import io.github.omkar210404.gstcheck.rules.RuleEngine;
import io.github.omkar210404.gstcheck.testutil.Fixtures;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;

/** Behaviour of the four report formats beyond what the golden files pin down. */
class ReportFormatsTest {

  private static final String HOSTILE = "</script><script>alert(1)</script><img src=x onerror=y>&";

  private static ReportData build(String inputFile, InvoiceLine... lines) {
    List<InvoiceLine> list = List.of(lines);
    List<Issue> issues = RuleEngine.standard().run(list, Fixtures.context());
    return ReportBuilder.build(
        new ReportBuilder.RunInfo("TEST", Fixtures.AS_OF, inputFile, "cfg.yml", "2026-27"),
        list,
        issues);
  }

  private static ReportData clean() {
    return build("in.csv", validLine().build());
  }

  private static ReportData failing() {
    return build("in.csv", validLine().hsnOrSac("12").build());
  }

  // --- HTML ---

  @Test
  void htmlNeverContainsHostileInputAsMarkup() {
    String html =
        HtmlReport.render(
            build(HOSTILE, validLine().description(HOSTILE).invoiceNo(HOSTILE).build()));
    assertFalse(html.contains(HOSTILE), "raw input must not appear in the page");
    assertFalse(html.contains("<img src=x"));
    assertEquals(
        2, count(html, "</script>"), "only the page's own two script elements may be closed");
  }

  @Test
  void htmlEmbedsDataThatParsesBackToTheSameValues() throws IOException {
    String html = HtmlReport.render(build("in.csv", validLine().description(HOSTILE).build()));
    Matcher matcher =
        Pattern.compile(
                "<script id=\"report-data\" type=\"application/json\">(.*?)</script>",
                Pattern.DOTALL)
            .matcher(html);
    assertTrue(matcher.find());
    JsonNode data = new ObjectMapper().readTree(matcher.group(1));
    assertEquals(HOSTILE, data.at("/invoices/0/lines/0/description").asText());
    assertEquals(1, data.at("/summary/lines").asInt());
  }

  @Test
  void htmlDoesNotExpandPlaceholdersThatComeFromTheData() {
    String html = HtmlReport.render(build("{{DISCLAIMER}}", validLine().build()));
    assertTrue(html.contains("{{DISCLAIMER}}"), "text from the data is left exactly as it was");
    assertFalse(html.contains("{{TOOL}}"), "every template placeholder was filled in");
    assertFalse(html.contains("{{REPORT_JSON}}"));
  }

  @Test
  void htmlIsSelfContainedAndMakesNoNetworkReferences() {
    String html = HtmlReport.render(clean());
    assertFalse(html.contains("http://"));
    assertFalse(html.contains("https://"));
    assertFalse(html.contains("src=\""), "no external scripts or images");
    assertFalse(html.contains("<link"), "no external stylesheets");
    assertTrue(html.contains("default-src 'none'"), "content security policy blocks all loading");
  }

  @Test
  void htmlCarriesTheDisclaimerAndTheStructuralOnlyNotice() {
    String html = HtmlReport.render(clean());
    assertTrue(html.contains("not tax advice"));
    assertTrue(html.contains("does not prove the GSTIN is registered or active"));
    assertTrue(html.contains("<footer>"));
    int footer = html.indexOf("<footer>");
    assertTrue(html.indexOf("not tax advice", footer) > footer, "the disclaimer is in the footer");
  }

  @Test
  void htmlEscapingHelpers() {
    assertEquals(
        "&lt;a href=&quot;x&quot;&gt;&amp;&#39;", HtmlReport.escapeHtml("<a href=\"x\">&'"));
    assertEquals(
        "{\"a\":\"\\u003c/script\\u003e\\u0026\"}",
        HtmlReport.escapeJsonForScript("{\"a\":\"</script>&\"}"));
  }

  // --- JSON ---

  @Test
  void jsonUsesSnakeCaseAndUnixLineEndings() throws IOException {
    String json = JsonReport.render(failing());
    assertFalse(json.contains("\r"));
    assertTrue(json.endsWith("}\n"));
    JsonNode root = new ObjectMapper().readTree(json);
    assertEquals("TEST", root.get("tool_version").asText());
    assertEquals(1, root.at("/summary/errors").asInt());
    assertEquals("HSN-002", root.at("/issues/0/check_id").asText());
    assertEquals("error", root.at("/issues/0/severity").asText());
    assertEquals(2, root.at("/issues/0/line_no").asInt());
    assertFalse(root.at("/issues/0/suggested_fix").asText().isBlank());
    assertTrue(root.get("disclaimer").asText().contains("not tax advice"));
    assertTrue(root.get("structural_notice").asText().contains("does not prove"));
  }

  @Test
  void jsonKeepsAmountsAsTheExactTextFromTheFile() throws IOException {
    JsonNode root = new ObjectMapper().readTree(JsonReport.render(clean()));
    JsonNode line = root.at("/invoices/0/lines/0");
    assertTrue(line.get("taxable_value").isTextual(), "amounts are strings, never floats");
    assertEquals("1000.00", line.get("taxable_value").asText());
    assertEquals("1180.00", root.at("/invoices/0/grand_total").asText());
  }

  // --- Issues CSV ---

  @Test
  void issuesCsvHasAHeaderAndOneRowPerIssue() throws IOException {
    String csv = IssuesCsvReport.render(failing());
    assertFalse(csv.contains("\r"));
    List<CSVRecord> records =
        CSVFormat.DEFAULT
            .builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .get()
            .parse(new StringReader(csv))
            .getRecords();
    assertEquals(1, records.size());
    assertEquals("2", records.get(0).get("line_no"));
    assertEquals("HSN-002", records.get(0).get("check_id"));
    assertEquals("error", records.get(0).get("severity"));
    assertEquals("hsn_or_sac", records.get(0).get("field"));
    assertEquals("12", records.get(0).get("value"));
    assertFalse(records.get(0).get("suggested_fix").isBlank());
  }

  @Test
  void issuesCsvForACleanRunIsJustTheHeader() {
    assertEquals(
        "line_no,invoice_no,supplier_gstin,check_id,severity,field,value,message,suggested_fix\n",
        IssuesCsvReport.render(clean()));
  }

  @Test
  void issuesCsvNeutralisesSpreadsheetFormulas() {
    assertEquals("'=1+1", IssuesCsvReport.safe("=1+1"));
    assertEquals("'@SUM(A1)", IssuesCsvReport.safe("@SUM(A1)"));
    assertEquals("'+cmd|' /C calc'!A0", IssuesCsvReport.safe("+cmd|' /C calc'!A0"));
    assertEquals("'-2+3", IssuesCsvReport.safe("-2+3"));
    assertEquals("'\tx", IssuesCsvReport.safe("\tx"));
  }

  @Test
  void issuesCsvLeavesOrdinaryValuesAndSignedNumbersAlone() {
    assertEquals("-5.00", IssuesCsvReport.safe("-5.00"));
    assertEquals("+12", IssuesCsvReport.safe("+12"));
    assertEquals("INV-001", IssuesCsvReport.safe("INV-001"));
    assertEquals("a=b", IssuesCsvReport.safe("a=b"));
    assertEquals("", IssuesCsvReport.safe(""));
    assertEquals("", IssuesCsvReport.safe(null));
  }

  // --- Console ---

  @Test
  void consoleSaysPassedForACleanRun() {
    String text = ConsoleReport.render(clean());
    assertTrue(text.contains("Result: PASSED - no errors"));
    assertTrue(text.contains("No issues found."));
    assertFalse(text.contains("\r"));
    // The notes are word-wrapped, so compare with line breaks folded back to spaces.
    String unwrapped = text.replace("\n", " ");
    assertTrue(unwrapped.contains("does not prove the GSTIN is registered or active"));
    assertTrue(unwrapped.contains("not tax advice"));
  }

  @Test
  void consoleWrapsLongLinesInsteadOfCuttingThemOff() {
    String text = ConsoleReport.render(build("in.csv", validLine().invoiceNo("A_B#C D.E").build()));
    for (String line : text.split("\n")) {
      if (!line.startsWith("+") && !line.startsWith("|")) {
        assertTrue(line.length() <= 96, "line too long: " + line);
      }
    }
    assertFalse(text.contains("..."), "messages are wrapped, not truncated");
  }

  @Test
  void consoleSaysFailedAndListsTheIssue() {
    String text = ConsoleReport.render(failing());
    assertTrue(text.contains("Result: FAILED - 1 error(s)"));
    assertTrue(text.contains("row 2  HSN-002  error  INV/26-27/001"));
    assertTrue(text.contains("All 1 issue(s):"));
  }

  @Test
  void consoleLimitsTheIssueListAndSaysSo() {
    InvoiceLine[] lines = new InvoiceLine[5];
    for (int i = 0; i < lines.length; i++) {
      lines[i] = validLine().lineNo(i + 2).invoiceNo("A/" + i).hsnOrSac("12").build();
    }
    String text = ConsoleReport.render(build("in.csv", lines), 2);
    assertTrue(text.contains("First 2 of 5 issues"));
    assertEquals(2, count(text, "  HSN-002  error  "));
    assertTrue(text.contains("Result: FAILED - 5 error(s)"));
  }

  @Test
  void consoleReplacesControlCharactersFromTheInput() {
    String text =
        ConsoleReport.render(build("in.csv", validLine().invoiceNo("A\u001B[31mB").build()));
    assertFalse(
        text.contains("\u001B"), "escape sequences from the file must not reach the terminal");
  }

  @Test
  void consoleTablesAreAligned() {
    String table =
        ConsoleReport.table(
            List.of("Name", "N"),
            List.of(List.of("a", "1"), List.of("longer", "100")),
            List.of(false, true));
    assertEquals(
        """
        +--------+-----+
        | Name   | N   |
        +--------+-----+
        | a      |   1 |
        | longer | 100 |
        +--------+-----+
        """,
        table);
  }

  private static int count(String text, String needle) {
    int count = 0;
    for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + 1)) {
      count++;
    }
    return count;
  }
}
