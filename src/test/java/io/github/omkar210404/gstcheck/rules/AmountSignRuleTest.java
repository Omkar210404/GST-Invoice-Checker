package io.github.omkar210404.gstcheck.rules;

import static io.github.omkar210404.gstcheck.testutil.Fixtures.ids;
import static io.github.omkar210404.gstcheck.testutil.Fixtures.run;
import static io.github.omkar210404.gstcheck.testutil.Fixtures.validLine;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.omkar210404.gstcheck.model.InvoiceLine;
import io.github.omkar210404.gstcheck.model.Issue;
import java.util.List;
import org.junit.jupiter.api.Test;

class AmountSignRuleTest {

  private final AmountSignRule rule = new AmountSignRule();

  @Test
  void positiveAmountsPass() {
    assertTrue(run(rule, validLine().build()).isEmpty());
  }

  @Test
  void zeroTaxColumnsCessAndRateAreNormal() {
    assertTrue(
        run(rule, validLine().rate("0").cgst("0").sgst("0.00").igst("0").cess("0").build())
            .isEmpty());
    assertTrue(run(rule, validLine().unitPrice("0").build()).isEmpty());
  }

  @Test
  void everyNegativeNumberIsAnError() {
    InvoiceLine line =
        validLine()
            .quantity("-2")
            .unitPrice("-500.00")
            .taxableValue("-1000.00")
            .rate("-18")
            .cgst("-90.00")
            .sgst("-90.00")
            .igst("-0.01")
            .cess("-1")
            .lineTotal("-1180.00")
            .build();
    List<Issue> issues = run(rule, line);
    assertEquals(9, issues.size());
    assertTrue(issues.stream().allMatch(issue -> issue.check().id().equals("MATH-004")));
    assertTrue(issues.stream().allMatch(Issue::isError));
    assertEquals(
        List.of(
            "quantity",
            "unit_price",
            "taxable_value",
            "rate",
            "cgst",
            "sgst",
            "igst",
            "cess",
            "line_total"),
        issues.stream().map(Issue::field).toList());
  }

  @Test
  void negativeZeroIsNotNegative() {
    assertTrue(run(rule, validLine().cess("-0.00").build()).isEmpty());
  }

  @Test
  void zeroTaxableQuantityOrTotalIsAWarning() {
    List<Issue> issues =
        run(rule, validLine().quantity("0").taxableValue("0.00").lineTotal("0").build());
    assertEquals(List.of("MATH-005", "MATH-005", "MATH-005"), ids(issues));
    assertFalse(issues.get(0).isError());
    assertEquals(
        List.of("quantity", "taxable_value", "line_total"),
        issues.stream().map(Issue::field).toList());
  }

  @Test
  void blankAndUnparseableCellsAreIgnored() {
    assertTrue(run(rule, validLine().quantity("").cess("").build()).isEmpty());
    assertTrue(run(rule, validLine().taxableValue("-abc").build()).isEmpty());
  }
}
