#!/usr/bin/env python3
"""Clean rotated log files older than N days from project log directories.

Two cleanup modes per directory:
- Rotated-file mode (manager-api / xiaozhi-server logs): delete files matching
  `*.<YYYY-MM-DD>.<N>.log` whose embedded date is older than the cutoff. Active
  files (api.log, app.log, error.log, manager-api.log) are skipped.
- Mtime mode (.sandcastle/logs): delete files and batch-ID subdirectories whose
  mtime is older than the cutoff. Batch state files (`batch-*.json`) are always
  kept, because loadBatch() in .sandcastle/main.mts reads them to resume
  in-flight batches — deleting them would silently restart a running batch.

Non-log files (.DS_Store, app.pid) and unknown directory layouts are skipped.
"""
from __future__ import annotations

import argparse
import re
import shutil
import sys
import time
from dataclasses import dataclass
from datetime import date, datetime, timedelta
from pathlib import Path

# 匹配 rotated 日志文件名：任意前缀 + .YYYY-MM-DD.N.log
# 例：manager-api.2026-10-20.0.log / error.2026-05-08.1.log
ROTATED_RE = re.compile(r"^.+\.(\d{4}-\d{2}-\d{2})\.\d+\.log$")

# Sandcastle batch 状态文件，永远跳过（loadBatch 用它 resume 未完成的批次）
BATCH_STATE_RE = re.compile(r"^batch-.+\.json$")

# Sandcastle batch 子目录名：<timestamp>-<sha-prefix>，对应 .sandcastle/logs/<batch-id>/
BATCH_DIR_RE = re.compile(r"^\d{10,}-[0-9a-f]{6,}$")

# rotated 模式：按文件名内嵌日期判断
ROTATED_DIRS = [
    "main/manager-api/logs",
    "main/xiaozhi-server/logs",
]

# mtime 模式：文件名没日期，按文件 mtime 判断
MTIME_DIRS = [
    ".sandcastle/logs",
]


@dataclass
class Candidate:
    path: Path
    log_date: date
    size: int
    is_dir: bool = False


def find_repo_root(start: Path) -> Path:
    """向上找含 .git 的目录作为 repo 根。"""
    current = start.resolve()
    for parent in [current, *current.parents]:
        if (parent / ".git").exists():
            return parent
    return current


def scan_rotated_directory(log_dir: Path, cutoff: date) -> tuple[list[Candidate], int, int]:
    """扫 rotated 模式目录，返回 (待删候选, 跳过文件数, 跳过字节数)。

    按文件名内嵌的 YYYY-MM-DD 判断新旧。只扫顶层，不递归。
    """
    candidates: list[Candidate] = []
    skipped_files = 0
    skipped_bytes = 0
    if not log_dir.is_dir():
        return candidates, skipped_files, skipped_bytes
    for entry in sorted(log_dir.iterdir()):
        if not entry.is_file():
            continue
        match = ROTATED_RE.match(entry.name)
        if not match:
            # 活跃日志 / .DS_Store / .pid 等跳过
            skipped_files += 1
            try:
                skipped_bytes += entry.stat().st_size
            except OSError:
                pass
            continue
        try:
            log_date = datetime.strptime(match.group(1), "%Y-%m-%d").date()
        except ValueError:
            skipped_files += 1
            continue
        if log_date < cutoff:
            try:
                size = entry.stat().st_size
            except OSError:
                size = 0
            candidates.append(Candidate(path=entry, log_date=log_date, size=size))
        else:
            skipped_files += 1
            try:
                skipped_bytes += entry.stat().st_size
            except OSError:
                pass
    return candidates, skipped_files, skipped_bytes


def _tree_size(path: Path) -> int:
    """递归算目录大小；文件直接 stat。"""
    if path.is_file():
        try:
            return path.stat().st_size
        except OSError:
            return 0
    total = 0
    for child in path.rglob("*"):
        if child.is_file():
            try:
                total += child.stat().st_size
            except OSError:
                pass
    return total


def scan_mtime_directory(log_dir: Path, cutoff_epoch: float) -> tuple[list[Candidate], int, int]:
    """扫 mtime 模式目录（.sandcastle/logs）。

    - batch-*.json 永远跳过（loadBatch 用它 resume）
    - 顶层文件按 mtime 过滤
    - 符合 <timestamp>-<sha> 形式的 batch 子目录整棵按目录 mtime 过滤
    - 其他子目录跳过不递归
    """
    candidates: list[Candidate] = []
    skipped_files = 0
    skipped_bytes = 0
    if not log_dir.is_dir():
        return candidates, skipped_files, skipped_bytes
    cutoff_date = date.fromtimestamp(cutoff_epoch)
    for entry in sorted(log_dir.iterdir()):
        if BATCH_STATE_RE.match(entry.name):
            skipped_files += 1
            try:
                skipped_bytes += entry.stat().st_size
            except OSError:
                pass
            continue
        try:
            mtime = entry.stat().st_mtime
        except OSError:
            continue
        if entry.is_file():
            if mtime < cutoff_epoch:
                size = entry.stat().st_size
                candidates.append(Candidate(
                    path=entry,
                    log_date=date.fromtimestamp(mtime),
                    size=size,
                ))
            else:
                skipped_files += 1
                skipped_bytes += entry.stat().st_size
        elif entry.is_dir() and BATCH_DIR_RE.match(entry.name):
            if mtime < cutoff_epoch:
                size = _tree_size(entry)
                candidates.append(Candidate(
                    path=entry,
                    log_date=date.fromtimestamp(mtime),
                    size=size,
                    is_dir=True,
                ))
            else:
                skipped_files += 1
                skipped_bytes += _tree_size(entry)
        # 其他目录（非 batch 命名）跳过，避免误删
    return candidates, skipped_files, skipped_bytes


def human_size(num: int) -> str:
    for unit in ("B", "KB", "MB", "GB"):
        if num < 1024 or unit == "GB":
            return f"{num:.1f}{unit}" if unit != "B" else f"{num}B"
        num /= 1024
    return f"{num:.1f}GB"


def main() -> int:
    parser = argparse.ArgumentParser(description="Clean rotated logs older than N days.")
    parser.add_argument("--days", type=int, default=7, help="Keep logs from last N days (default: 7)")
    parser.add_argument("--apply", action="store_true", help="Actually delete; default is dry-run")
    parser.add_argument(
        "--dir",
        action="append",
        dest="dirs",
        help="Extra rotated-mode directory to scan (relative to repo root). Can be repeated.",
    )
    args = parser.parse_args()

    if args.days < 1:
        print("error: --days must be >= 1", file=sys.stderr)
        return 2

    repo_root = find_repo_root(Path.cwd())
    rotated_dirs = list(ROTATED_DIRS) + (args.dirs or [])
    cutoff = date.today() - timedelta(days=args.days)
    cutoff_epoch = time.time() - args.days * 86400

    print(f"repo_root: {repo_root}")
    print(f"cutoff:    delete files older than {cutoff} (keep last {args.days} days)")
    print(f"mode:      {'APPLY (will delete)' if args.apply else 'DRY-RUN (no deletion)'}")
    print()

    total_candidates: list[Candidate] = []
    total_skipped_files = 0
    total_skipped_bytes = 0
    for rel in rotated_dirs:
        log_dir = repo_root / rel
        if not log_dir.is_dir():
            print(f"[skip] {rel} (directory not found)")
            continue
        candidates, skipped_files, skipped_bytes = scan_rotated_directory(log_dir, cutoff)
        total_candidates.extend(candidates)
        total_skipped_files += skipped_files
        total_skipped_bytes += skipped_bytes
        print(f"[scan:rotated] {rel}: {len(candidates)} to delete, {skipped_files} kept")
    for rel in MTIME_DIRS:
        log_dir = repo_root / rel
        if not log_dir.is_dir():
            print(f"[skip] {rel} (directory not found)")
            continue
        candidates, skipped_files, skipped_bytes = scan_mtime_directory(log_dir, cutoff_epoch)
        total_candidates.extend(candidates)
        total_skipped_files += skipped_files
        total_skipped_bytes += skipped_bytes
        print(f"[scan:mtime]   {rel}: {len(candidates)} to delete, {skipped_files} kept")

    if not total_candidates:
        print("\nnothing to delete.")
        return 0

    total_bytes = sum(c.size for c in total_candidates)
    noun = "entries" if any(c.is_dir for c in total_candidates) else "files"
    print(f"\n{'would delete' if not args.apply else 'deleting'} {len(total_candidates)} {noun}, "
          f"{human_size(total_bytes)}:")
    for c in total_candidates:
        rel = c.path.relative_to(repo_root)
        suffix = "/" if c.is_dir else ""
        print(f"  {c.log_date}  {human_size(c.size):>10}  {rel}{suffix}")

    if not args.apply:
        print("\ndry-run only; rerun with --apply to delete.")
        return 0

    failures: list[tuple[Path, str]] = []
    deleted_bytes = 0
    for c in total_candidates:
        try:
            if c.is_dir:
                shutil.rmtree(c.path)
            else:
                c.path.unlink()
            deleted_bytes += c.size
        except OSError as exc:
            failures.append((c.path, str(exc)))

    print(f"\ndeleted {len(total_candidates) - len(failures)} files, freed {human_size(deleted_bytes)}.")
    if failures:
        print("failures:", file=sys.stderr)
        for path, err in failures:
            print(f"  {path}: {err}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
