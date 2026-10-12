package io.github.omkar210404.gstcheck.rules;

import io.github.omkar210404.gstcheck.model.CheckId;
import io.github.omkar210404.gstcheck.model.Column;
import io.github.omkar210404.gstcheck.model.InvoiceLine;
import io.github.omkar210404.gstcheck.model.Issue;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * MATH-003 (warning): when both quantity and unit price are given, taxable value should equal
 * quantity x unit price within the tolerance. It is only a warning because the input has no
 * discount column, and a discount is a legitimate reason for the two to differ.
 */
public final class QuantityPriceRule implements LineRule {

  @Override
  public List<CheckId> checks() {
    return List.of(CheckId.MATH_003);
  }

  @Override
  public void check(InvoiceLine line, RuleContext context, List<Issue> issues) {
    if (!line.quantity().isPresent()
        || !line.unitPrice().isPresent()
        || !line.taxableValue().isPresent()) {
      return;
    }
    BigDecimal expected = line.quantity().value().multiply(line.unitPrice().value());
    BigDecimal difference = line.taxableValue().value().subtract(expected).abs();
    BigDecimal tolerance = context.config().tolerance().quantityPrice();
    if (difference.compareTo(tolerance) > 0) {
      issues.add(
          Issue.of(
              line,
              CheckId.MATH_003,
              Column.TAXABLE_VALUE,
              line.taxableValue().text(),
              "Taxable value is "
                  + line.taxableValue().text()
                  + " but "
                  + line.quantity().text()
                  + " x "
                  + line.unitPrice().text()
                  + " is "
                  + expected
                      .setScale(Math.max(2, expected.scale()), RoundingMode.UNNECESSARY)
                      .toPlainString()
                  + ".",
              "Check quantity, unit price and taxable value. If the difference is a discount,"
                  + " this warning can be ignored or MATH-003 disabled in the config."));
    }
  }
}
