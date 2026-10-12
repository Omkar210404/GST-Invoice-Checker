package io.github.omkar210404.gstcheck.rules;

import io.github.omkar210404.gstcheck.model.CheckId;
import io.github.omkar210404.gstcheck.model.InvoiceLine;
import io.github.omkar210404.gstcheck.model.Issue;
import java.util.List;

/**
 * One validation rule. A rule owns one or more check IDs and turns invoice lines into issues.
 *
 * <p>Rules are pure: no I/O, no clock access except through the context, and they never throw on
 * bad data. When a value a rule depends on is missing or unparseable, the rule stays silent and
 * leaves the report to the DATA checks, so one bad cell does not produce a pile of issues.
 */
public interface Rule {

  /** The checks this rule can raise. */
  List<CheckId> checks();

  /** Returns the issues found. Issues carry each check's default severity. */
  List<Issue> apply(List<InvoiceLine> lines, RuleContext context);
}
