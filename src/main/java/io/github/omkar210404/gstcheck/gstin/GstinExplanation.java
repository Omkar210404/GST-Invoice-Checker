package io.github.omkar210404.gstcheck.gstin;

import io.github.omkar210404.gstcheck.model.CheckId;
import io.github.omkar210404.gstcheck.model.Severity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a {@link GstinResult} into a step-by-step explanation: one line per GSTIN check saying
 * whether it passed, failed, only warned or could not be applied. The {@code gstin} command and the
 * local page both show this.
 */
public final class GstinExplanation {

  /** The outcome of one check for one GSTIN. */
  public enum Status {
    PASS,
    FAIL,
    WARN,
    /** Not applied, because the length or characters are wrong and positions mean nothing. */
    SKIP
  }

  /**
   * One line of the explanation.
   *
   * @param check the check
   * @param label what the check looks at, phrased neutrally
   * @param status the outcome
   * @param message what is wrong; empty unless the status is FAIL or WARN
   * @param suggestedFix what to do about it; empty unless the status is FAIL or WARN
   */
  public record Step(
      CheckId check, String label, Status status, String message, String suggestedFix) {}

  private static final Map<CheckId, String> LABELS = new LinkedHashMap<>();

  static {
    LABELS.put(CheckId.GSTIN_001, "15 characters, only A-Z and 0-9");
    LABELS.put(CheckId.GSTIN_002, "State code (characters 1-2)");
    LABELS.put(CheckId.GSTIN_003, "PAN-shaped part (characters 3-12)");
    LABELS.put(CheckId.GSTIN_004, "Entity number (character 13)");
    LABELS.put(CheckId.GSTIN_005, "Fixed character Z (character 14)");
    LABELS.put(CheckId.GSTIN_006, "Check character (character 15)");
    LABELS.put(CheckId.GSTIN_007, "Upper case with no spaces");
  }

  private GstinExplanation() {}

  /** One step per GSTIN check, in check ID order. */
  public static List<Step> steps(GstinResult result) {
    boolean shapeOk = !result.has(CheckId.GSTIN_001);
    List<Step> steps = new ArrayList<>();
    for (Map.Entry<CheckId, String> entry : LABELS.entrySet()) {
      CheckId check = entry.getKey();
      GstinProblem problem =
          result.problems().stream().filter(p -> p.check() == check).findFirst().orElse(null);
      if (problem != null) {
        Status status = check.defaultSeverity() == Severity.ERROR ? Status.FAIL : Status.WARN;
        steps.add(
            new Step(check, entry.getValue(), status, problem.message(), problem.suggestedFix()));
      } else if (!shapeOk && check != CheckId.GSTIN_007) {
        steps.add(new Step(check, entry.getValue(), Status.SKIP, "", ""));
      } else {
        steps.add(new Step(check, entry.getValue(), Status.PASS, "", ""));
      }
    }
    return steps;
  }

  /** The caveat to show under the verdict: different wording for a pass and a fail. */
  public static String notice(GstinResult result) {
    return result.structurallyValid()
        ? GstinResult.STRUCTURAL_ONLY_NOTICE
        : "This GSTIN is malformed. Remember that even one that passes is only structurally"
            + " valid: passing does not prove a GSTIN is registered or active.";
  }
}
