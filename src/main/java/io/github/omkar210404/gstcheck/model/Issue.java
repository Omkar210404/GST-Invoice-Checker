package io.github.omkar210404.gstcheck.model;

import java.util.Objects;

/**
 * One problem found on one CSV row.
 *
 * @param lineNo the row as a spreadsheet shows it (the header is row 1)
 * @param invoiceNo the invoice number on that row, possibly empty
 * @param supplierGstin the supplier GSTIN on that row, possibly empty
 * @param check which check raised it
 * @param severity the effective severity, after any override from the config
 * @param field the canonical column the issue is about
 * @param value the offending value as it appeared in the file
 * @param message what is wrong, in plain English
 * @param suggestedFix what to do about it
 */
public record Issue(
    int lineNo,
    String invoiceNo,
    String supplierGstin,
    CheckId check,
    Severity severity,
    String field,
    String value,
    String message,
    String suggestedFix) {

  public Issue {
    Objects.requireNonNull(check, "check");
    Objects.requireNonNull(severity, "severity");
    invoiceNo = invoiceNo == null ? "" : invoiceNo;
    supplierGstin = supplierGstin == null ? "" : supplierGstin;
    field = field == null ? "" : field;
    value = value == null ? "" : value;
    message = message == null ? "" : message;
    suggestedFix = suggestedFix == null ? "" : suggestedFix;
  }

  /** Creates an issue for a row with the check's default severity. */
  public static Issue of(
      InvoiceLine line,
      CheckId check,
      Column column,
      String value,
      String message,
      String suggestedFix) {
    return new Issue(
        line.lineNo(),
        line.invoiceNo(),
        line.supplierGstinNormalized(),
        check,
        check.defaultSeverity(),
        column.key(),
        value,
        message,
        suggestedFix);
  }

  public Issue withSeverity(Severity newSeverity) {
    return new Issue(
        lineNo, invoiceNo, supplierGstin, check, newSeverity, field, value, message, suggestedFix);
  }

  public boolean isError() {
    return severity == Severity.ERROR;
  }
}
