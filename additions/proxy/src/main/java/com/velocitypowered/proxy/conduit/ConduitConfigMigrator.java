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
import com.electronwill.nightconfig.core.io.WritingMode;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.electronwill.nightconfig.toml.TomlWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Keeps an existing {@code conduit.toml} forward-compatible with newer Conduit versions.
 *
 * <p>When Conduit adds a configuration option in a later release, operators should not have to
 * delete or regenerate their file to pick it up. On every load this migrator compares the user's
 * file against the defaults bundled in the jar and:
 *
 * <ul>
 *   <li><b>Adds missing keys and sections</b> with their default value <em>and</em> their
 *       explanatory comment, so a freshly-added option arrives fully documented.</li>
 *   <li><b>Never overwrites an existing value.</b> Anything the operator set — even a value
 *       equal to the default — is left exactly as-is.</li>
 *   <li><b>Preserves the user's comments and layout</b> where the TOML round-trip allows; only the
 *       new keys are appended into their section.</li>
 *   <li><b>Applies structural renames</b> declared in {@link #RENAMES} so an option that changes
 *       key/section between versions carries the operator's value over instead of resetting.</li>
 * </ul>
 *
 * <p>The file is rewritten only when something actually changed, so an already-current file is
 * never touched (its modification time is preserved and no spurious reformatting occurs).
 */
final class ConduitConfigMigrator {

  private static final Logger logger = LogManager.getLogger(ConduitConfigMigrator.class);

  private static final String DEFAULT_RESOURCE =
      "/com/velocitypowered/proxy/conduit/conduit.toml";

  /**
   * Dotted old-path → new-path renames applied before missing keys are filled in. Populate this
   * when an option is moved or renamed between versions; the operator's existing value is copied to
   * the new path and the old key removed. Empty today — the extension point for future structural
   * changes so a moved option migrates cleanly instead of silently resetting to defaults.
   */
  private static final Map<String, String> RENAMES = new LinkedHashMap<>();

  private ConduitConfigMigrator() {
  }

  /**
   * Tops up {@code file} with any options missing relative to the bundled defaults, applying any
   * declared renames first. Best-effort: on any error the file is left untouched and the load
   * proceeds against whatever is on disk.
   *
   * @param file the operator's {@code conduit.toml}
   */
  static void migrate(Path file) {
    CommentedConfig defaults = loadDefaults();
    if (defaults == null) {
      return;
    }

    try {
      CommentedConfig user;
      try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
        user = TomlFormat.instance().createParser().parse(reader);
      }

      List<String> renamed = applyRenames(user);
      List<String> added = new ArrayList<>();
      fillMissing(defaults, user, List.of(), added);

      if (added.isEmpty() && renamed.isEmpty()) {
        // Nothing to do — leave the file byte-for-byte untouched (no reformatting).
        return;
      }
      // Rewrite the whole file: existing keys, values, and comments are carried through the parse,
      // and only the missing options are added. This is the point where minor reformatting (indent,
      // comment placement) may occur — hence the early return above for the unchanged case.
      new TomlWriter().write(user, file, WritingMode.REPLACE, StandardCharsets.UTF_8);
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

  private static CommentedConfig loadDefaults() {
    try (InputStream in = ConduitConfigMigrator.class.getResourceAsStream(DEFAULT_RESOURCE)) {
      if (in == null) {
        logger.warn("[Conduit] Bundled conduit.toml defaults not found; skipping auto-update.");
        return null;
      }
      try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
        return TomlFormat.instance().createParser().parse(reader);
      }
    } catch (Exception e) {
      logger.warn("[Conduit] Failed to parse bundled conduit.toml defaults: {}", e.toString());
      return null;
    }
  }

  /**
   * Recursively copies keys present in {@code defaults} but absent from {@code user}, carrying each
   * new key's default value and its comment. Existing keys are never modified; where both sides
   * have a sub-table the merge descends into it so a new key inside an existing section is added
   * without disturbing the operator's other keys in that section.
   *
   * <p>All writes go through the root {@code user} config using the full path so that new sections
   * are created as native sub-tables (and thus written as {@code [section]} headers) rather than
   * inline tables. {@code level} is the current level of {@code defaults} being walked, used only
   * to read each key's default value and comment.
   */
  private static void fillMissing(CommentedConfig level, CommentedConfig user,
      List<String> prefix, List<String> added) {
    for (CommentedConfig.Entry entry : level.entrySet()) {
      String key = entry.getKey();
      List<String> here = List.of(key);
      List<String> path = concat(prefix, key);
      Object defaultValue = entry.getValue();
      Object userValue = user.get(path);
      String comment = level.getComment(here);

      if (defaultValue instanceof CommentedConfig defaultSub) {
        if (userValue == null) {
          // Create the section as a native sub-table so it is written as a [section] header, carry
          // its comment, then descend to add every key inside it.
          user.set(path, user.createSubConfig());
          if (comment != null) {
            user.setComment(path, comment);
          }
          fillMissing(defaultSub, user, path, added);
        } else if (userValue instanceof CommentedConfig) {
          fillMissing(defaultSub, user, path, added);
        }
        // A non-table value where a table is expected is an operator override; leave it alone.
      } else if (userValue == null) {
        user.set(path, defaultValue);
        if (comment != null) {
          user.setComment(path, comment);
        }
        added.add(String.join(".", path));
      }
    }
  }

  private static List<String> concat(List<String> prefix, String key) {
    List<String> path = new ArrayList<>(prefix.size() + 1);
    path.addAll(prefix);
    path.add(key);
    return path;
  }

  /**
   * Moves values for any {@link #RENAMES} whose old path still exists and whose new path does not
   * yet. Returns the list of {@code old → new} descriptions actually applied.
   */
  private static List<String> applyRenames(CommentedConfig user) {
    List<String> applied = new ArrayList<>();
    for (Map.Entry<String, String> rename : RENAMES.entrySet()) {
      List<String> oldPath = List.of(rename.getKey().split("\\."));
      List<String> newPath = List.of(rename.getValue().split("\\."));
      if (user.contains(oldPath) && !user.contains(newPath)) {
        user.set(newPath, user.get(oldPath));
        user.remove(oldPath);
        applied.add(rename.getKey() + " → " + rename.getValue());
      }
    }
    return applied;
  }
}
