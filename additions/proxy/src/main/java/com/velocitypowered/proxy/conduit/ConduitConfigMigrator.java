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

package com.velocitypowered.proxy.conduit;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.electronwill.nightconfig.toml.TomlWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Keeps an existing {@code conduit.toml} forward-compatible with newer Conduit versions, without
 * ever disturbing what the operator already wrote.
 *
 * <p>On every startup this migrator compares the user's file against the {@code conduit.toml}
 * defaults bundled in the jar and performs an <b>append-only</b> update directly on the file text:
 *
 * <ul>
 *   <li><b>New sections</b> introduced by a later release are appended verbatim from the shipped
 *       defaults — header, explanatory comments, and every key with its default value — so the
 *       operator picks them up fully documented and formatted exactly as shipped.</li>
 *   <li><b>New keys</b> added inside a section the operator already has are inserted just below that
 *       section header, again copied verbatim (comment + default) from the shipped defaults.</li>
 *   <li><b>Existing content is never rewritten.</b> Every line the operator wrote — values,
 *       comments, ordering, blank lines, indentation — is preserved byte-for-byte. Only brand-new
 *       lines are inserted. A value the operator set (even one equal to the default, e.g.
 *       {@code [luckperms] bundle-enabled = false}) is therefore impossible to reset.</li>
 *   <li><b>Removed options are left in place.</b> A key that no longer exists in the defaults is
 *       simply ignored by the loader, so old files keep working without manual cleanup.</li>
 *   <li><b>Renames</b> declared in {@link #RENAMES} carry the operator's value from the old key to
 *       the new one and drop the old line, so a moved option migrates instead of silently
 *       reverting to its default.</li>
 * </ul>
 *
 * <p>If nothing is missing the file is not touched at all (its modification time is preserved). The
 * update is best-effort: on any error the file is left exactly as-is and startup continues against
 * whatever is on disk.
 */
final class ConduitConfigMigrator {

  private static final Logger logger = LogManager.getLogger(ConduitConfigMigrator.class);

  private static final String DEFAULT_RESOURCE =
      "/com/velocitypowered/proxy/conduit/conduit.toml";

  private static final Pattern SECTION_HEADER = Pattern.compile("^\\s*\\[([^\\[\\]]+)]\\s*$");
  private static final Pattern KEY_LINE = Pattern.compile("^\\s*([A-Za-z0-9_-]+)\\s*=.*$");

  /**
   * Dotted {@code section.key} old-path → new-path renames applied before missing keys are filled
   * in. Populate this when an option is moved or renamed between versions; the operator's existing
   * value is copied to the new key and the old line removed. Empty today — the extension point for
   * future structural changes so a moved option migrates cleanly instead of silently resetting.
   */
  private static final Map<String, String> RENAMES = new LinkedHashMap<>();

  private ConduitConfigMigrator() {
  }

  /**
   * Tops up {@code file} with any options missing relative to the bundled defaults, applying any
   * declared renames first, editing the file text in place so existing content is preserved
   * exactly. Best-effort: on any error the file is left untouched.
   *
   * @param file the operator's {@code conduit.toml}
   */
  static void migrate(Path file) {
    String defaultsText = loadDefaultsText();
    if (defaultsText == null) {
      return;
    }

    try {
      String userText = Files.readString(file, StandardCharsets.UTF_8);
      CommentedConfig user = parse(userText);
      CommentedConfig defaults = parse(defaultsText);

      Defaults shipped = index(defaultsText);
      // Preserve the operator's line-ending style; process with '\r' stripped so regex matching is
      // not defeated by CRLF files. We only ever insert lines into this working copy.
      String eol = userText.contains("\r\n") ? "\r\n" : "\n";
      List<String> lines = splitLines(userText);

      List<String> renamed = applyRenames(lines, user, shipped);
      List<String> added = new ArrayList<>();
      fillMissing(lines, user, defaults, shipped, added);

      if (added.isEmpty() && renamed.isEmpty()) {
        // Nothing to do — leave the file byte-for-byte untouched.
        return;
      }

      Files.writeString(file, String.join(eol, lines), StandardCharsets.UTF_8);
      if (!renamed.isEmpty()) {
        logger.info("[Conduit] conduit.toml: migrated {} renamed option(s): {}",
            renamed.size(), String.join(", ", renamed));
      }
      if (!added.isEmpty()) {
        logger.info("[Conduit] conduit.toml: added {} new option(s) with defaults: {}",
            added.size(), String.join(", ", added));
      }
    } catch (RuntimeException | IOException e) {
      logger.warn("[Conduit] Could not auto-update conduit.toml ({}); "
          + "leaving it unchanged.", e.toString());
    }
  }

  /**
   * Appends missing sections (whole) and inserts missing keys (into existing sections), copying the
   * text verbatim from the shipped defaults so formatting and comments match. Records each added
   * {@code section.key} in {@code added}.
   */
  private static void fillMissing(List<String> lines, CommentedConfig user, CommentedConfig defaults,
      Defaults shipped, List<String> added) {
    for (Map.Entry<String, Section> sectionEntry : shipped.sections.entrySet()) {
      String section = sectionEntry.getKey();
      Section shippedSection = sectionEntry.getValue();

      if (!(defaults.get(List.of(section)) instanceof CommentedConfig)) {
        // Only migrate genuine [section] tables; ignore any stray top-level keys.
        continue;
      }

      if (!(user.get(List.of(section)) instanceof CommentedConfig userSection)) {
        // Whole section missing — append its shipped block at the end of the file.
        appendSection(lines, shippedSection);
        for (String key : shippedSection.keyBlocks.keySet()) {
          added.add(section + "." + key);
        }
        continue;
      }

      // Section exists — insert only the keys the operator does not already have, just below the
      // section header, in the order they appear in the shipped defaults.
      List<String> toInsert = new ArrayList<>();
      List<String> insertedKeys = new ArrayList<>();
      for (Map.Entry<String, List<String>> keyEntry : shippedSection.keyBlocks.entrySet()) {
        String key = keyEntry.getKey();
        if (!userSection.contains(List.of(key))) {
          toInsert.addAll(keyEntry.getValue());
          insertedKeys.add(key);
        }
      }
      if (!toInsert.isEmpty()) {
        int headerIndex = findSectionHeader(lines, section);
        if (headerIndex < 0) {
          // The section is present in the parsed model but its header could not be located in the
          // text (unusual); skip rather than risk corrupting the file.
          continue;
        }
        lines.addAll(headerIndex + 1, toInsert);
        for (String key : insertedKeys) {
          added.add(section + "." + key);
        }
      }
    }
  }

  /** Appends a whole shipped section block to the end of the file, preceded by a blank line. */
  private static void appendSection(List<String> lines, Section shippedSection) {
    // Ensure exactly one blank line separates the appended section from prior content.
    if (!lines.isEmpty() && !lines.get(lines.size() - 1).isBlank()) {
      lines.add("");
    }
    lines.addAll(shippedSection.headerBlock);
    for (List<String> keyBlock : shippedSection.keyBlocks.values()) {
      lines.addAll(keyBlock);
    }
  }

  /**
   * Moves values for any {@link #RENAMES} whose old {@code section.key} still exists and whose new
   * key does not, editing the file text: the old key line is removed and the new key inserted with
   * the operator's carried value. Returns the {@code old → new} descriptions actually applied.
   */
  private static List<String> applyRenames(List<String> lines, CommentedConfig user,
      Defaults shipped) {
    List<String> applied = new ArrayList<>();
    for (Map.Entry<String, String> rename : RENAMES.entrySet()) {
      List<String> oldPath = List.of(rename.getKey().split("\\."));
      List<String> newPath = List.of(rename.getValue().split("\\."));
      if (oldPath.size() != 2 || newPath.size() != 2) {
        continue;
      }
      if (!user.contains(oldPath) || user.contains(newPath)) {
        continue;
      }

      Object value = user.get(oldPath);
      String newSection = newPath.get(0);
      String newKey = newPath.get(1);

      Section shippedSection = shipped.sections.get(newSection);
      if (shippedSection == null) {
        continue;
      }

      // Remove the old key line from its section and insert the new key (carrying the value) below
      // the new section header (appending the section first if the operator does not have it).
      removeKeyLine(lines, oldPath.get(0), oldPath.get(1));
      if (findSectionHeader(lines, newSection) < 0) {
        appendSection(lines, shippedSection);
      }
      List<String> newKeyBlock = renameKeyBlock(shippedSection, newKey, value);
      int headerIndex = findSectionHeader(lines, newSection);
      if (headerIndex >= 0) {
        lines.addAll(headerIndex + 1, newKeyBlock);
        user.set(newPath, value);
        user.remove(oldPath);
        applied.add(rename.getKey() + " → " + rename.getValue());
      }
    }
    return applied;
  }

  /**
   * Builds the text block for a renamed key: the shipped comment lines for the new key, followed by
   * {@code newKey = <carried value>} serialized as TOML.
   */
  private static List<String> renameKeyBlock(Section shippedSection, String newKey, Object value) {
    List<String> block = new ArrayList<>();
    List<String> shippedBlock = shippedSection.keyBlocks.get(newKey);
    if (shippedBlock != null) {
      // Keep the shipped comment lines (everything above the key line).
      for (String line : shippedBlock) {
        if (KEY_LINE.matcher(line).matches()) {
          break;
        }
        block.add(line);
      }
    }
    block.add(renderKeyValue(newKey, value));
    return block;
  }

  /** Serializes a single {@code key = value} line as TOML using nightconfig. */
  private static String renderKeyValue(String key, Object value) {
    CommentedConfig tmp = TomlFormat.instance().createConfig();
    tmp.set(List.of(key), value);
    StringWriter sw = new StringWriter();
    new TomlWriter().write(tmp, sw);
    return sw.toString().strip();
  }

  /** Removes the single {@code key = ...} line belonging to {@code section} from the text. */
  private static void removeKeyLine(List<String> lines, String section, String key) {
    int headerIndex = findSectionHeader(lines, section);
    if (headerIndex < 0) {
      return;
    }
    for (int i = headerIndex + 1; i < lines.size(); i++) {
      if (SECTION_HEADER.matcher(lines.get(i)).matches()) {
        return; // reached the next section without finding the key
      }
      Matcher m = KEY_LINE.matcher(lines.get(i));
      if (m.matches() && m.group(1).equals(key)) {
        lines.remove(i);
        return;
      }
    }
  }

  /** Finds the line index of the {@code [section]} header, or -1 if absent. */
  private static int findSectionHeader(List<String> lines, String section) {
    for (int i = 0; i < lines.size(); i++) {
      Matcher m = SECTION_HEADER.matcher(lines.get(i));
      if (m.matches() && m.group(1).trim().equals(section)) {
        return i;
      }
    }
    return -1;
  }

  private static CommentedConfig parse(String text) {
    try (Reader reader = new java.io.StringReader(text)) {
      return TomlFormat.instance().createParser().parse(reader);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static String loadDefaultsText() {
    try (InputStream in = ConduitConfigMigrator.class.getResourceAsStream(DEFAULT_RESOURCE)) {
      if (in == null) {
        logger.warn("[Conduit] Bundled conduit.toml defaults not found; skipping auto-update.");
        return null;
      }
      try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[4096];
        int n;
        while ((n = reader.read(buf)) != -1) {
          sb.append(buf, 0, n);
        }
        return sb.toString();
      }
    } catch (Exception e) {
      logger.warn("[Conduit] Failed to read bundled conduit.toml defaults: {}", e.toString());
      return null;
    }
  }

  /**
   * Indexes the shipped defaults text into ordered sections, each with its header block (leading
   * comment box + {@code [section]} line) and ordered key blocks (each key's leading comments + its
   * {@code key = value} line, including any multi-line array continuation).
   */
  private static Defaults index(String defaultsText) {
    Defaults defaults = new Defaults();
    List<String> lines = splitLines(defaultsText);

    Section current = null;
    List<String> pendingComments = new ArrayList<>();
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
      Matcher header = SECTION_HEADER.matcher(line);
      if (header.matches()) {
        current = new Section();
        current.headerBlock.addAll(pendingComments);
        current.headerBlock.add(line);
        pendingComments.clear();
        defaults.sections.put(header.group(1).trim(), current);
        continue;
      }

      Matcher key = KEY_LINE.matcher(line);
      if (key.matches() && current != null) {
        List<String> block = new ArrayList<>(pendingComments);
        pendingComments.clear();
        block.add(line);
        // Absorb multi-line array values until the brackets balance.
        int depth = bracketDelta(line);
        int j = i;
        while (depth > 0 && j + 1 < lines.size()) {
          j++;
          block.add(lines.get(j));
          depth += bracketDelta(lines.get(j));
        }
        i = j;
        current.keyBlocks.put(key.group(1), block);
        continue;
      }

      if (line.isBlank()) {
        // A blank line detaches any pending comments from a following key/section.
        pendingComments.clear();
      } else if (line.strip().startsWith("#")) {
        pendingComments.add(line);
      } else {
        pendingComments.clear();
      }
    }
    return defaults;
  }

  /** Splits text into lines on {@code \n}, stripping any trailing {@code \r} so CRLF files match. */
  private static List<String> splitLines(String text) {
    String[] raw = text.split("\n", -1);
    List<String> lines = new ArrayList<>(raw.length);
    for (String line : raw) {
      lines.add(line.endsWith("\r") ? line.substring(0, line.length() - 1) : line);
    }
    return lines;
  }

  private static int bracketDelta(String line) {
    int delta = 0;
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (c == '[') {
        delta++;
      } else if (c == ']') {
        delta--;
      }
    }
    return delta;
  }

  /** Ordered index of the shipped default sections. */
  private static final class Defaults {
    final Map<String, Section> sections = new LinkedHashMap<>();
  }

  /** A single shipped section: its header block and ordered key blocks. */
  private static final class Section {
    final List<String> headerBlock = new ArrayList<>();
    final Map<String, List<String>> keyBlocks = new LinkedHashMap<>();
  }
}
