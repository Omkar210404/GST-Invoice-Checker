package io.github.omkar210404.gstcheck.rules;

import io.github.omkar210404.gstcheck.config.CheckerConfig;
import io.github.omkar210404.gstcheck.model.InvoiceLine;
import io.github.omkar210404.gstcheck.model.Issue;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Runs every rule over the lines, then applies the config: disabled checks are dropped and severity
 * overrides are applied. Issues come back sorted by row, then check ID, then column.
 */
public final class RuleEngine {

  private static final Comparator<Issue> ORDER =
      Comparator.comparingInt(Issue::lineNo)
          .thenComparing(issue -> issue.check().id())
          .thenComparing(Issue::field);

  private final List<Rule> rules;

  public RuleEngine(List<Rule> rules) {
    this.rules = List.copyOf(rules);
  }

  /**
   * The standard rule set. To add a rule, write a class implementing {@link Rule} (or {@link
   * LineRule}), add its check IDs to {@code CheckId}, and list it here.
   */
  public static RuleEngine standard() {
    return new RuleEngine(
        List.of(
            new DataQualityRule(),
            new GstinRule(),
            new InvoiceNumberRule(),
            new DuplicateInvoiceRule(),
            new InvoiceDateRule(),
            new HsnSacRule(),
            new PlaceOfSupplyRule(),
            new TaxTypeRule(),
            new TaxAmountRule(),
            new LineTotalRule(),
            new QuantityPriceRule(),
            new AmountSignRule(),
            new RateSlabRule()));
  }

  public List<Rule> rules() {
    return rules;
  }

  public List<Issue> run(List<InvoiceLine> lines, RuleContext context) {
    CheckerConfig config = context.config();
    List<Issue> issues = new ArrayList<>();
    for (Rule rule : rules) {
      for (Issue issue : rule.apply(lines, context)) {
        if (!config.isDisabled(issue.check())) {
          issues.add(issue.withSeverity(config.severityOf(issue.check())));
        }
      }
    }
    issues.sort(ORDER);
    return List.copyOf(issues);
  }
}
