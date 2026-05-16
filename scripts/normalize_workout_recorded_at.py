#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import shutil
import sys
from datetime import datetime, time
from pathlib import Path
from zoneinfo import ZoneInfo


BEIJING_TZ = ZoneInfo("Asia/Shanghai")
DEFAULT_PATH = Path(__file__).resolve().parents[1] / "stack" / "data" / "workout_sessions.json"


def parse_recorded_at(value: str) -> datetime:
    try:
        parsed = datetime.fromisoformat(value)
    except ValueError as exc:
        raise ValueError(f"invalid recorded_at: {value}") from exc
    if parsed.tzinfo is None:
        return parsed.replace(tzinfo=BEIJING_TZ)
    return parsed.astimezone(BEIJING_TZ)


def normalize_to_time(value: str, target_time: time) -> str:
    parsed = parse_recorded_at(value)
    normalized = datetime.combine(parsed.date(), target_time, tzinfo=BEIJING_TZ)
    return normalized.isoformat()


def load_sessions(path: Path) -> list[dict]:
    with path.open("r", encoding="utf-8") as file:
        data = json.load(file)
    if not isinstance(data, list):
        raise ValueError(f"{path} must contain a JSON array")
    return data


def save_sessions(path: Path, sessions: list[dict]) -> None:
    temp_path = path.with_suffix(path.suffix + ".tmp")
    with temp_path.open("w", encoding="utf-8") as file:
        json.dump(sessions, file, ensure_ascii=False, indent=2)
        file.write("\n")
    temp_path.replace(path)


def parse_hhmm(value: str) -> time:
    try:
        parsed = datetime.strptime(value, "%H:%M")
    except ValueError as exc:
        raise argparse.ArgumentTypeError("time must use HH:MM format, for example 22:00") from exc
    return time(hour=parsed.hour, minute=parsed.minute)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Normalize workout session recorded_at to a fixed Beijing time on the original date.",
    )
    parser.add_argument(
        "--file",
        type=Path,
        default=DEFAULT_PATH,
        help=f"workout_sessions.json path, default: {DEFAULT_PATH}",
    )
    parser.add_argument(
        "--time",
        type=parse_hhmm,
        default=time(hour=22),
        help="target Beijing time in HH:MM format, default: 22:00",
    )
    parser.add_argument(
        "--write",
        action="store_true",
        help="write changes to the file. Without this flag, only prints a preview.",
    )
    parser.add_argument(
        "--no-backup",
        action="store_true",
        help="do not create a .bak file when writing.",
    )
    return parser


def main() -> int:
    args = build_parser().parse_args()
    path = args.file.expanduser().resolve()
    sessions = load_sessions(path)

    changed = 0
    preview: list[tuple[str, str, str]] = []
    for session in sessions:
        session_id = str(session.get("id", ""))
        old_value = session.get("recorded_at")
        if not isinstance(old_value, str) or not old_value.strip():
            raise ValueError(f"session {session_id or '<missing id>'} has no valid recorded_at")
        new_value = normalize_to_time(old_value, args.time)
        if old_value != new_value:
            changed += 1
            preview.append((session_id, old_value, new_value))
            session["recorded_at"] = new_value

    for session_id, old_value, new_value in preview[:20]:
        print(f"{session_id}: {old_value} -> {new_value}")
    if len(preview) > 20:
        print(f"... {len(preview) - 20} more changes")

    if not args.write:
        print(f"Dry run: {changed} session(s) would be changed. Add --write to update {path}.")
        return 0

    if changed == 0:
        print("No changes needed.")
        return 0

    if not args.no_backup:
        backup_path = path.with_suffix(path.suffix + ".bak")
        shutil.copy2(path, backup_path)
        print(f"Backup written: {backup_path}")

    save_sessions(path, sessions)
    print(f"Updated {changed} session(s): {path}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"Error: {exc}", file=sys.stderr)
        raise SystemExit(1)
