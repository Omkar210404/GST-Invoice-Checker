package io.github.omkar210404.gstcheck.rules;

import io.github.omkar210404.gstcheck.model.CheckId;
import io.github.omkar210404.gstcheck.model.Column;
import io.github.omkar210404.gstcheck.model.InvoiceLine;
import io.github.omkar210404.gstcheck.model.Issue;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * MATH-001: the tax charged (CGST + SGST + IGST) must equal taxable value x rate, within the
 * configured tolerance. A difference exactly equal to the tolerance passes.
 *
 * <p>The expected tax is computed exactly with {@link BigDecimal}; nothing is rounded before the
 * comparison, so the tolerance is the only slack.
 */
public final class TaxAmountRule implements LineRule {

  @Override
  public List<CheckId> checks() {
    return List.of(CheckId.MATH_001);
  }

  @Override
  public void check(InvoiceLine line, RuleContext context, List<Issue> issues) {
    if (!line.taxableValue().isPresent() || !line.rate().isPresent()) {
      return;
    }
    if (line.cgst().isInvalid() || line.sgst().isInvalid() || line.igst().isInvalid()) {
      return;
    }
    // Negative inputs are reported by MATH-004; comparing them here would only add noise.
    if (line.taxableValue().isNegative() || line.rate().isNegative()) {
      return;
    }
    BigDecimal expected =
        line.taxableValue().value().multiply(line.rate().value()).movePointLeft(2);
    BigDecimal actual = line.cgst().orZero().add(line.sgst().orZero()).add(line.igst().orZero());
    BigDecimal difference = actual.subtract(expected).abs();
    BigDecimal tolerance = context.config().tolerance().taxAmount();
    if (difference.compareTo(tolerance) > 0) {
      String expectedText = expected.setScale(2, RoundingMode.HALF_UP).toPlainString();
      issues.add(
          Issue.of(
              line,
              CheckId.MATH_001,
              Column.RATE,
              actual.toPlainString(),
              "Tax charged is "
                  + actual.toPlainString()
                  + " but "
                  + line.taxableValue().text()
                  + " x "
                  + line.rate().text()
                  + "% is "
                  + expectedText
                  + " (difference "
                  + Decimals.show(difference)
                  + ", tolerance "
                  + tolerance.toPlainString()
                  + ").",
              "Recalculate the tax, or correct the rate or taxable value. If your invoices round"
                  + " tax differently, raise tolerance.tax_amount in the config."));
    }
  }
}
