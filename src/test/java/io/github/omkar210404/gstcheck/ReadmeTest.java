package io.github.omkar210404.gstcheck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.omkar210404.gstcheck.model.CheckId;
import io.github.omkar210404.gstcheck.report.Notices;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Keeps the README honest: its checks table and its caveats must match the code. */
class ReadmeTest {

  private static final Pattern TABLE_ROW =
      Pattern.compile("^\\| [A-Z]+-\\d{3} \\| (error|warning) \\| .+ \\|$", Pattern.MULTILINE);

  private static String readme() throws IOException {
    return Files.readString(Path.of("README.md"), StandardCharsets.UTF_8).replace("\r\n", "\n");
  }

  @Test
  void checksTableListsExactlyTheChecksInTheCatalogue() throws IOException {
    String readme = readme();
    for (CheckId check : CheckId.values()) {
      String row =
          "| "
              + check.id()
              + " | "
              + check.defaultSeverity().label()
              + " | "
              + check.title()
              + " |";
      assertTrue(readme.contains(row), "README checks table is missing or out of date: " + row);
    }
    assertEquals(
        CheckId.values().length,
        TABLE_ROW.matcher(readme).results().count(),
        "README checks table has a row for a check that no longer exists");
  }

  @Test
  void carriesTheDisclaimerAndTheHonestyNotes() throws IOException {
    String readme = readme();
    assertTrue(readme.contains(Notices.DISCLAIMER), "the disclaimer must appear word for word");
    assertTrue(readme.contains("That does not prove the number is registered or active"));
    assertTrue(readme.contains("Never use one as a real identity"));
    assertTrue(readme.contains("Verify both lists against official GST notifications"));
    assertTrue(readme.contains("## Limitations"));
  }
}
