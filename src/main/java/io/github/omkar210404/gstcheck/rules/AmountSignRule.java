package io.github.omkar210404.gstcheck.rules;

import io.github.omkar210404.gstcheck.model.Amount;
import io.github.omkar210404.gstcheck.model.CheckId;
import io.github.omkar210404.gstcheck.model.Column;
import io.github.omkar210404.gstcheck.model.InvoiceLine;
import io.github.omkar210404.gstcheck.model.Issue;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MATH-004 (error) for any negative number, and MATH-005 (warning) for a zero taxable value,
 * quantity or line total. Zero tax columns and a zero rate are normal and are not flagged.
 */
public final class AmountSignRule implements LineRule {

  @Override
  public List<CheckId> checks() {
    return List.of(CheckId.MATH_004, CheckId.MATH_005);
  }

  @Override
  public void check(InvoiceLine line, RuleContext context, List<Issue> issues) {
    Map<Column, Amount> all = new LinkedHashMap<>();
    all.put(Column.QUANTITY, line.quantity());
    all.put(Column.UNIT_PRICE, line.unitPrice());
    all.put(Column.TAXABLE_VALUE, line.taxableValue());
    all.put(Column.RATE, line.rate());
    all.put(Column.CGST, line.cgst());
    all.put(Column.SGST, line.sgst());
    all.put(Column.IGST, line.igst());
    all.put(Column.CESS, line.cess());
    all.put(Column.LINE_TOTAL, line.lineTotal());
    for (Map.Entry<Column, Amount> entry : all.entrySet()) {
      if (entry.getValue().isNegative()) {
        issues.add(
            Issue.of(
                line,
                CheckId.MATH_004,
                entry.getKey(),
                entry.getValue().text(),
                entry.getKey().key() + " is negative (" + entry.getValue().text() + ").",
                "Invoice lines should carry positive amounts. If this is a return or a credit"
                    + " note, keep it out of the invoice file."));
      }
    }

    Map<Column, Amount> shouldNotBeZero = new LinkedHashMap<>();
    shouldNotBeZero.put(Column.QUANTITY, line.quantity());
    shouldNotBeZero.put(Column.TAXABLE_VALUE, line.taxableValue());
    shouldNotBeZero.put(Column.LINE_TOTAL, line.lineTotal());
    for (Map.Entry<Column, Amount> entry : shouldNotBeZero.entrySet()) {
      if (entry.getValue().isZero()) {
        issues.add(
            Issue.of(
                line,
                CheckId.MATH_005,
                entry.getKey(),
                entry.getValue().text(),
                entry.getKey().key() + " is zero.",
                "Check whether this line was meant to be on the invoice, or whether a value is"
                    + " missing."));
      }
    }
  }
}
