#!/usr/bin/env python3
"""
FlashChores maintenance CLI — for the operator, on the host's terminal.

Why this exists
---------------
The privacy notice promises people can have their data erased on request. Almost every
such request is self-service (the admin uses Admin -> Danger zone in the app), but as
operator you occasionally need to do it yourself: a lost admin PIN, a legal escalation, or
clearing out abandoned sign-ups.

This script is the safe way to do that. It does NOT talk SQL to the database and it does
NOT expose an admin endpoint on the internet. It starts the app's own code in a one-shot
"maintenance" mode with no web server, so deleting a home goes through exactly the same
cascade the in-app Danger zone uses. Nothing new listens on a port.

Because H2 locks the database file exclusively, the service must be STOPPED first. The
script checks and refuses rather than failing halfway.

Usage
-----
    ./flashchores-admin.py list
    ./flashchores-admin.py show K7QP4ZT
    ./flashchores-admin.py export K7QP4ZT
    ./flashchores-admin.py delete K7QP4ZT
    ./flashchores-admin.py purge --days 30 --dry-run
    ./flashchores-admin.py purge --days 30

`delete` exports a JSON backup first (unless --no-backup) and asks you to type the home
code, mirroring the confirmation the app itself requires. Keep those exports: they are
your evidence the request was honoured, and your undo if someone changes their mind.

Locating the app
----------------
By default the script looks for the runnable jar in ../target. Override with --jar, or
set FLASHCHORES_JAR. For a dev checkout without a built jar, pass --classpath with a
file containing a Java classpath (see --help).
"""

from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import socket
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

JSON_BEGIN = "---FLASHCHORES-JSON-BEGIN---"
JSON_END = "---FLASHCHORES-JSON-END---"

HERE = Path(__file__).resolve().parent
PROJECT_ROOT = HERE.parent
DEFAULT_BACKUP_DIR = PROJECT_ROOT / "data" / "erasure-exports"
DEFAULT_DB_PORT = 8080


class Failure(Exception):
    """An expected, explainable problem — printed without a traceback."""


# ---------------------------------------------------------------------------- locating


def find_jar(explicit: str | None) -> Path:
    if explicit:
        jar = Path(explicit).expanduser()
        if not jar.is_file():
            raise Failure(f"No jar at {jar}")
        return jar
    env = os.environ.get("FLASHCHORES_JAR")
    if env:
        jar = Path(env).expanduser()
        if not jar.is_file():
            raise Failure(f"FLASHCHORES_JAR points at a missing file: {jar}")
        return jar
    candidates = sorted(
        (p for p in (PROJECT_ROOT / "target").glob("*.jar")
         if not p.name.endswith(("-sources.jar", "-javadoc.jar"))),
        key=lambda p: p.stat().st_mtime,
        reverse=True,
    )
    if not candidates:
        raise Failure(
            "Could not find the app jar. Build it with:\n"
            "  mvn clean package -Pproduction\n"
            "or pass --jar /path/to/flashchores.jar (or set FLASHCHORES_JAR)."
        )
    return candidates[0]


def java_executable() -> str:
    java_home = os.environ.get("JAVA_HOME")
    if java_home:
        candidate = Path(java_home) / "bin" / "java"
        if candidate.is_file():
            return str(candidate)
    found = shutil.which("java")
    if not found:
        raise Failure("No `java` on PATH and JAVA_HOME is not set.")
    return found


# ---------------------------------------------------------------------------- safety


def app_appears_to_be_running(port: int) -> bool:
    """H2 gives the running app an exclusive file lock, so we must not race it."""
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.settimeout(0.4)
        return sock.connect_ex(("127.0.0.1", port)) == 0


def ensure_app_stopped(port: int, skip: bool) -> None:
    if skip or not app_appears_to_be_running(port):
        return
    raise Failure(
        f"Something is still serving on 127.0.0.1:{port}, so FlashChores is probably\n"
        "running and holding the database file. Stop it first, for example:\n"
        "  sudo systemctl stop flashchores\n"
        "then re-run this command (and start it again afterwards).\n"
        "If that port is something else entirely, pass --port or --force."
    )


# ---------------------------------------------------------------------------- invoking


def run_maintenance(args, params: dict[str, str]) -> dict:
    """Runs the app once in maintenance mode and returns its parsed JSON result."""
    cmd = [java_executable()]
    if args.classpath:
        classpath = Path(args.classpath).expanduser()
        if not classpath.is_file():
            raise Failure(f"No classpath file at {classpath}")
        cmd += ["-cp", classpath.read_text().strip(), "com.homechores.Application"]
    else:
        cmd += ["-jar", str(find_jar(args.jar))]

    cmd += [
        # An ephemeral, loopback-only port: Vaadin's Spring integration insists on a web
        # context, so rather than fight it we start one nobody can reach and shut it down
        # as soon as the command has run.
        "--server.port=0",
        "--server.address=127.0.0.1",
        "--logging.level.root=WARN",
        "--spring.main.banner-mode=off",
    ]
    if args.db_url:
        cmd.append(f"--spring.datasource.url={args.db_url}")
    cmd += [f"--{key}={value}" for key, value in params.items()]

    if args.verbose:
        print("$ " + " ".join(cmd), file=sys.stderr)

    completed = subprocess.run(
        cmd, cwd=str(PROJECT_ROOT), capture_output=True, text=True, check=False
    )
    payload = extract_json(completed.stdout)
    if payload is None:
        raise Failure(
            "The maintenance run produced no result.\n"
            f"exit code: {completed.returncode}\n"
            f"--- stdout ---\n{completed.stdout.strip()[-2500:]}\n"
            f"--- stderr ---\n{completed.stderr.strip()[-2500:]}"
        )
    if not payload.get("ok"):
        raise Failure(payload.get("error", "the maintenance command failed"))
    return payload


def extract_json(stdout: str) -> dict | None:
    match = re.search(
        re.escape(JSON_BEGIN) + r"\s*(.*?)\s*" + re.escape(JSON_END), stdout, re.DOTALL
    )
    if not match:
        return None
    try:
        return json.loads(match.group(1))
    except json.JSONDecodeError:
        return None


# ---------------------------------------------------------------------------- output


def age_days(iso: str) -> str:
    try:
        when = datetime.fromisoformat(iso.replace("Z", "+00:00"))
    except ValueError:
        return "?"
    if when.tzinfo is None:
        when = when.replace(tzinfo=timezone.utc)
    return f"{(datetime.now(timezone.utc) - when).days}d"


def print_home_table(homes: list[dict]) -> None:
    if not homes:
        print("No homes.")
        return
    header = f"{'CODE':<9} {'MEMBERS':>7} {'HISTORY':>7} {'IDLE':>6}  NAME"
    print(header)
    print("-" * len(header))
    for home in homes:
        print(
            f"{home['code']:<9} {home['members']:>7} "
            f"{'yes' if home['hasHistory'] else 'no':>7} "
            f"{age_days(home['lastActive']):>6}  {home['name']}"
        )
    print(f"\n{len(homes)} home(s). IDLE is time since the last real use.")


def confirm_code(expected: str, what: str) -> None:
    print(f"\nType the home code to confirm {what}, or anything else to abort.")
    try:
        typed = input(f"  code [{expected}]: ").strip().upper()
    except (EOFError, KeyboardInterrupt):
        raise Failure("Aborted.")
    if typed != expected.upper():
        raise Failure("Codes did not match — nothing was changed.")


# ---------------------------------------------------------------------------- commands


def cmd_list(args) -> int:
    print_home_table(run_maintenance(args, {"maintenance.command": "list"})["homes"])
    return 0


def cmd_show(args) -> int:
    home = run_maintenance(
        args, {"maintenance.command": "show", "maintenance.code": args.code}
    )
    print(f"{home['name']}  ({home['code']})")
    print(f"  created      {home['created']}")
    print(f"  last used    {home['lastActive']}  ({age_days(home['lastActive'])} ago)"
          + ("" if home["lastActiveRecorded"] else "   [never recorded; showing creation]"))
    print(f"  chores       {home['chores']}")
    print(f"  history      {'yes' if home['hasHistory'] else 'no completions at all'}")
    print(f"  members      {home['members']}")
    for member in home.get("memberDetails", []):
        print(f"    - {member['name']}{' (admin)' if member['admin'] else ''}"
              f"  {member['approvedChores']} chores")
    return 0


def cmd_export(args) -> int:
    out = Path(args.out) if args.out else default_export_path(args.code)
    result = run_maintenance(args, {
        "maintenance.command": "export",
        "maintenance.code": args.code,
        "maintenance.out": str(out),
    })
    print(f"Exported {result['code']} -> {result['exportedTo']} ({result['bytes']} bytes)")
    return 0


def default_export_path(code: str) -> Path:
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    return DEFAULT_BACKUP_DIR / f"{code.upper()}-{stamp}.json"


def cmd_delete(args) -> int:
    code = args.code.strip().upper()
    home = run_maintenance(args, {"maintenance.command": "show", "maintenance.code": code})

    print(f"\nAbout to permanently delete:")
    print(f"  {home['name']}  ({home['code']})")
    print(f"  {home['members']} member(s), {home['chores']} chore(s), "
          f"history: {'yes' if home['hasHistory'] else 'none'}")
    print(f"  last used {age_days(home['lastActive'])} ago")
    print("\nThis removes members, chores, completions, credits and settings. No undo.")

    if not args.no_backup:
        out = Path(args.out) if args.out else default_export_path(code)
        result = run_maintenance(args, {
            "maintenance.command": "export",
            "maintenance.code": code,
            "maintenance.out": str(out),
        })
        print(f"\nBacked up first: {result['exportedTo']}")
    else:
        print("\n--no-backup given: nothing will be recoverable.")

    if not args.yes:
        confirm_code(code, f"deletion of {home['name']}")

    result = run_maintenance(args, {"maintenance.command": "delete", "maintenance.code": code})
    if result["deleted"]:
        print(f"\nDeleted \"{result['name']}\" ({result['code']}) and all its data.")
        return 0
    print(f"\nNothing deleted — {code} was already gone.")
    return 1


def cmd_purge(args) -> int:
    params = {
        "maintenance.command": "purge",
        "maintenance.days": str(args.days),
        "maintenance.dry-run": "true" if args.dry_run else "false",
    }
    result = run_maintenance(args, params)
    if args.dry_run:
        candidates = result["candidates"]
        print(f"Dry run — homes abandoned for {args.days}+ days "
              f"(no chore history, at most one member):\n")
        print_home_table(candidates)
        if candidates:
            print("\nRe-run without --dry-run to delete these.")
        return 0
    purged = result["purged"]
    print(f"Purged {len(purged)} abandoned home(s): {', '.join(purged) if purged else '(none)'}")
    return 0


# ---------------------------------------------------------------------------- wiring


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="flashchores-admin",
        description="Operator maintenance for FlashChores. Run on the host, with the "
                    "service stopped (H2 locks the database file).",
        epilog="Deleting goes through the app's own code, so the cascade matches the "
               "in-app Danger zone exactly.",
    )
    parser.add_argument("--jar", help="path to the runnable jar (default: newest in ./target)")
    parser.add_argument("--classpath", help="dev alternative: file containing a Java classpath")
    parser.add_argument("--port", type=int, default=DEFAULT_DB_PORT,
                        help=f"port to check for a running app (default {DEFAULT_DB_PORT})")
    parser.add_argument("--db-url", dest="db_url",
                        help="operate on a different database, e.g. a restored copy "
                             "(default: the one in application.properties)")
    parser.add_argument("--force", action="store_true",
                        help="skip the 'is the app stopped?' check")
    parser.add_argument("-v", "--verbose", action="store_true", help="print the java command")

    sub = parser.add_subparsers(dest="command", required=True)

    p_list = sub.add_parser("list", help="list every home, most idle first")
    p_list.set_defaults(func=cmd_list)

    p_show = sub.add_parser("show", help="details for one home")
    p_show.add_argument("code")
    p_show.set_defaults(func=cmd_show)

    p_export = sub.add_parser("export", help="write a home's JSON backup")
    p_export.add_argument("code")
    p_export.add_argument("--out", help=f"output file (default: {DEFAULT_BACKUP_DIR}/CODE-<ts>.json)")
    p_export.set_defaults(func=cmd_export)

    p_delete = sub.add_parser("delete", help="erase a home and all its data")
    p_delete.add_argument("code")
    p_delete.add_argument("--out", help="where to write the backup taken first")
    p_delete.add_argument("--no-backup", action="store_true",
                          help="do not export before deleting (not recommended)")
    p_delete.add_argument("--yes", action="store_true",
                          help="skip the typed-code confirmation (for scripts)")
    p_delete.set_defaults(func=cmd_delete)

    p_purge = sub.add_parser("purge", help="delete homes abandoned before anyone used them")
    p_purge.add_argument("--days", type=int, required=True,
                         help="idle days before an unused, empty home qualifies")
    p_purge.add_argument("--dry-run", action="store_true", help="list candidates only")
    p_purge.set_defaults(func=cmd_purge)

    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        ensure_app_stopped(args.port, args.force)
        return args.func(args)
    except Failure as failure:
        print(f"\n{failure}", file=sys.stderr)
        return 2
    except KeyboardInterrupt:
        print("\nAborted.", file=sys.stderr)
        return 130


if __name__ == "__main__":
    sys.exit(main())
