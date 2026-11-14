#!/usr/bin/env python3
"""Uploads a release's jars to Modrinth and CurseForge.

Run by .github/workflows/publish.yml when a GitHub release is published, with the two sites' tokens in the
MODRINTH_TOKEN and CURSEFORGE_TOKEN environment variables. A site whose token isn't set is skipped.

    scripts/publish.py --tag v1.5.0 --jars jars/ --changelog-file notes.md [--only 26.3] [--sites modrinth] [--dry-run]

Which jar is listed under which game versions comes from publish_versions in versions/<mc>.properties.
--dry-run uploads nothing: it prints what would be sent and, where a token is set, checks that the token works and
that the site knows every game version.

Needs only Python 3; no packages.
"""

import argparse
import json
import os
import sys
import urllib.error
import urllib.request
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
USER_AGENT = "Kyle-15/TweakSwitch-publish (https://github.com/Kyle-15/TweakSwitch)"

MODRINTH_API = "https://api.modrinth.com/v2"
MODRINTH_PROJECT = "tweakswitch"
# Modrinth names dependencies by project id
MODRINTH_DEPENDENCIES = [
    {"project_id": "P7dR8mSH", "dependency_type": "required"},  # Fabric API
    {"project_id": "mOgUt4GM", "dependency_type": "optional"},  # Mod Menu
]

CURSEFORGE_API = "https://minecraft.curseforge.com/api"
CURSEFORGE_PROJECT = 1785794
# CurseForge names them by the last part of their page address
CURSEFORGE_RELATIONS = [
    {"slug": "fabric-api", "type": "requiredDependency"},
    {"slug": "modmenu", "type": "optionalDependency"},
]

# The release notes end with sections that only make sense on GitHub; the changelog is everything before this heading
CHANGELOG_ENDS_AT = "### Which jar do I need?"


class PublishError(Exception):
    """Something a person has to fix: a missing jar, a refused token, a game version the site doesn't know."""


def read_properties(path):
    values = {}
    for line in path.read_text().splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip()
    return values


def families(versions_dir):
    """{"26.1": {"game_versions": ["26.1", "26.1.1", "26.1.2"], "java": "25"}, ...} in upload order, oldest first."""
    found = {}
    for path in versions_dir.glob("*.properties"):
        properties = read_properties(path)
        if properties.get("publish_versions"):
            found[path.name[: -len(".properties")]] = {
                "game_versions": [version.strip() for version in properties["publish_versions"].split(",") if version.strip()],
                "java": properties.get("java_version", ""),
            }
    return dict(sorted(found.items(), key=lambda item: version_key(item[0])))


def version_key(version):
    return [int(part) if part.isdigit() else 0 for part in version.split(".")]


def changelog_from(notes):
    """The release notes up to the GitHub-only sections."""
    cut = notes.find(CHANGELOG_ENDS_AT)
    return (notes if cut < 0 else notes[:cut]).strip()


def request(url, headers=None, data=None, content_type=None):
    """Sends a request and returns the parsed JSON reply. Raises PublishError with the site's own message on failure."""
    all_headers = {"User-Agent": USER_AGENT, "Accept": "application/json"}
    all_headers.update(headers or {})
    if content_type:
        all_headers["Content-Type"] = content_type
    try:
        with urllib.request.urlopen(urllib.request.Request(url, data=data, headers=all_headers), timeout=180) as reply:
            body = reply.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", "replace")[:500]
        raise PublishError(f"{url} answered HTTP {error.code}: {detail}") from None
    except urllib.error.URLError as error:
        raise PublishError(f"Couldn't reach {url}: {error.reason}") from None
    return json.loads(body) if body.strip() else None


def multipart(fields, file_field, file_name, file_bytes):
    """Encodes text fields and one file the way a web form upload does. Returns (body, content type)."""
    boundary = "----tweakswitch" + uuid.uuid4().hex
    parts = []
    for name, value in fields.items():
        parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"\r\n\r\n'.encode() + value.encode("utf-8") + b"\r\n")
    parts.append(
        f'--{boundary}\r\nContent-Disposition: form-data; name="{file_field}"; filename="{file_name}"\r\n'
        f"Content-Type: application/java-archive\r\n\r\n".encode() + file_bytes + b"\r\n"
    )
    parts.append(f"--{boundary}--\r\n".encode())
    return b"".join(parts), f"multipart/form-data; boundary={boundary}"


# ---- Modrinth

def modrinth_payload(project_id, mod_version, mc, family, changelog):
    return {
        "name": f"TweakSwitch {mod_version}",
        "version_number": f"{mod_version}+{mc}",
        "changelog": changelog,
        "dependencies": MODRINTH_DEPENDENCIES,
        "game_versions": family["game_versions"],
        "version_type": "release",
        "loaders": ["fabric"],
        "featured": False,
        "status": "listed",
        "project_id": project_id,
        "file_parts": ["file"],
        "primary_file": "file",
    }


def publish_modrinth(token, mod_version, jobs, changelog, dry_run):
    print("\n== Modrinth")
    project_id = MODRINTH_PROJECT
    existing = set()
    if token:
        auth = {"Authorization": token}
        project = request(f"{MODRINTH_API}/project/{MODRINTH_PROJECT}", auth)
        project_id = project["id"]
        print(f"Project {project['title']} ({project_id}), status: {project.get('status')}")
        existing = {version["version_number"] for version in request(f"{MODRINTH_API}/project/{project_id}/version", auth)}
        known = {tag["version"] for tag in request(f"{MODRINTH_API}/tag/game_version")}
        for mc, family, _ in jobs:
            unknown = [version for version in family["game_versions"] if version not in known]
            if unknown:
                raise PublishError(f"Modrinth doesn't know game version(s) {', '.join(unknown)} (jar +{mc})")
    else:
        print("No MODRINTH_TOKEN: showing what would be sent, without checking it against the site.")

    uploaded = []
    for mc, family, jar in jobs:
        payload = modrinth_payload(project_id, mod_version, mc, family, changelog)
        number = payload["version_number"]
        if number in existing:
            print(f"- {number}: already on Modrinth, skipped")
            continue
        if dry_run:
            print(f"- {number}: would upload {jar.name} for {', '.join(family['game_versions'])}")
            continue
        body, content_type = multipart({"data": json.dumps(payload)}, "file", jar.name, jar.read_bytes())
        created = request(f"{MODRINTH_API}/version", {"Authorization": token}, body, content_type)
        print(f"- {number}: uploaded {jar.name} (version id {created['id']})")
        uploaded.append(number)
    return uploaded


# ---- CurseForge

def curseforge_version_ids(token, family):
    """The site's numeric ids for the jar's game versions, Fabric, Client and its Java version."""
    auth = {"X-Api-Token": token}
    types = {entry["id"]: entry["slug"] for entry in request(f"{CURSEFORGE_API}/game/version-types", auth)}
    versions = request(f"{CURSEFORGE_API}/game/versions", auth)

    def find(name, type_matches):
        for version in versions:
            if version["name"] == name and type_matches(types.get(version["gameVersionTypeID"], "")):
                return version["id"]
        return None

    ids = []
    missing = []
    # The same number can also be listed for Bukkit plugins; only the "minecraft-…" lists are for mods
    for game_version in family["game_versions"]:
        found = find(game_version, lambda slug: slug.startswith("minecraft"))
        (ids if found is not None else missing).append(found if found is not None else game_version)
    if missing:
        raise PublishError(f"CurseForge doesn't know game version(s) {', '.join(missing)}")
    fabric = find("Fabric", lambda slug: slug == "modloader")
    if fabric is None:
        raise PublishError("CurseForge's version list has no Fabric mod loader entry")
    ids.append(fabric)
    # Nice to have: the file still works without them
    for name, slug in (("Client", "environment"), (f"Java {family['java']}", "java")):
        found = find(name, lambda candidate, wanted=slug: candidate == wanted)
        if found is None:
            print(f"  (CurseForge has no \"{name}\" tag; left out)")
        else:
            ids.append(found)
    return ids


def publish_curseforge(token, mod_version, jobs, changelog, dry_run):
    print("\n== CurseForge")
    if not token:
        print("No CURSEFORGE_TOKEN: showing what would be sent, without checking it against the site.")
    uploaded = []
    for mc, family, jar in jobs:
        name = f"TweakSwitch {mod_version}+{mc}"
        version_ids = curseforge_version_ids(token, family) if token else []
        if dry_run:
            checked = f", version ids {version_ids}" if token else ""
            print(f"- {name}: would upload {jar.name} for {', '.join(family['game_versions'])}{checked}")
            continue
        metadata = {
            "changelog": changelog,
            "changelogType": "markdown",
            "displayName": name,
            "gameVersions": version_ids,
            "releaseType": "release",
            "relations": {"projects": CURSEFORGE_RELATIONS},
        }
        body, content_type = multipart({"metadata": json.dumps(metadata)}, "file", jar.name, jar.read_bytes())
        created = request(f"{CURSEFORGE_API}/projects/{CURSEFORGE_PROJECT}/upload-file", {"X-Api-Token": token}, body, content_type)
        print(f"- {name}: uploaded {jar.name} (file id {created['id']})")
        uploaded.append(name)
    return uploaded


# ---- Command line

def main(argv=None):
    parser = argparse.ArgumentParser(description="Upload a TweakSwitch release's jars to Modrinth and CurseForge.")
    parser.add_argument("--tag", required=True, help="the release's tag, like v1.5.0")
    parser.add_argument("--jars", required=True, type=Path, help="folder holding the release's jars")
    parser.add_argument("--changelog-file", type=Path, help="the release notes; everything before the \"Which jar\" heading is the changelog")
    parser.add_argument("--only", default="", help="upload just this jar, named by its suffix (26.3, 1.21.10, ...)")
    parser.add_argument("--sites", default="modrinth,curseforge", help="which sites, comma-separated")
    parser.add_argument("--dry-run", action="store_true", help="upload nothing; print what would be sent")
    parser.add_argument("--versions-dir", type=Path, default=ROOT / "versions", help=argparse.SUPPRESS)
    args = parser.parse_args(argv)

    mod_version = args.tag[1:] if args.tag.startswith("v") else args.tag
    sites = [site.strip().lower() for site in args.sites.split(",") if site.strip()]
    unknown_sites = [site for site in sites if site not in ("modrinth", "curseforge")]
    try:
        if unknown_sites:
            raise PublishError(f"Unknown site(s): {', '.join(unknown_sites)}")
        all_families = families(args.versions_dir)
        if args.only and args.only not in all_families:
            raise PublishError(f"No jar +{args.only}; there are: {', '.join(all_families)}")
        jobs = []
        for mc, family in all_families.items():
            if args.only and mc != args.only:
                continue
            jar = args.jars / f"tweakswitch-{mod_version}+{mc}.jar"
            if not jar.is_file():
                raise PublishError(f"{jar} is missing: the release needs its jars attached before they can be uploaded")
            jobs.append((mc, family, jar))
        notes = args.changelog_file.read_text() if args.changelog_file else ""
        changelog = changelog_from(notes)
        if not changelog and not args.dry_run:
            raise PublishError("The release has no notes to use as the changelog")

        print(f"TweakSwitch {mod_version}{' (dry run: nothing is uploaded)' if args.dry_run else ''}")
        print(f"Changelog: {len(changelog.splitlines())} lines, starting \"{changelog.splitlines()[0] if changelog else ''}\"")
        tokens = {"modrinth": os.environ.get("MODRINTH_TOKEN", "").strip(), "curseforge": os.environ.get("CURSEFORGE_TOKEN", "").strip()}
        publishers = {"modrinth": publish_modrinth, "curseforge": publish_curseforge}
        skipped = []
        for site in sites:
            if not tokens[site] and not args.dry_run:
                print(f"\n== {site.capitalize()}\nNo {site.upper()}_TOKEN secret is set: skipped.")
                skipped.append(site)
                continue
            publishers[site](tokens[site], mod_version, jobs, changelog, args.dry_run)
        if skipped:
            print(f"\nSkipped: {', '.join(skipped)}. Add the token as a repository secret to upload there.")
    except PublishError as error:
        print(f"\nStopped: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
