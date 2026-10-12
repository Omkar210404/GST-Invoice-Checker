package io.github.omkar210404.gstcheck.rules;

import static io.github.omkar210404.gstcheck.testutil.Fixtures.ids;
import static io.github.omkar210404.gstcheck.testutil.Fixtures.validLine;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.omkar210404.gstcheck.model.Issue;
import io.github.omkar210404.gstcheck.testutil.Fixtures;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class InvoiceDateRuleTest {

  private final InvoiceDateRule rule = new InvoiceDateRule();

  /** Runs the rule for a date, with "today" fixed at {@code asOf}. */
  private List<Issue> check(String date, String asOf) {
    return rule.apply(
        List.of(validLine().invoiceDateText(date).invoiceDate(LocalDate.parse(date)).build()),
        new RuleContext(Fixtures.config(), LocalDate.parse(asOf)));
  }

  @Test
  void aDateInsideTheYearAndNotInTheFuturePasses() {
    assertTrue(check("2026-07-15", "2026-10-12").isEmpty());
  }

  @Test
  void textThatDidNotParseIsNotAValidDate() {
    List<Issue> issues =
        Fixtures.run(rule, validLine().invoiceDateText("30-02-2027").invoiceDate(null).build());
    assertEquals(List.of("DATE-001"), ids(issues));
    assertEquals("30-02-2027", issues.get(0).value());
    assertTrue(issues.get(0).message().contains("yyyy-MM-dd"), "lists the accepted formats");
  }

  @Test
  void anEmptyDateIsLeftToTheRequiredFieldCheck() {
    assertTrue(
        Fixtures.run(rule, validLine().invoiceDateText("").invoiceDate(null).build()).isEmpty());
  }

  @Test
  void todayIsNotInTheFutureButTomorrowIs() {
    assertTrue(check("2026-10-12", "2026-10-12").isEmpty());
    assertEquals(List.of("DATE-002"), ids(check("2026-10-13", "2026-10-12")));
  }

  @Test
  void theFirstAndLastDayOfTheFinancialYearAreInside() {
    assertTrue(check("2026-04-01", "2027-12-31").isEmpty());
    assertTrue(check("2027-03-31", "2027-12-31").isEmpty());
  }

  @Test
  void theDayBeforeAndTheDayAfterTheFinancialYearAreOutside() {
    assertEquals(List.of("DATE-003"), ids(check("2026-03-31", "2027-12-31")));
    assertEquals(List.of("DATE-003"), ids(check("2027-04-01", "2027-12-31")));
  }

  @Test
  void theMessageNamesTheYearAndItsRange() {
    Issue issue = check("2026-03-31", "2027-12-31").get(0);
    assertTrue(issue.message().contains("2026-27"));
    assertTrue(issue.message().contains("2026-04-01"));
    assertTrue(issue.message().contains("2027-03-31"));
  }

  @Test
  void aFutureDateOutsideTheYearGetsBothIssues() {
    assertEquals(List.of("DATE-002", "DATE-003"), ids(check("2028-01-01", "2026-10-12")));
  }

  @Test
  void theFinancialYearComesFromTheConfig() {
    RuleContext context =
        Fixtures.context(
            Fixtures.configWith("financial_year: \"2026-27\"", "financial_year: \"2025-26\""));
    List<Issue> issues =
        rule.apply(
            List.of(
                validLine()
                    .invoiceDateText("2026-07-15")
                    .invoiceDate(LocalDate.of(2026, 7, 15))
                    .build()),
            context);
    assertEquals(List.of("DATE-003"), ids(issues));
  }
}
