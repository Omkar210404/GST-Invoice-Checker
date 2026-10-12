package io.github.omkar210404.gstcheck.gstin;

import io.github.omkar210404.gstcheck.model.CheckId;
import java.util.Objects;

/**
 * One thing wrong with a GSTIN.
 *
 * @param check the check it maps to (GSTIN-001 to GSTIN-007)
 * @param message what is wrong
 * @param suggestedFix what to do about it
 */
public record GstinProblem(CheckId check, String message, String suggestedFix) {
  public GstinProblem {
    Objects.requireNonNull(check, "check");
    Objects.requireNonNull(message, "message");
    Objects.requireNonNull(suggestedFix, "suggestedFix");
  }
}
