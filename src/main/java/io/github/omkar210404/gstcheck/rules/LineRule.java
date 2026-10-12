package io.github.omkar210404.gstcheck.rules;

import io.github.omkar210404.gstcheck.model.InvoiceLine;
import io.github.omkar210404.gstcheck.model.Issue;
import java.util.ArrayList;
import java.util.List;

/** A rule that looks at one line at a time. Most rules are of this kind. */
public interface LineRule extends Rule {

  /** Adds any issues found on this line to {@code issues}. */
  void check(InvoiceLine line, RuleContext context, List<Issue> issues);

  @Override
  default List<Issue> apply(List<InvoiceLine> lines, RuleContext context) {
    List<Issue> issues = new ArrayList<>();
    for (InvoiceLine line : lines) {
      check(line, context, issues);
    }
    return issues;
  }
}
