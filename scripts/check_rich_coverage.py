#!/usr/bin/env python3
"""Require one honest matrix row for every pinned TDLib RichText/PageBlock type."""
from pathlib import Path
import argparse
import hashlib
import re

ROOT = Path(__file__).resolve().parents[1]
SCHEMA_SHA = '326b65b41442901ad6bf0ca2f7c356ae54365d6c343956a62e06a8b3cb305e87'
STATUS = {'ضمن النطاق', 'جزئي', 'غير منفذ'}


def check(schema_path: Path, matrix_path: Path) -> tuple[int, int]:
    schema = schema_path.read_bytes()
    assert hashlib.sha256(schema).hexdigest() == SCHEMA_SHA, 'Rich matrix source is not the pinned TDLib schema'
    source = schema.decode('utf-8')
    matrix = matrix_path.read_text(encoding='utf-8')
    for family, pattern in (
        ('RichText', r'^richText\w+\b[^\n]*= RichText;$|^richTexts\b[^\n]*= RichText;$'),
        ('PageBlock', r'^pageBlock\w+\b[^\n]*= PageBlock;$'),
    ):
        expected = {re.match(r'\w+', line).group() for line in re.findall(pattern, source, re.M)}
        section = re.search(r'^## ' + family + r'\s*\n(.*?)(?=^## |\Z)', matrix, re.M | re.S)
        assert section, f'Missing {family} matrix section'
        rows = re.findall(r'^\| `([^`]+)` \| ([^|]+) \| ([^|]+) \|$', section.group(1), re.M)
        names = [name for name, _, _ in rows]
        assert len(names) == len(set(names)), f'Duplicate {family} matrix row'
        assert set(names) == expected, f'{family} missing={sorted(expected-set(names))}, extra={sorted(set(names)-expected)}'
        for name, status, detail in rows:
            assert status.strip() in STATUS, f'Unrecognized status: {name}'
            assert len(detail.strip()) >= 4, f'Missing practical limit/evidence: {name}'
        assert len(expected) == {'RichText': 30, 'PageBlock': 36}[family], f'Unexpected {family} schema count'
        print(f'{family}: {len(rows)} pinned types covered exactly')
    return 30, 36


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--schema', type=Path, default=ROOT / 'native/output/arm64-v8a/metadata/td_api.tl')
    args = parser.parse_args()
    check(args.schema, ROOT / 'docs/RICH_COVERAGE.md')
