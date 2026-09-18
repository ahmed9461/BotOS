#!/usr/bin/env python3
"""Validate or verify injected build settings. Never print secret values, lengths or hashes."""
from __future__ import annotations

import argparse
from collections.abc import Mapping
from pathlib import Path
import re
import sys
import os

ID_KEY = "BOTOS_TELEGRAM_API_ID"
HASH_KEY = "BOTOS_TELEGRAM_API_HASH"
ERROR = "Telegram application configuration is missing, incomplete or invalid."


def is_configured(values: Mapping[str, str], *, required: bool = False) -> bool:
    api_id, api_hash = values.get(ID_KEY, ""), values.get(HASH_KEY, "")
    if not api_id and not api_hash:
        if required:
            raise ValueError(ERROR)
        return False
    if (not re.fullmatch(r"[1-9][0-9]{0,9}", api_id)
            or not 0 < int(api_id) <= 2_147_483_647
            or not re.fullmatch(r"[0-9a-fA-F]{32}", api_hash)):
        raise ValueError(ERROR)
    return True


def verify_generated(directory: Path, values: Mapping[str, str], *, required: bool = False) -> None:
    configured = is_configured(values, required=required)
    files = list(directory.rglob("BuildConfig.java"))
    if len(files) != 1:
        raise ValueError("Expected exactly one generated application BuildConfig.")
    text = files[0].read_text(encoding="utf-8")
    expected = {
        "TELEGRAM_CONFIGURED": "true" if configured else "false",
        "TELEGRAM_API_ID": str(int(values[ID_KEY])) if configured else "0",
        "TELEGRAM_API_HASH": '"' + (values[HASH_KEY] if configured else "") + '"',
    }
    for name, value in expected.items():
        matches = re.findall(r"\b" + name + r"\s*=\s*([^;]+);", text)
        if len(matches) != 1 or matches[0].strip() != value:
            raise ValueError("Generated application configuration does not match the injected pair.")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--require", action="store_true")
    parser.add_argument("--verify-generated", type=Path)
    options = parser.parse_args()
    try:
        configured = is_configured(os.environ, required=options.require)
        if options.verify_generated is not None:
            verify_generated(options.verify_generated, os.environ, required=options.require)
    except (ValueError, OSError, UnicodeError):
        # No exception repr/traceback: file content and environment may include secrets.
        print("Telegram build configuration check failed. Check the configured pair and build setup.", file=sys.stderr)
        return 1
    print("telegram_build_config=" + ("configured" if configured else "disabled"))
    if options.verify_generated is not None:
        print("generated_configuration=verified")
    print("telegram_authentication=not_tested")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
