package io.github.omkar210404.gstcheck.rules;

import io.github.omkar210404.gstcheck.config.FinancialYear;
import io.github.omkar210404.gstcheck.model.CheckId;
import io.github.omkar210404.gstcheck.model.Column;
import io.github.omkar210404.gstcheck.model.InvoiceLine;
import io.github.omkar210404.gstcheck.model.Issue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * INV-003 and INV-004: invoice numbers must be unique per supplier within a financial year.
 *
 * <p>The input has one row per invoice <em>line</em>, so the same number appearing on several rows
 * is normal. A repeat is treated as a duplicate only when the rows disagree on the invoice date,
 * the buyer or the place of supply, which a single invoice cannot do. Two separate invoices that
 * share a number and all three of those details cannot be told apart from one multi-line invoice.
 */
public final class DuplicateInvoiceRule implements Rule {

  @Override
  public List<CheckId> checks() {
    return List.of(CheckId.INV_003, CheckId.INV_004);
  }

  /** Supplier, financial year and invoice number: the scope a number must be unique in. */
  private record Key(String supplier, FinancialYear year, String invoiceNo) {}

  private record Scope(String supplier, FinancialYear year) {}

  @Override
  public List<Issue> apply(List<InvoiceLine> lines, RuleContext context) {
    List<Issue> issues = new ArrayList<>();
    Map<Key, InvoiceLine> firstSeen = new LinkedHashMap<>();
    // Per supplier and year: lower-cased number -> the first line that used each exact spelling.
    Map<Scope, Map<String, Map<String, InvoiceLine>>> spellings = new LinkedHashMap<>();

    for (InvoiceLine line : lines) {
      String supplier = line.supplierGstinNormalized();
      if (line.invoiceNo().isEmpty() || supplier.isEmpty() || line.invoiceDate() == null) {
        continue;
      }
      FinancialYear year = FinancialYear.containing(line.invoiceDate());
      Key key = new Key(supplier, year, line.invoiceNo());

      InvoiceLine first = firstSeen.putIfAbsent(key, line);
      if (first != null) {
        String difference = difference(first, line);
        if (difference != null) {
          issues.add(
              Issue.of(
                  line,
                  CheckId.INV_003,
                  Column.INVOICE_NO,
                  line.invoiceNo(),
                  "Invoice number "
                      + line.invoiceNo()
                      + " was already used by this supplier in "
                      + year.label()
                      + " on row "
                      + first.lineNo()
                      + " with a different "
                      + difference
                      + ".",
                  "If these are two invoices, give one a new number. If they are lines of the"
                      + " same invoice, make the date, buyer GSTIN and place of supply match."));
        }
        continue;
      }

      Map<String, InvoiceLine> sameIgnoringCase =
          spellings
              .computeIfAbsent(new Scope(supplier, year), s -> new LinkedHashMap<>())
              .computeIfAbsent(
                  line.invoiceNo().toLowerCase(Locale.ROOT), n -> new LinkedHashMap<>());
      if (!sameIgnoringCase.isEmpty()) {
        InvoiceLine earlier = sameIgnoringCase.values().iterator().next();
        issues.add(
            Issue.of(
                line,
                CheckId.INV_004,
                Column.INVOICE_NO,
                line.invoiceNo(),
                "Invoice number "
                    + line.invoiceNo()
                    + " differs only by letter case from "
                    + earlier.invoiceNo()
                    + " on row "
                    + earlier.lineNo()
                    + " for the same supplier and year.",
                "Check whether these are the same invoice typed two ways. Use one consistent"
                    + " spelling."));
      }
      sameIgnoringCase.put(line.invoiceNo(), line);
    }
    return issues;
  }

  /** Names the first invoice-level detail that differs, or null when the rows agree. */
  private static String difference(InvoiceLine first, InvoiceLine other) {
    if (!first.invoiceDate().equals(other.invoiceDate())) {
      return "invoice date (" + first.invoiceDateText() + " vs " + other.invoiceDateText() + ")";
    }
    if (!first.buyerGstinNormalized().equals(other.buyerGstinNormalized())) {
      return "buyer GSTIN";
    }
    if (!first.placeOfSupply().equals(other.placeOfSupply())) {
      return "place of supply (" + first.placeOfSupply() + " vs " + other.placeOfSupply() + ")";
    }
    return null;
  }
}
