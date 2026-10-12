package io.github.omkar210404.gstcheck.rules;

import io.github.omkar210404.gstcheck.model.CheckId;
import io.github.omkar210404.gstcheck.model.Column;
import io.github.omkar210404.gstcheck.model.InvoiceLine;
import io.github.omkar210404.gstcheck.model.Issue;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * RATE-001: the rate must be one of the slabs listed in the user's config.
 *
 * <p>The list of rates lives only in the config file. This class knows nothing about which GST
 * rates are in force, and neither does the rest of the code.
 */
public final class RateSlabRule implements LineRule {

  @Override
  public List<CheckId> checks() {
    return List.of(CheckId.RATE_001);
  }

  @Override
  public void check(InvoiceLine line, RuleContext context, List<Issue> issues) {
    if (!line.rate().isPresent() || line.rate().isNegative()) {
      return;
    }
    BigDecimal rate = line.rate().value();
    if (!context.config().isSlabAllowed(rate)) {
      String slabs =
          context.config().allowedSlabs().stream()
              .map(BigDecimal::toPlainString)
              .collect(Collectors.joining(", "));
      issues.add(
          Issue.of(
              line,
              CheckId.RATE_001,
              Column.RATE,
              line.rate().text(),
              "Rate "
                  + line.rate().text()
                  + "% is not in rates.allowed_slabs in the config ("
                  + slabs
                  + ").",
              "Correct the rate. If this rate does apply to the item, verify it against official"
                  + " GST notifications and add it to rates.allowed_slabs."));
    }
  }
}
