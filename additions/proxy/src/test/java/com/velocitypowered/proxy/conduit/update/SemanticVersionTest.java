/*
 * Copyright (C) 2026 Velocity Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.velocitypowered.proxy.conduit.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SemanticVersionTest {

  private static int cmp(String a, String b) {
    return SemanticVersion.parse(a).orElseThrow()
        .compareTo(SemanticVersion.parse(b).orElseThrow());
  }

  @Test
  void parsesLenientlyAndRejectsGarbage() {
    assertTrue(SemanticVersion.parse("1.3.5").isPresent());
    assertTrue(SemanticVersion.parse("v1.3.5").isPresent(), "leading v accepted");
    assertTrue(SemanticVersion.parse("1.3").isPresent(), "missing patch defaults to 0");
    assertTrue(SemanticVersion.parse("dev").isEmpty(), "non-version rejected");
    assertTrue(SemanticVersion.parse(null).isEmpty(), "null rejected");
  }

  @Test
  void ordersByMajorMinorPatch() {
    assertTrue(cmp("2.0.0", "1.9.9") > 0);
    assertTrue(cmp("1.4.0", "1.3.9") > 0);
    assertTrue(cmp("1.3.5", "1.3.4") > 0);
    assertEquals(0, cmp("1.3.5", "1.3.5"));
    assertEquals(0, cmp("1.3", "1.3.0"), "missing patch equals .0");
  }

  @Test
  void prereleaseOrdersBeforeRelease() {
    assertTrue(cmp("1.3.5-beta.1", "1.3.5") < 0);
    assertTrue(cmp("1.3.5-beta.2", "1.3.5-beta.1") > 0);
    assertTrue(cmp("1.3.5-beta.10", "1.3.5-beta.2") > 0, "numeric identifiers compare numerically");
    assertTrue(cmp("1.3.5-alpha", "1.3.5-beta") < 0);
  }

  @Test
  void buildMetadataIsIgnoredForOrdering() {
    assertEquals(0, cmp("1.3.5+build.7", "1.3.5"));
  }

  @Test
  void reportsPrereleaseFlag() {
    assertFalse(SemanticVersion.parse("1.3.5").orElseThrow().isPrerelease());
    assertTrue(SemanticVersion.parse("1.3.5-rc.1").orElseThrow().isPrerelease());
  }
}
