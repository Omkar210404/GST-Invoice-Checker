package io.github.omkar210404.gstcheck.rules;

import static io.github.omkar210404.gstcheck.testutil.Fixtures.ids;
import static io.github.omkar210404.gstcheck.testutil.Fixtures.run;
import static io.github.omkar210404.gstcheck.testutil.Fixtures.validLine;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.omkar210404.gstcheck.model.Issue;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class InvoiceNumberRuleTest {

  private final InvoiceNumberRule rule = new InvoiceNumberRule();

  private List<String> check(String invoiceNo) {
    return ids(run(rule, validLine().invoiceNo(invoiceNo).build()));
  }

  @Test
  void sixteenCharactersIsTheLongestAllowed() {
    String sixteen = "ABCD/2026-27/001";
    assertEquals(16, sixteen.length());
    assertTrue(check(sixteen).isEmpty());
  }

  @Test
  void seventeenCharactersIsTooLong() {
    String seventeen = "ABCD/2026-27/0001";
    assertEquals(17, seventeen.length());
    assertEquals(List.of("INV-001"), check(seventeen));
  }

  @Test
  void theMessageSaysHowLongTheNumberIs() {
    Issue issue = run(rule, validLine().invoiceNo("A".repeat(20)).build()).get(0);
    assertTrue(issue.message().contains("20 characters"));
    assertTrue(issue.message().contains("16"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"1", "A", "INV-001", "inv/001", "2026/27-0001", "-", "/", "aZ09/-"})
  void lettersDigitsSlashAndHyphenAreAllowed(String invoiceNo) {
    assertTrue(check(invoiceNo).isEmpty(), invoiceNo);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "INV 001",
        "INV_001",
        "INV#001",
        "INV.001",
        "INV\\001",
        "INV:001",
        "INV(1)",
        "INV,1",
        "INV+1",
        "INV\t1",
        "ÍNV-1",
        "INV–1",
        "इन्व-1",
        "INV-१"
      })
  void anythingElseIsRejected(String invoiceNo) {
    assertEquals(List.of("INV-002"), check(invoiceNo), invoiceNo);
  }

  @Test
  void theMessageListsEachBadCharacterOnce() {
    Issue issue = run(rule, validLine().invoiceNo("A_B_C#D E").build()).get(0);
    assertTrue(issue.message().contains("'_', '#', space"), issue.message());
  }

  @Test
  void tooLongAndBadCharactersAreBothReported() {
    assertEquals(List.of("INV-001", "INV-002"), check("INVOICE_NUMBER_0001"));
  }

  @Test
  void lengthCountsCharactersNotUtf16Units() {
    // Sixteen characters, the last of which is outside the basic plane (two UTF-16 units).
    String sixteen = "A".repeat(15) + "😀";
    assertEquals(List.of("INV-002"), check(sixteen));
  }

  @Test
  void anEmptyNumberIsLeftToTheRequiredFieldCheck() {
    assertTrue(check("").isEmpty());
  }
}
