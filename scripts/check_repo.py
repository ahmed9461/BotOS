#!/usr/bin/env python3
"""Repository/traceability checks. Does not pretend to prove that a person read the plan."""
from pathlib import Path
import argparse
import re
import subprocess
import sys
import tomllib
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
required = ['AGENTS.md', 'docs/PROJECT_MEMORY.md', 'docs/ARCHITECTURE.md', 'docs/ROADMAP.md',
            'docs/UI_GUIDELINES.md', 'docs/DECISIONS.md', 'docs/DEPENDENCIES.md',
            'docs/SECURITY.md', 'docs/TELEGRAM_CAPABILITIES.md', 'docs/CHANGELOG.md', 'docs/tasks/0001-foundation.md']
parser = argparse.ArgumentParser()
parser.add_argument('--base', default='')
args = parser.parse_args()
errors = []
for name in required:
    if not (ROOT / name).is_file(): errors.append(f'Missing required file: {name}')
for xml in ROOT.glob('**/src/main/res/**/*.xml'):
    try: ET.parse(xml)
    except ET.ParseError as exc: errors.append(f'Invalid XML: {xml.relative_to(ROOT)}: {exc}')
versions = tomllib.loads((ROOT / 'gradle/libs.versions.toml').read_text())['versions']
for name, version in versions.items():
    if any(part in version.lower() for part in ['+', 'latest', 'snapshot', 'alpha', 'beta', 'rc']):
        errors.append(f'Unpinned or prerelease dependency: {name}')
kotlin_override = re.search(r'kotlin-gradle-plugin:([^"\s]+)', (ROOT / 'build.gradle.kts').read_text())
if not kotlin_override or kotlin_override.group(1) != versions['kotlin']: errors.append('KGP/Compose compiler version mismatch')
base = args.base
if base and set(base) != {'0'}:
    result = subprocess.run(['git', 'diff', '--name-only', f'{base}...HEAD'], cwd=ROOT, text=True, capture_output=True)
    if result.returncode:
        errors.append('Cannot resolve base commit for documentation check')
    else:
        changed = set(result.stdout.splitlines())
        if any(p.startswith(('app/', 'core/')) or p.endswith('.gradle.kts') for p in changed):
            for doc in ['docs/PROJECT_MEMORY.md', 'docs/CHANGELOG.md']:
                if doc not in changed: errors.append(f'Code changed without updating {doc}')
            if not any(p.startswith('docs/tasks/') and p.endswith('.md') for p in changed):
                errors.append('Code changed without an updated task plan')
# Accidental credentials are a separate review concern; these checks catch common literals only.
for source in list(ROOT.glob('**/src/**/*.kt')) + list(ROOT.glob('**/*.properties')):
    text = source.read_text()
    if re.search(r'\b[0-9]{8,12}:[A-Za-z0-9_-]{30,}\b', text): errors.append(f'Possible bot token in {source}')
if errors:
    print('\n'.join(errors), file=sys.stderr)
    sys.exit(1)
print('Repository structure, XML, pinned versions and documentation checks passed.')
