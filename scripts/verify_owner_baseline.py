#!/usr/bin/env python3
"""Fail before secret injection if the archived owner baseline is not 0.4."""
from pathlib import Path
import sys

from verify_inplace_update import APP_ID, single_apk


if __name__ == '__main__':
    if len(sys.argv) != 2:
        raise SystemExit('Expected one ownerPreview APK output folder')
    single_apk(Path(sys.argv[1]), APP_ID, (4, '0.4.0-preview'))
    print('owner_baseline_0.4=verified')
