package io.github.omkar210404.gstcheck.rules;

import io.github.omkar210404.gstcheck.model.CheckId;
import io.github.omkar210404.gstcheck.model.Column;
import io.github.omkar210404.gstcheck.model.InvoiceLine;
import io.github.omkar210404.gstcheck.model.Issue;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * HSN-001 and HSN-002: the HSN or SAC code must be digits only and have one of the lengths allowed
 * in the config. The code is not looked up in any HSN master list.
 */
public final class HsnSacRule implements LineRule {

  @Override
  public List<CheckId> checks() {
    return List.of(CheckId.HSN_001, CheckId.HSN_002);
  }

  @Override
  public void check(InvoiceLine line, RuleContext context, List<Issue> issues) {
    String code = line.hsnOrSac();
    if (code.isEmpty()) {
      return;
    }
    if (!isAsciiDigits(code)) {
      issues.add(
          Issue.of(
              line,
              CheckId.HSN_001,
              Column.HSN_OR_SAC,
              code,
              "The HSN/SAC code '" + code + "' contains something other than digits.",
              "Enter the code as digits only, with no spaces, dots or letters."));
      return;
    }
    Set<Integer> allowed = new TreeSet<>(context.config().hsnAllowedLengths());
    if (!allowed.contains(code.length())) {
      String lengths = allowed.stream().map(String::valueOf).collect(Collectors.joining(", "));
      String fix =
          allowed.contains(code.length() + 1)
              ? "Check the code. A spreadsheet may have dropped a leading zero: format the"
                  + " column as text and re-enter it."
              : "Use a code with one of the allowed lengths.";
      issues.add(
          Issue.of(
              line,
              CheckId.HSN_002,
              Column.HSN_OR_SAC,
              code,
              "The HSN/SAC code has "
                  + code.length()
                  + " digits. The config allows these lengths: "
                  + lengths
                  + ".",
              fix));
    }
  }

  private static boolean isAsciiDigits(String text) {
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c < '0' || c > '9') {
        return false;
      }
    }
    return true;
  }
}
