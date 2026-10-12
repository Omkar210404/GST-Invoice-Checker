package io.github.omkar210404.gstcheck.rules;

import io.github.omkar210404.gstcheck.model.CheckId;
import io.github.omkar210404.gstcheck.model.Column;
import io.github.omkar210404.gstcheck.model.InvoiceLine;
import io.github.omkar210404.gstcheck.model.Issue;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * INV-001 and INV-002: an invoice number may be at most 16 characters and may contain only letters,
 * digits, '/' and '-'.
 */
public final class InvoiceNumberRule implements LineRule {

  public static final int MAX_LENGTH = 16;

  @Override
  public List<CheckId> checks() {
    return List.of(CheckId.INV_001, CheckId.INV_002);
  }

  @Override
  public void check(InvoiceLine line, RuleContext context, List<Issue> issues) {
    String number = line.invoiceNo();
    if (number.isEmpty()) {
      return;
    }
    int length = number.codePointCount(0, number.length());
    if (length > MAX_LENGTH) {
      issues.add(
          Issue.of(
              line,
              CheckId.INV_001,
              Column.INVOICE_NO,
              number,
              "The invoice number has "
                  + length
                  + " characters. The maximum is "
                  + MAX_LENGTH
                  + ".",
              "Shorten the invoice number series to " + MAX_LENGTH + " characters or fewer."));
    }

    Set<String> notAllowed = new LinkedHashSet<>();
    number.codePoints().filter(c -> !isAllowed(c)).forEach(c -> notAllowed.add(describe(c)));
    if (!notAllowed.isEmpty()) {
      issues.add(
          Issue.of(
              line,
              CheckId.INV_002,
              Column.INVOICE_NO,
              number,
              "The invoice number contains characters that are not allowed: "
                  + notAllowed.stream().collect(Collectors.joining(", "))
                  + ".",
              "Use only letters, digits, '/' and '-' in invoice numbers."));
    }
  }

  private static boolean isAllowed(int codePoint) {
    return (codePoint >= 'A' && codePoint <= 'Z')
        || (codePoint >= 'a' && codePoint <= 'z')
        || (codePoint >= '0' && codePoint <= '9')
        || codePoint == '/'
        || codePoint == '-';
  }

  private static String describe(int codePoint) {
    if (codePoint == ' ') {
      return "space";
    }
    if (Character.isWhitespace(codePoint) || Character.isISOControl(codePoint)) {
      return String.format("U+%04X", codePoint);
    }
    return "'" + Character.toString(codePoint) + "'";
  }
}
