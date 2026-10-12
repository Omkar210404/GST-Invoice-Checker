package io.github.omkar210404.gstcheck.gstin;

import io.github.omkar210404.gstcheck.model.CheckId;
import io.github.omkar210404.gstcheck.model.Severity;
import java.util.List;

/**
 * The outcome of checking one GSTIN.
 *
 * <p>"Structurally valid" means the format and check character are right. It does not mean the
 * number is registered or active; only the GST portal can say that.
 *
 * @param input the text exactly as given
 * @param normalized the text trimmed and upper-cased, which is what was checked
 * @param problems everything found, in check ID order; empty when nothing is wrong
 */
public record GstinResult(String input, String normalized, List<GstinProblem> problems) {

  /** The caveat that must accompany any "valid" verdict. */
  public static final String STRUCTURAL_ONLY_NOTICE =
      "Structurally valid only: the format and check character are correct. This does not prove"
          + " the GSTIN is registered or active. Confirm it on the official GST portal.";

  public GstinResult {
    input = input == null ? "" : input;
    normalized = normalized == null ? "" : normalized;
    problems = List.copyOf(problems);
  }

  /** True when there are no error-level problems. A lower-case warning alone still passes. */
  public boolean structurallyValid() {
    return problems.stream().noneMatch(p -> p.check().defaultSeverity() == Severity.ERROR);
  }

  public boolean has(CheckId check) {
    return problems.stream().anyMatch(p -> p.check() == check);
  }
}
