package io.github.omkar210404.gstcheck.cli;

import io.github.omkar210404.gstcheck.config.CheckerConfig;
import io.github.omkar210404.gstcheck.config.ConfigLoader;
import io.github.omkar210404.gstcheck.gstin.GstinProblem;
import io.github.omkar210404.gstcheck.gstin.GstinResult;
import io.github.omkar210404.gstcheck.gstin.GstinValidator;
import io.github.omkar210404.gstcheck.model.CheckId;
import io.github.omkar210404.gstcheck.model.Severity;
import io.github.omkar210404.gstcheck.report.Notices;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/** {@code gstin}: checks one GSTIN and explains each part of the result. */
@Command(
    name = "gstin",
    mixinStandardHelpOptions = true,
    description = {
      "Check the structure of one GSTIN and explain the result.",
      "A pass means structurally valid only. It does not prove the GSTIN is registered or active."
    },
    footer = {"", "Exit codes: 0 structurally valid, 1 not valid, 2 usage or input problem."})
final class GstinCommand implements Callable<Integer> {

  /** What each check looks at, phrased neutrally so it reads well next to PASS or FAIL. */
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

  @Spec private CommandSpec spec;

  @Parameters(index = "0", paramLabel = "GSTIN", description = "The GSTIN to check.")
  private String gstin;

  @Option(
      names = {"-c", "--config"},
      paramLabel = "FILE",
      description = "YAML config to take state codes from. Default: the built-in example list.")
  private Path config;

  @Override
  public Integer call() {
    PrintWriter out = spec.commandLine().getOut();
    CheckerConfig checkerConfig =
        config != null ? ConfigLoader.load(config) : ConfigLoader.loadExample();
    GstinResult result = new GstinValidator(checkerConfig.stateCodes()).validate(gstin);
    String value = result.normalized();
    boolean shapeOk = !result.has(CheckId.GSTIN_001);

    out.println("GSTIN: " + printable(value));
    if (shapeOk) {
      String state = value.substring(0, 2);
      out.println();
      out.println(
          "  Characters 1-2    "
              + state
              + "          State code"
              + (checkerConfig.isKnownState(state)
                  ? ": " + checkerConfig.describeState(state)
                  : ""));
      out.println("  Characters 3-12   " + value.substring(2, 12) + "  PAN-shaped part");
      out.println("  Character 13      " + value.charAt(12) + "           Entity number");
      out.println("  Character 14      " + value.charAt(13) + "           Fixed character");
      out.println("  Character 15      " + value.charAt(14) + "           Check character");
    }
    out.println();
    out.println("Checks");
    for (Map.Entry<CheckId, String> entry : LABELS.entrySet()) {
      CheckId check = entry.getKey();
      GstinProblem problem =
          result.problems().stream().filter(p -> p.check() == check).findFirst().orElse(null);
      String status;
      if (problem != null) {
        status = check.defaultSeverity() == Severity.ERROR ? "FAIL" : "WARN";
      } else if (!shapeOk && check != CheckId.GSTIN_007) {
        // Positions mean nothing when the length or characters are wrong.
        status = "SKIP";
      } else {
        status = "PASS";
      }
      out.println("  [" + status + "] " + check.id() + "  " + entry.getValue());
      if (problem != null) {
        out.println("         " + printable(problem.message()));
        out.println("         Fix: " + problem.suggestedFix());
      }
    }
    out.println();
    out.println(result.structurallyValid() ? "Result: STRUCTURALLY VALID" : "Result: NOT VALID");
    out.println();
    out.println("Note: " + Notices.STRUCTURAL_ONLY);
    out.println(
        "Note: state codes came from "
            + (config != null
                ? config.toString().replace('\\', '/')
                : "the built-in example list, which you should verify")
            + ".");
    out.flush();
    return result.structurallyValid() ? ExitCodes.OK : ExitCodes.ERRORS_FOUND;
  }

  /** Replaces control characters so odd input cannot disturb the terminal, and caps the length. */
  private static String printable(String text) {
    StringBuilder clean = new StringBuilder();
    text.codePoints()
        .limit(200)
        .map(c -> Character.isISOControl(c) ? '?' : c)
        .forEach(clean::appendCodePoint);
    return clean.toString();
  }
}
