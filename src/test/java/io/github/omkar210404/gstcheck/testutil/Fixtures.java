package io.github.omkar210404.gstcheck.testutil;

import io.github.omkar210404.gstcheck.config.CheckerConfig;
import io.github.omkar210404.gstcheck.config.ConfigLoader;
import io.github.omkar210404.gstcheck.gstin.GstinChecksum;
import io.github.omkar210404.gstcheck.model.InvoiceLine;
import io.github.omkar210404.gstcheck.model.Issue;
import io.github.omkar210404.gstcheck.rules.Rule;
import io.github.omkar210404.gstcheck.rules.RuleContext;
import java.time.LocalDate;
import java.util.List;

/** Shared test data. Every GSTIN here is synthetic: built from a made-up body plus a checksum. */
public final class Fixtures {

  /** The date tests treat as "today". */
  public static final LocalDate AS_OF = LocalDate.of(2026, 10, 12);

  /** Synthetic supplier in state 27. */
  public static final String SUPPLIER_27 = gstin("27ABCXD1234E1Z");

  /** Synthetic buyer in state 27. */
  public static final String BUYER_27 = gstin("27PQRXS5678T1Z");

  /** Synthetic buyer in state 24. */
  public static final String BUYER_24 = gstin("24LMNXO2468P1Z");

  /** Synthetic supplier in state 04, which the example config marks as charging UTGST. */
  public static final String SUPPLIER_04 = gstin("04GHIXJ1357K1Z");

  private Fixtures() {}

  /** Appends the check character to a 14-character body. */
  public static String gstin(String body) {
    return body + GstinChecksum.compute(body);
  }

  /** The example config bundled with the tool (financial year 2026-27, tolerance 0.01). */
  public static CheckerConfig config() {
    return ConfigLoader.loadExample();
  }

  /** The example config with extra YAML appended, for tests that change one setting. */
  public static CheckerConfig configWith(String replaceFrom, String replaceTo) {
    String yaml = ConfigLoader.exampleText();
    if (!yaml.contains(replaceFrom)) {
      throw new IllegalArgumentException("Example config does not contain: " + replaceFrom);
    }
    return ConfigLoader.parse(yaml.replace(replaceFrom, replaceTo), "test config");
  }

  public static RuleContext context() {
    return new RuleContext(config(), AS_OF);
  }

  public static RuleContext context(CheckerConfig config) {
    return new RuleContext(config, AS_OF);
  }

  /**
   * A correct intra-state line: 2 x 500.00 = 1000.00 taxable at 18%, CGST 90.00 + SGST 90.00, total
   * 1180.00, dated inside financial year 2026-27. No rule should object to it.
   */
  public static InvoiceLine.Builder validLine() {
    return InvoiceLine.builder()
        .lineNo(2)
        .invoiceNo("INV/26-27/001")
        .invoiceDateText("2026-07-15")
        .invoiceDate(LocalDate.of(2026, 7, 15))
        .supplierGstin(SUPPLIER_27)
        .buyerGstin(BUYER_27)
        .placeOfSupply("27")
        .hsnOrSac("123456")
        .description("Test item")
        .quantity("2")
        .unit("NOS")
        .unitPrice("500.00")
        .taxableValue("1000.00")
        .rate("18")
        .cgst("90.00")
        .sgst("90.00")
        .igst("0.00")
        .cess("0.00")
        .lineTotal("1180.00");
  }

  /** A correct inter-state line: the same sale to state 24 with IGST 180.00. */
  public static InvoiceLine.Builder validInterStateLine() {
    return validLine()
        .buyerGstin(BUYER_24)
        .placeOfSupply("24")
        .cgst("0.00")
        .sgst("0.00")
        .igst("180.00");
  }

  /** Runs one rule on one line with the standard context. */
  public static List<Issue> run(Rule rule, InvoiceLine line) {
    return rule.apply(List.of(line), context());
  }

  public static List<Issue> run(Rule rule, InvoiceLine line, RuleContext context) {
    return rule.apply(List.of(line), context);
  }

  /** The check IDs of the issues, in order. */
  public static List<String> ids(List<Issue> issues) {
    return issues.stream().map(issue -> issue.check().id()).toList();
  }
}
