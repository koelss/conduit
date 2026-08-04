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

import java.io.IOException;
import java.util.List;

/**
 * A source of published releases against which the running build can be compared.
 *
 * <p>The update system is intentionally provider-based so a different backend (a self-hosted
 * update endpoint, a Modrinth project, a Maven metadata file, …) can be added later by
 * implementing this interface and handing it to {@link UpdateChecker} — no other code needs to
 * change. The GitHub Releases implementation is {@link GitHubReleaseProvider}.</p>
 */
public interface UpdateProvider {

  /**
   * A short, stable identifier for this provider (e.g. {@code github}). Used only for logging.
   *
   * @return the provider identifier
   */
  String id();

  /**
   * Fetches the list of published releases. Draft releases must be excluded; pre-releases must be
   * included with {@link Release#prerelease()} set so the caller can decide whether to honour them.
   *
   * <p>Implementations should apply sensible network timeouts and must not block indefinitely. The
   * returned list may be in any order — {@link UpdateChecker} sorts it.</p>
   *
   * @return the published releases that could be parsed; never {@code null}
   * @throws IOException if the releases could not be retrieved (network error, rate limit, …)
   */
  List<Release> fetchReleases() throws IOException;
}
