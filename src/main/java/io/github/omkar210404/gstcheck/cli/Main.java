package io.github.omkar210404.gstcheck.cli;

import io.github.omkar210404.gstcheck.model.InputProblemException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Properties;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IVersionProvider;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

/** Entry point. Dispatches to the check, gstin, sample and ui subcommands. */
@Command(
    name = "gst-invoice-checker",
    mixinStandardHelpOptions = true,
    versionProvider = Main.VersionProvider.class,
    description = {
      "Checks invoice CSV data against structural Indian GST rules. Works fully offline:",
      "it never contacts another machine. The ui command serves a page on this computer only.",
      "",
      "This is a data-quality helper, not tax advice. A GSTIN that passes is only",
      "structurally valid; that does not prove it is registered or active."
    },
    footer = {
      "",
      "Exit codes: 0 no errors, 1 errors found, 2 usage or input problem.",
      "Start with:  gst-invoice-checker sample   (or: gst-invoice-checker ui)"
    },
    subcommands = {CheckCommand.class, GstinCommand.class, SampleCommand.class, UiCommand.class})
public final class Main implements Runnable {

  private final Clock clock;

  @Spec private CommandSpec spec;

  Main(Clock clock) {
    this.clock = clock;
  }

  /** The clock subcommands use for "today". Tests pass a fixed one. */
  Clock clock() {
    return clock;
  }

  @Override
  public void run() {
    throw new ParameterException(
        spec.commandLine(), "Missing command: check, gstin, sample or ui.");
  }

  public static void main(String[] args) {
    PrintWriter out = new PrintWriter(System.out, true);
    PrintWriter err = new PrintWriter(System.err, true);
    int exitCode = run(args, out, err, Clock.systemDefaultZone());
    out.flush();
    err.flush();
    System.exit(exitCode);
  }

  /** Runs the CLI without exiting the JVM, so tests can call it directly. */
  public static int run(String[] args, PrintWriter out, PrintWriter err, Clock clock) {
    CommandLine commandLine = new CommandLine(new Main(clock));
    commandLine.setOut(out);
    commandLine.setErr(err);
    commandLine.setExecutionExceptionHandler(
        (exception, cmd, parseResult) -> {
          if (exception instanceof InputProblemException) {
            cmd.getErr().println("Error: " + exception.getMessage());
          } else {
            cmd.getErr().println("Unexpected error: " + exception);
          }
          return ExitCodes.USAGE;
        });
    return commandLine.execute(args);
  }

  /** The version from {@code version.properties}, which Maven fills in at build time. */
  static String version() {
    try (InputStream in = Main.class.getResourceAsStream("/version.properties")) {
      if (in == null) {
        return "unknown";
      }
      Properties properties = new Properties();
      properties.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
      return properties.getProperty("version", "unknown");
    } catch (IOException e) {
      return "unknown";
    }
  }

  /** Supplies the text for {@code --version}. */
  static final class VersionProvider implements IVersionProvider {
    @Override
    public String[] getVersion() {
      return new String[] {"gst-invoice-checker " + version()};
    }
  }
}
