package io.github.omkar210404.gstcheck.cli;

import io.github.omkar210404.gstcheck.config.CheckerConfig;
import io.github.omkar210404.gstcheck.config.ConfigLoader;
import io.github.omkar210404.gstcheck.gstin.GstinExplanation;
import io.github.omkar210404.gstcheck.gstin.GstinExplanation.Status;
import io.github.omkar210404.gstcheck.gstin.GstinExplanation.Step;
import io.github.omkar210404.gstcheck.gstin.GstinResult;
import io.github.omkar210404.gstcheck.gstin.GstinValidator;
import io.github.omkar210404.gstcheck.model.CheckId;
import java.io.PrintWriter;
import java.nio.file.Path;
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

    out.println("GSTIN: " + printable(value));
    if (!result.has(CheckId.GSTIN_001)) {
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
    for (Step step : GstinExplanation.steps(result)) {
      out.println("  [" + step.status() + "] " + step.check().id() + "  " + step.label());
      if (step.status() == Status.FAIL || step.status() == Status.WARN) {
        out.println("         " + printable(step.message()));
        out.println("         Fix: " + step.suggestedFix());
      }
    }
    out.println();
    out.println(result.structurallyValid() ? "Result: STRUCTURALLY VALID" : "Result: NOT VALID");
    out.println();
    out.println("Note: " + GstinExplanation.notice(result));
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
