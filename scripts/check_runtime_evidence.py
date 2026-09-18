#!/usr/bin/env python3
"""Verify real device results, then archive the original native report without rewriting it."""
from pathlib import Path
import json
import shutil
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
outputs = root / 'core/tdlib/build/outputs'
reports = list((outputs / 'androidTest-results').rglob('TEST-*.xml'))
suites = [ET.parse(p).getroot() for p in reports]
assert sum(int(x.get('tests', 0)) for x in suites) == 8, 'Expected eight native/storage tests'
assert all(int(x.get(k, 0)) == 0 for x in suites for k in ('failures', 'errors', 'skipped')), 'Native/storage test did not pass'
evidence = [p for p in outputs.rglob('native-runtime.txt') if 'additional_output' in str(p)]
assert len(evidence) == 1, 'Native evidence missing or ambiguous'
text = evidence[0].read_text()
fields = dict(line.split('=', 1) for line in text.splitlines() if '=' in line)
pin = json.loads((root / 'native/dependencies.lock.json').read_text())['tdlib']
assert fields.get('version') == pin['version'], 'Runtime version differs from the build lock'
assert fields.get('commit') == pin['commit'], 'Runtime source differs from the build lock'
assert fields.get('unicodeRoundTrip') == 'passed'
assert fields.get('authorization') == 'waitTdlibParameters'
assert fields.get('accountUsed') == 'false'
destination = root / 'diagnostics/native-runtime.txt'
destination.parent.mkdir(parents=True, exist_ok=True)
shutil.copyfile(evidence[0], destination)
assert destination.read_bytes() == evidence[0].read_bytes()
print(text)
print('Original native device report copied to the published diagnostics artifact.')
