package io.github.omkar210404.gstcheck.cli;

import io.github.omkar210404.gstcheck.config.CheckerConfig;
import io.github.omkar210404.gstcheck.config.ConfigLoader;
import io.github.omkar210404.gstcheck.csvio.InvoiceCsvReader;
import io.github.omkar210404.gstcheck.model.InputProblemException;
import io.github.omkar210404.gstcheck.model.InvoiceLine;
import io.github.omkar210404.gstcheck.model.Issue;
import io.github.omkar210404.gstcheck.report.ConsoleReport;
import io.github.omkar210404.gstcheck.report.HtmlReport;
import io.github.omkar210404.gstcheck.report.IssuesCsvReport;
import io.github.omkar210404.gstcheck.report.JsonReport;
import io.github.omkar210404.gstcheck.report.ReportBuilder;
import io.github.omkar210404.gstcheck.report.ReportData;
import io.github.omkar210404.gstcheck.rules.RuleContext;
import io.github.omkar210404.gstcheck.rules.RuleEngine;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/** {@code check}: validates a CSV and writes the console, CSV, JSON and HTML reports. */
@Command(
    name = "check",
    mixinStandardHelpOptions = true,
    description = "Validate an invoice CSV and write the reports.",
    footer = {"", "Exit codes: 0 no errors, 1 errors found, 2 usage or input problem."})
final class CheckCommand implements Callable<Integer> {

  static final String DEFAULT_CONFIG = "gst-checker.yml";

  @ParentCommand private Main parent;

  @Spec private CommandSpec spec;

  @Parameters(index = "0", paramLabel = "FILE", description = "The invoice CSV to check.")
  private Path file;

  @Option(
      names = {"-c", "--config"},
      paramLabel = "FILE",
      description = "YAML config. Default: " + DEFAULT_CONFIG + " in the current folder.")
  private Path config;

  @Option(
      names = {"-o", "--out-dir"},
      paramLabel = "DIR",
      defaultValue = "reports",
      description =
          "Folder for issues.csv, report.json and report.html. Default: ${DEFAULT-VALUE}.")
  private Path outDir;

  @Option(
      names = "--as-of",
      paramLabel = "YYYY-MM-DD",
      description = "Date to treat as today when checking for future dates. Default: today.")
  private LocalDate asOf;

  @Option(names = "--strict", description = "Exit with code 1 on warnings as well as errors.")
  private boolean strict;

  @Option(
      names = "--max-issues",
      paramLabel = "N",
      defaultValue = "15",
      description = "How many issues to list on the console. Default: ${DEFAULT-VALUE}.")
  private int maxIssues;

  @Override
  public Integer call() {
    PrintWriter out = spec.commandLine().getOut();
    Path configFile = config != null ? config : Path.of(DEFAULT_CONFIG);
    if (config == null && !Files.exists(configFile)) {
      throw new InputProblemException(
          "No config file. Pass --config FILE, or run 'gst-invoice-checker sample' to write a"
              + " starter config you can edit. The tool has no built-in rate list on purpose.");
    }
    CheckerConfig checkerConfig = ConfigLoader.load(configFile);
    List<InvoiceLine> lines = new InvoiceCsvReader(checkerConfig).read(file);
    LocalDate today = asOf != null ? asOf : LocalDate.now(parent.clock());

    List<Issue> issues = RuleEngine.standard().run(lines, new RuleContext(checkerConfig, today));
    ReportData report =
        ReportBuilder.build(
            new ReportBuilder.RunInfo(
                Main.version(),
                today,
                display(file),
                display(configFile),
                checkerConfig.financialYear().label()),
            lines,
            issues);

    Path issuesCsv = outDir.resolve("issues.csv");
    Path json = outDir.resolve("report.json");
    Path html = outDir.resolve("report.html");
    try {
      Files.createDirectories(outDir);
      Files.writeString(issuesCsv, IssuesCsvReport.render(report), StandardCharsets.UTF_8);
      Files.writeString(json, JsonReport.render(report), StandardCharsets.UTF_8);
      Files.writeString(html, HtmlReport.render(report), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InputProblemException("Could not write reports to " + outDir + ": " + e, e);
    }

    boolean failedOnWarnings =
        strict && report.summary().errors() == 0 && report.summary().warnings() > 0;
    out.print(ConsoleReport.render(report, maxIssues));
    if (failedOnWarnings) {
      out.println();
      out.println(
          "Strict mode: warnings count as failures, so the exit code is 1 although there are"
              + " no errors.");
    }
    out.println();
    out.println("Reports written:");
    out.println("  " + display(issuesCsv));
    out.println("  " + display(json));
    out.println("  " + display(html));
    if (lines.isEmpty()) {
      out.println();
      out.println("Note: the file has a header but no data rows, so nothing was checked.");
    }
    out.flush();

    boolean failed = report.summary().errors() > 0 || (strict && report.summary().warnings() > 0);
    return failed ? ExitCodes.ERRORS_FOUND : ExitCodes.OK;
  }

  /** Paths are shown with forward slashes so output reads the same on every platform. */
  private static String display(Path path) {
    return path.toString().replace('\\', '/');
  }
}
