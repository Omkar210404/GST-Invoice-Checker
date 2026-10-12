package io.github.omkar210404.gstcheck.csvio;

import io.github.omkar210404.gstcheck.config.CheckerConfig;
import io.github.omkar210404.gstcheck.config.DateParser;
import io.github.omkar210404.gstcheck.model.Column;
import io.github.omkar210404.gstcheck.model.InputProblemException;
import io.github.omkar210404.gstcheck.model.InvoiceLine;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 * Reads the invoice CSV into {@link InvoiceLine}s.
 *
 * <p>The reader is deliberately forgiving about values (a bad number becomes an invalid {@code
 * Amount} that a rule reports) and strict about structure (a missing required column stops the
 * run). Every cell is read as text, so leading zeros in codes survive.
 */
public final class InvoiceCsvReader {

  private static final char BOM = '﻿';

  private final CheckerConfig config;
  private final DateParser dateParser;

  public InvoiceCsvReader(CheckerConfig config) {
    this.config = config;
    this.dateParser = config.dateParser();
  }

  /**
   * Reads a UTF-8 CSV file.
   *
   * @throws InputProblemException if the file is missing, unreadable, empty or lacks a column
   */
  public List<InvoiceLine> read(Path file) {
    try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      return read(reader, file.toString());
    } catch (NoSuchFileException e) {
      throw new InputProblemException("Input file not found: " + file, e);
    } catch (IOException e) {
      throw new InputProblemException("Could not read " + file + ": " + e.getMessage(), e);
    }
  }

  /**
   * Reads CSV text from a reader.
   *
   * @param source a name for error messages, usually the file path
   */
  public List<InvoiceLine> read(Reader source, String name) {
    CSVFormat format = CSVFormat.DEFAULT.builder().setIgnoreEmptyLines(false).get();
    try (CSVParser parser = format.parse(skipBom(source))) {
      Map<Column, Integer> positions = null;
      List<InvoiceLine> lines = new ArrayList<>();
      for (CSVRecord record : parser) {
        if (isBlank(record)) {
          continue;
        }
        if (positions == null) {
          positions = locateColumns(record, name);
          continue;
        }
        // Record numbers count every record including the header, which matches the row number
        // a spreadsheet shows.
        lines.add(toLine(record, positions, Math.toIntExact(record.getRecordNumber())));
      }
      if (positions == null) {
        throw new InputProblemException("Input " + name + " is empty: no header row found.");
      }
      return lines;
    } catch (UncheckedIOException e) {
      throw new InputProblemException(
          "Input " + name + " is not valid CSV: " + e.getCause().getMessage(), e);
    } catch (IOException e) {
      throw new InputProblemException("Could not read " + name + ": " + e.getMessage(), e);
    }
  }

  private static Reader skipBom(Reader source) throws IOException {
    PushbackReader reader = new PushbackReader(source, 1);
    int first = reader.read();
    if (first != -1 && first != BOM) {
      reader.unread(first);
    }
    return reader;
  }

  private static boolean isBlank(CSVRecord record) {
    for (String value : record) {
      if (value != null && !value.isBlank()) {
        return false;
      }
    }
    return true;
  }

  private Map<Column, Integer> locateColumns(CSVRecord header, String name) {
    Map<Column, Integer> positions = new EnumMap<>(Column.class);
    List<String> missing = new ArrayList<>();
    for (Column column : Column.values()) {
      String wanted = config.headerFor(column);
      int found = -1;
      for (int i = 0; i < header.size(); i++) {
        if (normalizeHeader(header.get(i)).equals(normalizeHeader(wanted))) {
          if (found >= 0) {
            throw new InputProblemException(
                "Input " + name + " has the column '" + wanted + "' more than once.");
          }
          found = i;
        }
      }
      if (found >= 0) {
        positions.put(column, found);
      } else if (column.required()) {
        missing.add(wanted);
      }
    }
    if (!missing.isEmpty()) {
      throw new InputProblemException(
          "Input "
              + name
              + " is missing required column(s): "
              + String.join(", ", missing)
              + ". If your file uses different headers, map them under 'columns' in the config.");
    }
    return positions;
  }

  private static String normalizeHeader(String header) {
    return header == null ? "" : header.strip().toLowerCase(Locale.ROOT);
  }

  private InvoiceLine toLine(CSVRecord record, Map<Column, Integer> positions, int lineNo) {
    String dateText = cell(record, positions, Column.INVOICE_DATE).strip();
    return InvoiceLine.builder()
        .lineNo(lineNo)
        .invoiceNo(cell(record, positions, Column.INVOICE_NO).strip())
        .invoiceDateText(dateText)
        .invoiceDate(dateParser.parse(dateText).orElse(null))
        // GSTINs are kept exactly as typed so GSTIN-007 can report stray spaces and lower case.
        .supplierGstin(cell(record, positions, Column.SUPPLIER_GSTIN))
        .buyerGstin(cell(record, positions, Column.BUYER_GSTIN))
        .placeOfSupply(cell(record, positions, Column.PLACE_OF_SUPPLY).strip())
        .hsnOrSac(cell(record, positions, Column.HSN_OR_SAC).strip())
        .description(cell(record, positions, Column.DESCRIPTION).strip())
        .quantity(cell(record, positions, Column.QUANTITY))
        .unit(cell(record, positions, Column.UNIT).strip())
        .unitPrice(cell(record, positions, Column.UNIT_PRICE))
        .taxableValue(cell(record, positions, Column.TAXABLE_VALUE))
        .rate(cell(record, positions, Column.RATE))
        .cgst(cell(record, positions, Column.CGST))
        .sgst(cell(record, positions, Column.SGST))
        .igst(cell(record, positions, Column.IGST))
        .cess(cell(record, positions, Column.CESS))
        .lineTotal(cell(record, positions, Column.LINE_TOTAL))
        .supplierName(cell(record, positions, Column.SUPPLIER_NAME).strip())
        .buyerName(cell(record, positions, Column.BUYER_NAME).strip())
        .build();
  }

  /** A cell's text, or empty when the column is absent or the row is short. */
  private static String cell(CSVRecord record, Map<Column, Integer> positions, Column column) {
    Integer index = positions.get(column);
    if (index == null || index >= record.size()) {
      return "";
    }
    String value = record.get(index);
    return value == null ? "" : value;
  }
}
