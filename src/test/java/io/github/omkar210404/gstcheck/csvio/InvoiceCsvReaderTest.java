package io.github.omkar210404.gstcheck.csvio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.omkar210404.gstcheck.config.CheckerConfig;
import io.github.omkar210404.gstcheck.model.InputProblemException;
import io.github.omkar210404.gstcheck.model.InvoiceLine;
import io.github.omkar210404.gstcheck.testutil.Fixtures;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InvoiceCsvReaderTest {

  private static final String HEADER =
      "invoice_no,invoice_date,supplier_gstin,buyer_gstin,place_of_supply_state_code,hsn_or_sac,"
          + "description,quantity,unit,unit_price,taxable_value,rate,cgst,sgst,igst,cess,line_total";

  private static final String ROW =
      "INV/1,2026-07-15,"
          + Fixtures.SUPPLIER_27
          + ","
          + Fixtures.BUYER_27
          + ",27,123456,Item,2,NOS,500.00,1000.00,18,90.00,90.00,0.00,0.00,1180.00";

  private static List<InvoiceLine> read(String csv) {
    return read(csv, Fixtures.config());
  }

  private static List<InvoiceLine> read(String csv, CheckerConfig config) {
    return new InvoiceCsvReader(config).read(new StringReader(csv), "test.csv");
  }

  private static String messageOf(String csv) {
    return assertThrows(InputProblemException.class, () -> read(csv)).getMessage();
  }

  @Test
  void readsARowIntoTypedFields() {
    InvoiceLine line = read(HEADER + "\n" + ROW + "\n").get(0);
    assertEquals(2, line.lineNo(), "the header is row 1");
    assertEquals("INV/1", line.invoiceNo());
    assertEquals(LocalDate.of(2026, 7, 15), line.invoiceDate());
    assertEquals(Fixtures.SUPPLIER_27, line.supplierGstin());
    assertEquals("27", line.placeOfSupply());
    assertEquals(new BigDecimal("1000.00"), line.taxableValue().value());
    assertEquals(new BigDecimal("18"), line.rate().value());
    assertEquals(new BigDecimal("1180.00"), line.lineTotal().value());
    assertEquals("NOS", line.unit());
  }

  @Test
  void codesAreReadAsTextSoLeadingZerosSurvive() {
    String row = ROW.replace(",27,123456,", ",07,012345,");
    InvoiceLine line = read(HEADER + "\n" + row).get(0);
    assertEquals("07", line.placeOfSupply());
    assertEquals("012345", line.hsnOrSac());
  }

  @Test
  void aUtf8ByteOrderMarkIsIgnored() {
    assertEquals(1, read("﻿" + HEADER + "\n" + ROW).size());
  }

  @Test
  void headersMatchIgnoringCaseAndSpaces() {
    String header = HEADER.replace("invoice_no", " Invoice_No ").replace("rate", "RATE");
    assertEquals("INV/1", read(header + "\n" + ROW).get(0).invoiceNo());
  }

  @Test
  void columnOrderDoesNotMatterAndExtraColumnsAreIgnored() {
    String csv =
        "notes,line_total,rate,taxable_value,hsn_or_sac,place_of_supply_state_code,supplier_gstin,"
            + "invoice_date,invoice_no\n"
            + "ignore me,118.00,18,100.00,1234,27,"
            + Fixtures.SUPPLIER_27
            + ",2026-07-15,INV/9\n";
    InvoiceLine line = read(csv).get(0);
    assertEquals("INV/9", line.invoiceNo());
    assertEquals(new BigDecimal("118.00"), line.lineTotal().value());
    assertTrue(line.cgst().isBlank(), "an absent optional column reads as blank");
    assertEquals("", line.buyerGstin());
  }

  @Test
  void blankLinesAreSkippedButRowNumbersStillMatchTheFile() {
    String csv = HEADER + "\n" + ROW + "\n\n,,,\n" + ROW.replace("INV/1", "INV/2") + "\n";
    List<InvoiceLine> lines = read(csv);
    assertEquals(2, lines.size());
    assertEquals(2, lines.get(0).lineNo());
    assertEquals(5, lines.get(1).lineNo());
  }

  @Test
  void quotedCellsMayContainCommasQuotesAndNewlines() {
    String row = ROW.replace(",Item,", ",\"Bolts, \"\"M8\"\"\nbox of 10\",");
    InvoiceLine line = read(HEADER + "\n" + row).get(0);
    assertEquals("Bolts, \"M8\"\nbox of 10", line.description());
    assertEquals(new BigDecimal("1180.00"), line.lineTotal().value());
  }

  @Test
  void shortRowsReadMissingCellsAsBlank() {
    InvoiceLine line = read(HEADER + "\nINV/1,2026-07-15\n").get(0);
    assertEquals("INV/1", line.invoiceNo());
    assertEquals("", line.supplierGstin());
    assertTrue(line.taxableValue().isBlank());
  }

  @Test
  void badValuesAreKeptForTheRulesToReport() {
    String row = ROW.replace("2026-07-15", "15 July").replace("1000.00", "\"1,000.00\"");
    InvoiceLine line = read(HEADER + "\n" + row).get(0);
    assertNull(line.invoiceDate());
    assertEquals("15 July", line.invoiceDateText());
    assertTrue(line.taxableValue().isInvalid());
    assertEquals("1,000.00", line.taxableValue().text());
  }

  @Test
  void gstinsKeepTheirOriginalSpacingAndCase() {
    String row = ROW.replace(Fixtures.SUPPLIER_27, " " + Fixtures.SUPPLIER_27.toLowerCase() + " ");
    InvoiceLine line = read(HEADER + "\n" + row).get(0);
    assertEquals(" " + Fixtures.SUPPLIER_27.toLowerCase() + " ", line.supplierGstin());
    assertEquals(Fixtures.SUPPLIER_27, line.supplierGstinNormalized());
  }

  @Test
  void aMissingRequiredColumnStopsTheRunAndNamesTheColumn() {
    String message = messageOf(HEADER.replace(",line_total", "") + "\n");
    assertTrue(message.contains("line_total"));
    assertTrue(message.contains("columns"), "points at the column mapping in the config");
  }

  @Test
  void aRepeatedColumnIsRejected() {
    assertTrue(messageOf(HEADER + ",rate\n" + ROW + ",18\n").contains("more than once"));
  }

  @Test
  void anEmptyFileIsRejected() {
    assertTrue(messageOf("").contains("empty"));
    assertTrue(messageOf("\n\n").contains("empty"));
  }

  @Test
  void aHeaderWithNoRowsGivesNoLines() {
    assertTrue(read(HEADER + "\n").isEmpty());
  }

  @Test
  void anUnterminatedQuoteIsAnInputProblemNotACrash() {
    assertThrows(InputProblemException.class, () -> read(HEADER + "\n\"INV/1,2026-07-15\n"));
  }

  @Test
  void columnMappingFromTheConfigIsHonoured() {
    CheckerConfig config =
        Fixtures.configWith(
            "columns: {}", "columns:\n  invoice_no: \"Bill No\"\n  rate: \"GST %\"");
    String header = HEADER.replace("invoice_no", "Bill No").replace(",rate,", ",GST %,");
    InvoiceLine line = read(header + "\n" + ROW, config).get(0);
    assertEquals("INV/1", line.invoiceNo());
    assertEquals(new BigDecimal("18"), line.rate().value());

    String message =
        assertThrows(InputProblemException.class, () -> read(HEADER + "\n" + ROW, config))
            .getMessage();
    assertTrue(message.contains("Bill No"), "the error names the header the config asked for");
  }

  @Test
  void readsFromAFileAndReportsAMissingOne(@TempDir Path dir) throws IOException {
    Path file = dir.resolve("invoices.csv");
    Files.writeString(file, HEADER + "\n" + ROW + "\n", StandardCharsets.UTF_8);
    assertEquals(1, new InvoiceCsvReader(Fixtures.config()).read(file).size());

    String message =
        assertThrows(
                InputProblemException.class,
                () -> new InvoiceCsvReader(Fixtures.config()).read(dir.resolve("nope.csv")))
            .getMessage();
    assertTrue(message.contains("not found"));
  }
}
