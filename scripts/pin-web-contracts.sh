#!/usr/bin/env bash
# Copies the web app's contract files at one commit into
# src/test/resources/contracts and records the commit in PINNED.
#
#   scripts/pin-web-contracts.sh <web-commit>   copy the files at that commit
#   scripts/pin-web-contracts.sh --check        confirm the copy still matches
#                                               the commit recorded in PINNED
#
# The web app writes the files and lists each one's SHA-256 in its manifest;
# the tests here read only the copy, so they never need the network.
set -euo pipefail

repository=Nubles/OSRS-Fate-Locked
source_dir=contracts/golden-bundles
# The relay's replies to the plugin's request, with the outcome of each.
relay_fixture=contracts/relay/relay-get.json
root=$(cd "$(dirname "$0")/.." && pwd)
dest="$root/src/test/resources/contracts"
pinned="$dest/PINNED"

download() {
  # $1 commit, $2 path in the web repository, $3 local file. The contents API
  # with the raw media type returns the file's exact bytes; GITHUB_TOKEN, when
  # set (as in CI), lifts the anonymous rate limit.
  local auth=()
  if [ -n "${GITHUB_TOKEN:-}" ]; then
    auth=(-H "Authorization: Bearer $GITHUB_TOKEN")
  fi
  curl --fail --silent --show-error --location --retry 3 "${auth[@]}" \
    -H 'Accept: application/vnd.github.raw' --output "$3" \
    "https://api.github.com/repos/$repository/contents/$2?ref=$1"
}

# Copies every file the manifest lists at $1 into $2, checking each SHA-256.
fetch_contracts() {
  local commit=$1 out=$2
  mkdir -p "$out/golden-bundles"
  download "$commit" "$source_dir/manifest.json" "$out/golden-bundles/manifest.json"
  local names
  names=$(sed -n '/"files"/,/}/p' "$out/golden-bundles/manifest.json" \
    | sed -n 's/^ *"\([^"]*\)": "\([0-9a-f]\{64\}\)".*/\1 \2/p')
  if [ -z "$names" ]; then
    echo "no files listed in the manifest at $commit" >&2
    exit 1
  fi
  while read -r name sum; do
    download "$commit" "$source_dir/$name" "$out/golden-bundles/$name"
    echo "$sum  $out/golden-bundles/$name" | sha256sum --check --quiet -
  done <<< "$names"
  mkdir -p "$out/relay"
  download "$commit" "$relay_fixture" "$out/relay/relay-get.json"
}

if [ "${1:-}" = "--check" ]; then
  commit=$(sed -n 's/^commit=//p' "$pinned")
  scratch=$(mktemp -d)
  trap 'rm -rf "$scratch"' EXIT
  fetch_contracts "$commit" "$scratch"
  if diff -r "$scratch/golden-bundles" "$dest/golden-bundles" > /dev/null \
    && diff -r "$scratch/relay" "$dest/relay" > /dev/null; then
    echo "contracts match $repository@$commit"
  else
    echo "src/test/resources/contracts differs from $repository@$commit;" \
      "re-run scripts/pin-web-contracts.sh instead of editing the copy" >&2
    exit 1
  fi
  exit 0
fi

commit=${1:?usage: scripts/pin-web-contracts.sh <web-commit> | --check}
if ! [[ "$commit" =~ ^[0-9a-f]{40}$ ]]; then
  echo "give the full 40-character web commit hash" >&2
  exit 1
fi
rm -rf "$dest/golden-bundles" "$dest/relay"
fetch_contracts "$commit" "$dest"
printf 'repository=%s\ncommit=%s\n' "$repository" "$commit" > "$pinned"
echo "copied $source_dir and $relay_fixture from $repository@$commit"
