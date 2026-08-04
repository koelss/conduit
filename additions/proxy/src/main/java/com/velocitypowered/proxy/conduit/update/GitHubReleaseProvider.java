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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * An {@link UpdateProvider} backed by the public GitHub Releases REST API.
 *
 * <p>It reads {@code GET /repos/{owner}/{repo}/releases}, skipping drafts and any release whose tag
 * is not a parseable {@link SemanticVersion}. No authentication token is used, so requests count
 * against the unauthenticated per-IP rate limit; a {@code 403}/{@code 429} with an exhausted
 * {@code X-RateLimit-Remaining} is surfaced as a clear {@link IOException} that
 * {@link UpdateChecker} downgrades to a soft "unknown" result rather than a hard failure.</p>
 */
public final class GitHubReleaseProvider implements UpdateProvider {

  private static final int CONNECT_TIMEOUT_MS = 5000;
  private static final int READ_TIMEOUT_MS = 5000;
  private static final int MAX_PER_PAGE = 100;

  private final String repository;
  private final String userAgent;

  /**
   * Creates a provider for the given {@code owner/repo} slug.
   *
   * @param repository the GitHub repository in {@code owner/name} form (e.g. tame-gg/conduit)
   * @param userAgent  the {@code User-Agent} header to send (GitHub requires one)
   */
  public GitHubReleaseProvider(String repository, String userAgent) {
    this.repository = repository;
    this.userAgent = userAgent;
  }

  @Override
  public String id() {
    return "github";
  }

  @Override
  public List<Release> fetchReleases() throws IOException {
    String endpoint = "https://api.github.com/repos/" + repository
        + "/releases?per_page=" + MAX_PER_PAGE;

    HttpURLConnection connection = (HttpURLConnection) URI.create(endpoint).toURL()
        .openConnection();
    try {
      connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
      connection.setReadTimeout(READ_TIMEOUT_MS);
      connection.setRequestProperty("User-Agent", userAgent);
      connection.setRequestProperty("Accept", "application/vnd.github+json");
      connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
      connection.connect();

      int status = connection.getResponseCode();
      if (status == HttpURLConnection.HTTP_NOT_FOUND) {
        // Repository has no releases (or is private/unknown): treat as "nothing to compare".
        return List.of();
      }
      if (status == HttpURLConnection.HTTP_FORBIDDEN || status == 429) {
        String remaining = connection.getHeaderField("X-RateLimit-Remaining");
        if ("0".equals(remaining)) {
          throw new IOException("GitHub API rate limit exceeded; skipping update check");
        }
        throw new IOException("GitHub API returned HTTP " + status);
      }
      if (status != HttpURLConnection.HTTP_OK) {
        throw new IOException("GitHub API returned HTTP " + status);
      }

      try (BufferedReader reader = new BufferedReader(
          new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
        return parse(JsonParser.parseReader(reader));
      }
    } finally {
      connection.disconnect();
    }
  }

  private static List<Release> parse(JsonElement root) throws IOException {
    if (!root.isJsonArray()) {
      throw new IOException("Unexpected GitHub releases payload (not a JSON array)");
    }
    JsonArray array = root.getAsJsonArray();
    List<Release> releases = new ArrayList<>(array.size());
    for (JsonElement element : array) {
      if (!element.isJsonObject()) {
        continue;
      }
      JsonObject obj = element.getAsJsonObject();
      if (obj.has("draft") && obj.get("draft").getAsBoolean()) {
        continue; // never surface drafts
      }
      JsonElement tagElement = obj.get("tag_name");
      if (tagElement == null || tagElement.isJsonNull()) {
        continue;
      }
      String tag = tagElement.getAsString();
      Optional<SemanticVersion> version = SemanticVersion.parse(tag);
      if (version.isEmpty()) {
        continue; // ignore tags that are not semantic versions
      }
      boolean prerelease = obj.has("prerelease") && obj.get("prerelease").getAsBoolean();
      String url = obj.has("html_url") && !obj.get("html_url").isJsonNull()
          ? obj.get("html_url").getAsString() : null;
      releases.add(new Release(version.get(), tag, url, prerelease));
    }
    return releases;
  }
}
