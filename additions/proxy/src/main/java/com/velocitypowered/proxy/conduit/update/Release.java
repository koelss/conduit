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

/**
 * A single published release, as returned by an {@link UpdateProvider}.
 *
 * <p>This is deliberately provider-agnostic: it captures only the parsed {@link SemanticVersion},
 * the original tag, a human-facing URL, and whether the upstream marked it as a pre-release. This
 * lets {@link UpdateChecker} reason about releases without knowing where they came from.</p>
 *
 * @param version    the parsed semantic version of the release
 * @param tag        the original release tag (e.g. {@code v1.3.5})
 * @param url        a browser-facing URL for the release, or {@code null} if none was provided
 * @param prerelease whether the provider flagged this release as a pre-release
 */
public record Release(SemanticVersion version, String tag, String url, boolean prerelease) {
}
