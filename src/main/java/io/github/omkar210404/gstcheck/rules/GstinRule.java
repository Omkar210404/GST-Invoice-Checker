package io.github.omkar210404.gstcheck.rules;

import io.github.omkar210404.gstcheck.gstin.GstinProblem;
import io.github.omkar210404.gstcheck.gstin.GstinValidator;
import io.github.omkar210404.gstcheck.model.CheckId;
import io.github.omkar210404.gstcheck.model.Column;
import io.github.omkar210404.gstcheck.model.InvoiceLine;
import io.github.omkar210404.gstcheck.model.Issue;
import java.util.ArrayList;
import java.util.List;

/**
 * GSTIN-001 to GSTIN-007 on the supplier and buyer GSTINs.
 *
 * <p>An empty buyer GSTIN is allowed and means an unregistered buyer. An empty supplier GSTIN is
 * reported by DATA-001, not here. Passing these checks means a GSTIN is structurally valid; it does
 * not show that the number is registered or active.
 */
public final class GstinRule implements Rule {

  @Override
  public List<CheckId> checks() {
    return List.of(
        CheckId.GSTIN_001,
        CheckId.GSTIN_002,
        CheckId.GSTIN_003,
        CheckId.GSTIN_004,
        CheckId.GSTIN_005,
        CheckId.GSTIN_006,
        CheckId.GSTIN_007);
  }

  @Override
  public List<Issue> apply(List<InvoiceLine> lines, RuleContext context) {
    GstinValidator validator = new GstinValidator(context.config().stateCodes());
    List<Issue> issues = new ArrayList<>();
    for (InvoiceLine line : lines) {
      check(validator, line, Column.SUPPLIER_GSTIN, line.supplierGstin(), issues);
      check(validator, line, Column.BUYER_GSTIN, line.buyerGstin(), issues);
    }
    return issues;
  }

  private static void check(
      GstinValidator validator, InvoiceLine line, Column column, String gstin, List<Issue> issues) {
    if (gstin.isBlank()) {
      return;
    }
    for (GstinProblem problem : validator.validate(gstin).problems()) {
      issues.add(
          Issue.of(
              line, problem.check(), column, gstin, problem.message(), problem.suggestedFix()));
    }
  }
}
