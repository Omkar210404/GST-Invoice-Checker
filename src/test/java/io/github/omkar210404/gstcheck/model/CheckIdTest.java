package io.github.omkar210404.gstcheck.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.omkar210404.gstcheck.rules.Rule;
import io.github.omkar210404.gstcheck.rules.RuleEngine;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CheckIdTest {

  @Test
  void idsAreUniqueAndWellFormed() {
    Set<String> seen = new HashSet<>();
    for (CheckId check : CheckId.values()) {
      assertTrue(check.id().matches("[A-Z]+-\\d{3}"), check.id());
      assertTrue(seen.add(check.id()), "duplicate ID " + check.id());
      assertFalse(check.title().isBlank());
    }
  }

  @Test
  void enumNameMatchesThePublicId() {
    for (CheckId check : CheckId.values()) {
      assertEquals(check.name().replace('_', '-'), check.id());
    }
  }

  @Test
  void lookupIgnoresCaseAndSpaces() {
    assertEquals(Optional.of(CheckId.GSTIN_006), CheckId.fromId(" gstin-006 "));
    assertEquals(Optional.empty(), CheckId.fromId("GSTIN-999"));
    assertEquals(Optional.empty(), CheckId.fromId(null));
  }

  @Test
  void everyCheckIsOwnedByExactlyOneStandardRule() {
    Map<CheckId, String> owner = new EnumMap<>(CheckId.class);
    for (Rule rule : RuleEngine.standard().rules()) {
      for (CheckId check : rule.checks()) {
        String previous = owner.put(check, rule.getClass().getSimpleName());
        assertNull(previous, check.id() + " is claimed by two rules");
      }
    }
    for (CheckId check : CheckId.values()) {
      assertTrue(owner.containsKey(check), check.id() + " is not raised by any rule");
    }
  }

  @Test
  void severityLabelsRoundTrip() {
    for (Severity severity : Severity.values()) {
      assertEquals(Optional.of(severity), Severity.fromLabel(severity.label()));
    }
    assertEquals(Optional.of(Severity.WARNING), Severity.fromLabel(" Warning "));
    assertEquals(Optional.empty(), Severity.fromLabel("info"));
    assertEquals(Optional.empty(), Severity.fromLabel(null));
  }
}
