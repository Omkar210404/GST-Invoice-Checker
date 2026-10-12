package io.github.omkar210404.gstcheck.rules;

import io.github.omkar210404.gstcheck.config.FinancialYear;
import io.github.omkar210404.gstcheck.model.CheckId;
import io.github.omkar210404.gstcheck.model.Column;
import io.github.omkar210404.gstcheck.model.InvoiceLine;
import io.github.omkar210404.gstcheck.model.Issue;
import java.time.LocalDate;
import java.util.List;

/**
 * DATE-001 to DATE-003: the invoice date must be a real date, not after the as-of date, and inside
 * the financial year from the config.
 */
public final class InvoiceDateRule implements LineRule {

  @Override
  public List<CheckId> checks() {
    return List.of(CheckId.DATE_001, CheckId.DATE_002, CheckId.DATE_003);
  }

  @Override
  public void check(InvoiceLine line, RuleContext context, List<Issue> issues) {
    String text = line.invoiceDateText();
    if (text.isBlank()) {
      return;
    }
    LocalDate date = line.invoiceDate();
    if (date == null) {
      issues.add(
          Issue.of(
              line,
              CheckId.DATE_001,
              Column.INVOICE_DATE,
              text,
              "'"
                  + text
                  + "' is not a valid date in any accepted format ("
                  + String.join(", ", context.config().dateFormats())
                  + ").",
              "Correct the date, or add the format your file uses to date_formats in the config."
                  + " Dates that do not exist, such as 30 February, are rejected."));
      return;
    }
    if (date.isAfter(context.asOf())) {
      issues.add(
          Issue.of(
              line,
              CheckId.DATE_002,
              Column.INVOICE_DATE,
              text,
              "The invoice date " + date + " is after " + context.asOf() + ".",
              "Check the year and whether day and month are swapped."));
    }
    FinancialYear year = context.config().financialYear();
    if (!year.contains(date)) {
      issues.add(
          Issue.of(
              line,
              CheckId.DATE_003,
              Column.INVOICE_DATE,
              text,
              "The invoice date "
                  + date
                  + " is outside financial year "
                  + year.label()
                  + " ("
                  + year.start()
                  + " to "
                  + year.end()
                  + ").",
              "Correct the date, or move this invoice to the file for its own financial year."));
    }
  }
}
