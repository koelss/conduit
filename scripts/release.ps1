# release.ps1 — Tag the current release and let CI publish it.
#
# Reads conduit.version from gradle.properties, creates an annotated `v<version>`
# tag on origin/main, and pushes it. Pushing a `v*` tag triggers the
# "Publish release" step in .github/workflows/build.yml, which builds
# conduit-<version>.jar and creates the GitHub Release with generated notes.
#
# Usage (from a clone of the repo):
#   .\scripts\release.ps1              # tag v<conduit.version> on origin/main
#   .\scripts\release.ps1 -Version 1.4.0
#   .\scripts\release.ps1 -Ref main    # tag a different branch/commit
#
# Requires: git, and push access to origin.

[CmdletBinding()]
param(
    [string]$Version,
    [string]$Ref = "main",
    [string]$Remote = "origin"
)

$ErrorActionPreference = "Stop"

# Move to the repository root (parent of this script's directory).
$RepoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $RepoRoot

if (-not $Version) {
    $line = Select-String -Path "gradle.properties" -Pattern '^conduit\.version=' | Select-Object -First 1
    if (-not $line) { throw "conduit.version not found in gradle.properties" }
    $Version = ($line.Line -split '=', 2)[1].Trim()
}

$Tag = "v$Version"
Write-Host "==> Releasing $Tag from $Remote/$Ref"

# Make sure we tag exactly what is on the remote branch.
git fetch $Remote $Ref --tags
$Target = (git rev-parse "$Remote/$Ref").Trim()
Write-Host "    Target commit: $Target"

# Refuse to clobber an existing published tag.
$existing = git ls-remote --tags $Remote "refs/tags/$Tag"
if ($existing) { throw "Tag $Tag already exists on $Remote. Bump conduit.version or delete the tag first." }

git tag -a $Tag $Target -m "Conduit $Tag"
git push $Remote "refs/tags/$Tag"

Write-Host ""
Write-Host "==> Pushed $Tag. GitHub Actions will build conduit-$Version.jar and publish the release."
Write-Host "    Watch: https://github.com/tame-gg/conduit/actions"
Write-Host "    Release: https://github.com/tame-gg/conduit/releases/tag/$Tag"
