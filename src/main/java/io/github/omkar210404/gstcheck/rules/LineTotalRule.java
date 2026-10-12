package io.github.omkar210404.gstcheck.rules;

import io.github.omkar210404.gstcheck.model.CheckId;
import io.github.omkar210404.gstcheck.model.Column;
import io.github.omkar210404.gstcheck.model.InvoiceLine;
import io.github.omkar210404.gstcheck.model.Issue;
import java.math.BigDecimal;
import java.util.List;

/**
 * MATH-002: the line total must equal taxable value + CGST + SGST + IGST + cess, within the
 * configured tolerance. A difference exactly equal to the tolerance passes.
 */
public final class LineTotalRule implements LineRule {

  @Override
  public List<CheckId> checks() {
    return List.of(CheckId.MATH_002);
  }

  @Override
  public void check(InvoiceLine line, RuleContext context, List<Issue> issues) {
    if (!line.taxableValue().isPresent() || !line.lineTotal().isPresent()) {
      return;
    }
    if (line.cgst().isInvalid()
        || line.sgst().isInvalid()
        || line.igst().isInvalid()
        || line.cess().isInvalid()) {
      return;
    }
    BigDecimal expected =
        line.taxableValue()
            .value()
            .add(line.cgst().orZero())
            .add(line.sgst().orZero())
            .add(line.igst().orZero())
            .add(line.cess().orZero());
    BigDecimal actual = line.lineTotal().value();
    BigDecimal difference = actual.subtract(expected).abs();
    BigDecimal tolerance = context.config().tolerance().lineTotal();
    if (difference.compareTo(tolerance) > 0) {
      issues.add(
          Issue.of(
              line,
              CheckId.MATH_002,
              Column.LINE_TOTAL,
              line.lineTotal().text(),
              "Line total is "
                  + line.lineTotal().text()
                  + " but taxable value + taxes + cess is "
                  + expected.toPlainString()
                  + " (difference "
                  + Decimals.show(difference)
                  + ", tolerance "
                  + tolerance.toPlainString()
                  + ").",
              "Recalculate the line total as taxable_value + cgst + sgst + igst + cess."));
    }
  }
}
