package io.github.omkar210404.gstcheck.cli;

import io.github.omkar210404.gstcheck.model.InputProblemException;
import io.github.omkar210404.gstcheck.sample.SampleDataGenerator;
import io.github.omkar210404.gstcheck.sample.SampleDataGenerator.Sample;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/** {@code sample}: writes a synthetic invoice CSV and an example config to try the tool with. */
@Command(
    name = "sample",
    mixinStandardHelpOptions = true,
    description = {
      "Write a SYNTHETIC sample CSV and an example config.",
      "Company names are fictional. GSTINs are generated, not real: never use one as an identity."
    })
final class SampleCommand implements Callable<Integer> {

  static final String CSV_NAME = "sample-invoices.csv";
  static final String CONFIG_NAME = "gst-checker.yml";
  static final String README_NAME = "SAMPLE-README.txt";

  @Spec private CommandSpec spec;

  @Option(
      names = {"-d", "--dir"},
      paramLabel = "DIR",
      defaultValue = "sample",
      description = "Folder to write into. Default: ${DEFAULT-VALUE}.")
  private Path dir;

  @Option(
      names = "--seed",
      paramLabel = "N",
      description = "Seed for the generated GSTINs. The same seed gives the same files.")
  private long seed = SampleDataGenerator.DEFAULT_SEED;

  @Option(names = "--force", description = "Overwrite the files if they already exist.")
  private boolean force;

  @Override
  public Integer call() {
    PrintWriter out = spec.commandLine().getOut();
    Sample sample = SampleDataGenerator.generate(seed);
    Map<Path, String> files = new LinkedHashMap<>();
    files.put(dir.resolve(CSV_NAME), sample.invoicesCsv());
    files.put(dir.resolve(CONFIG_NAME), sample.configYaml());
    files.put(dir.resolve(README_NAME), sample.readme());

    if (!force) {
      for (Path path : files.keySet()) {
        if (Files.exists(path)) {
          throw new InputProblemException(
              display(path)
                  + " already exists. Use --force to overwrite, or --dir to write"
                  + " somewhere else.");
        }
      }
    }
    try {
      Files.createDirectories(dir);
      for (Map.Entry<Path, String> file : files.entrySet()) {
        Files.writeString(file.getKey(), file.getValue(), StandardCharsets.UTF_8);
      }
    } catch (IOException e) {
      throw new InputProblemException("Could not write sample files to " + dir + ": " + e, e);
    }

    out.println("Wrote SYNTHETIC sample data (fictional companies, generated GSTINs):");
    for (Path path : files.keySet()) {
      out.println("  " + display(path));
    }
    out.println();
    out.println("Try it:");
    out.println(
        "  java -jar gst-invoice-checker.jar check "
            + display(dir.resolve(CSV_NAME))
            + " --config "
            + display(dir.resolve(CONFIG_NAME)));
    out.println();
    out.println(
        "The rate list and state list in the config are examples. Verify them against official"
            + " GST notifications before checking real data.");
    out.flush();
    return ExitCodes.OK;
  }

  private static String display(Path path) {
    return path.toString().replace('\\', '/');
  }
}
