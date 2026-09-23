#!/usr/bin/env python3
"""Validate an exact tested source and seal owner-only artifacts. Never print secret values."""
from pathlib import Path
import argparse
import hashlib
import json
import math
import os
import re
import subprocess
import sys
import tempfile
import urllib.request
import zipfile
import xml.etree.ElementTree as ET

REPO = 'ahmed9461/BotOS'
ROOT = Path(__file__).resolve().parents[1]


def validate_request(request: dict, run: dict, changed: list[str]) -> dict[str, str]:
    if request.get('enabled') is not True:
        if request != {'enabled': False}:
            raise ValueError('Invalid disabled delivery request')
        return {'enabled': 'false'}
    source = request.get('source_commit', '')
    run_id = request.get('integration_run')
    if not isinstance(source, str) or not re.fullmatch('[0-9a-f]{40}', source):
        raise ValueError('Delivery source must be an exact commit')
    if type(run_id) is not int or run_id <= 0:
        raise ValueError('Delivery requires an integration run')
    if (run.get('id') != run_id or run.get('head_sha') != source or
            run.get('status') != 'completed' or run.get('conclusion') != 'success' or
            run.get('event') != 'pull_request' or run.get('path') != '.github/workflows/android.yml' or
            run.get('head_repository', {}).get('full_name') != REPO or
            run.get('head_branch') != 'feat/media-profiles-streaming'):
        raise ValueError('Source lacks a matching successful trusted integration run')
    if type(run.get('run_number')) is not int or run['run_number'] <= 0:
        raise ValueError('Missing integration artifact number')
    if any(p != 'delivery/request.json' and not p.startswith('docs/') for p in changed):
        raise ValueError('Untested changes follow the requested source')
    return {'enabled': 'true', 'source': source, 'run_id': str(run_id), 'run_number': str(run['run_number'])}


def verify_request(path: Path) -> dict[str, str]:
    request = json.loads(path.read_text(encoding='utf-8'))
    if request.get('enabled') is not True:
        return validate_request(request, {}, [])
    source = request.get('source_commit', '')
    run_id = request.get('integration_run')
    if not re.fullmatch('[0-9a-f]{40}', str(source)) or type(run_id) is not int or run_id <= 0:
        raise ValueError('Invalid delivery reference')
    subprocess.run(['git', 'merge-base', '--is-ancestor', source, 'HEAD'], cwd=ROOT, check=True, capture_output=True)
    changed = subprocess.run(['git', 'diff', '--name-only', source, 'HEAD'], cwd=ROOT,
                             check=True, capture_output=True, text=True).stdout.splitlines()
    url = f'https://api.github.com/repos/{REPO}/actions/runs/{run_id}'
    req = urllib.request.Request(url, headers={'Accept': 'application/vnd.github+json',
        'Authorization': f'Bearer {os.environ["GH_TOKEN"]}', 'X-GitHub-Api-Version': '2022-11-28'})
    with urllib.request.urlopen(req, timeout=30) as response:
        run = json.load(response)
    return validate_request(request, run, changed)


def seal(source: Path, recipient: Path, destination: Path) -> None:
    if not source.is_file() or not 0 < source.stat().st_size <= 512 * 1024 * 1024:
        raise ValueError('Missing or oversized delivery payload')
    if not recipient.is_file() or 'PRIVATE KEY' in recipient.read_text(encoding='ascii'):
        raise ValueError('Delivery recipient must contain only a public certificate')
    if destination.exists() or source.resolve() == destination.resolve():
        raise ValueError('Refusing to overwrite a delivery file')
    destination.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp_name = tempfile.mkstemp(prefix='.sealed-', dir=destination.parent)
    os.close(fd)
    tmp = Path(tmp_name)
    try:
        subprocess.run(['openssl', 'cms', '-encrypt', '-binary', '-aes-256-gcm',
                        '-in', str(source), '-outform', 'DER', '-out', str(tmp),
                        '-recip', str(recipient), '-keyopt', 'rsa_padding_mode:oaep',
                        '-keyopt', 'rsa_oaep_md:sha256', '-keyopt', 'rsa_mgf1_md:sha256'],
                       check=True, capture_output=True, timeout=120)
        if tmp.stat().st_size <= source.stat().st_size:
            raise ValueError('Incomplete encrypted delivery')
        tmp.replace(destination)
    finally:
        tmp.unlink(missing_ok=True)


def validate_startup_report(path: Path) -> None:
    """AGP emits testsuites > testsuite > testcase; JUnit can also emit a flat suite."""
    root = ET.parse(path).getroot()
    if root.tag not in {'testsuite', 'testsuites'}:
        raise ValueError('Unsupported startup report root')
    for node in root.iter():
        # Interrupted instrumentation can write zero failures and a negative unfinished time.
        if node.tag in {'testsuite', 'testsuites', 'testcase'} and 'time' in node.attrib:
            elapsed = float(node.attrib['time'])
            if not math.isfinite(elapsed) or elapsed < 0:
                raise ValueError('Startup report contains an unfinished or invalid duration')
        if node.tag in {'failure', 'error', 'skipped'}:
            raise ValueError('Startup report contains a failed or skipped result')
        if node.tag in {'testsuite', 'testsuites'}:
            if int(node.get('tests', '-1')) != 1 or any(
                    int(node.get(key, '0')) != 0 for key in ['failures', 'errors', 'skipped']):
                raise ValueError('Startup report has conflicting result counts')
    cases = list(root.iter('testcase'))
    if len(cases) != 1 or cases[0].get('classname') != 'com.ahmed9461.botos.OwnerBuildSmokeTest' or \
            cases[0].get('name') != 'startupRequiresConsentBeforeCreatingAnySession':
        raise ValueError('Wrong startup test executed')


def locate_owner_apk(outputs: Path) -> Path:
    """Read the AGP-produced filename; never assume a variant's APK naming convention."""
    folder = outputs / 'apk/ownerPreview'
    metadata = json.loads((folder / 'output-metadata.json').read_text(encoding='utf-8'))
    if (metadata.get('applicationId') != 'com.ahmed9461.botos.app' or
            metadata.get('variantName') != 'ownerPreview' or
            metadata.get('artifactType', {}).get('type') != 'APK'):
        raise ValueError('Unexpected owner APK metadata')
    elements = metadata.get('elements', [])
    if len(elements) != 1 or elements[0].get('type') != 'SINGLE' or elements[0].get('filters') != []:
        raise ValueError('A single universal owner APK is required')
    if elements[0].get('versionCode') != 5 or elements[0].get('versionName') != '0.5.0-preview':
        raise ValueError('Owner APK version must advance to 0.5.0-preview')
    filename = elements[0].get('outputFile', '')
    if not isinstance(filename, str) or not filename.endswith('.apk') or Path(filename).name != filename or '\\' in filename:
        raise ValueError('Invalid owner APK output path')
    apk = folder / filename
    if apk.resolve().parent != folder.resolve() or not apk.is_file():
        raise ValueError('Missing or escaping owner APK')
    return apk


def validate_update_report(path: Path) -> None:
    expected = {
        'application_id': 'com.ahmed9461.botos.app', 'in_place_upgrade': 'passed',
        'from_version': '0.4.0-preview', 'to_version': '0.5.0-preview',
        'private_file_preserved': True, 'preferences_preserved': True,
        'uninstalled_between_installs': False, 'same_version_reinstall': False,
        'signer': 'temporary CI', 'owner_final_signer_device_test': False, 'account_used': False,
    }
    if json.loads(path.read_text(encoding='utf-8')) != expected:
        raise ValueError('Update-persistence evidence failed')


def package_owner(destination: Path, build_tools: Path) -> None:
    """Explicit file allowlist. No source trees, BuildConfig, logs, preferences or signing keys."""
    print('owner_check=startup-report')
    reports = list((ROOT / 'app/build/outputs/androidTest-results/connected').glob('**/TEST-*.xml'))
    if len(reports) != 1:
        raise ValueError('Exactly one configured startup report is required')
    validate_startup_report(reports[0])
    print('owner_check=startup-evidence')
    files = list((ROOT / 'app/build/outputs').glob('**/owner-startup.txt'))
    if not files or len({p.read_bytes() for p in files}) != 1:
        raise ValueError('Missing or conflicting original startup evidence')
    evidence = dict(line.split('=', 1) for line in files[0].read_text().splitlines())
    expected = {'configured': 'true', 'applicationId': 'com.ahmed9461.botos.app',
                'debuggable': 'false', 'consentGate': 'true', 'sessionCreated': 'false', 'accountUsed': 'false'}
    if evidence != expected:
        raise ValueError('Owner startup safety contract failed')
    print('owner_check=update-persistence')
    update_report = ROOT / 'diagnostics/owner-update.json'
    validate_update_report(update_report)
    print('owner_check=apk-metadata')
    apk = locate_owner_apk(ROOT / 'app/build/outputs')
    print('owner_check=apk-signature')
    subprocess.run([str(build_tools / 'apksigner'), 'verify', '--verbose', str(apk)],
                   check=True, capture_output=True, timeout=60)
    print('owner_check=apk-alignment')
    subprocess.run([str(build_tools / 'zipalign'), '-c', '-P', '16', '4', str(apk)],
                   check=True, capture_output=True, timeout=60)
    print('owner_check=apk-content')
    with zipfile.ZipFile(apk) as archive:
        if archive.testzip() is not None:
            raise ValueError('APK is corrupt')
        names = set(archive.namelist())
        if not all(f'lib/{abi}/libtdjsonjava.so' in names for abi in ['arm64-v8a', 'x86_64']):
            raise ValueError('Owner APK is missing a native architecture')
    print('owner_check=payload-allowlist')
    signer_tool = build_tools / 'lib/apksigner.jar'
    manifest = {'source_commit': os.environ['BOTOS_TESTED_SOURCE'], 'integration_run': os.environ['BOTOS_INTEGRATION_RUN'],
                'delivery_run': os.environ['GITHUB_RUN_ID'], 'configuration': 'verified', 'account_used': False,
                'application_id': expected['applicationId'], 'debuggable': False, 'version': '0.5.0-preview',
                'apk_sha256_before_owner_signing': hashlib.sha256(apk.read_bytes()).hexdigest(),
                'apksigner_sha256': hashlib.sha256(signer_tool.read_bytes()).hexdigest(),
                'signing': 'temporary CI certificate; final owner signing is required'}
    destination.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(destination, 'x', compression=zipfile.ZIP_DEFLATED) as archive:
        archive.write(apk, 'BotOS-0.5.0-preview-ci.apk')
        archive.write(signer_tool, 'tools/apksigner.jar')
        archive.write(files[0], 'owner-startup.txt')
        archive.write(reports[0], 'owner-startup.xml')
        archive.write(update_report, 'owner-update.json')
        archive.writestr('provenance.json', json.dumps(manifest, indent=2) + '\n')


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='command', required=True)
    verify = sub.add_parser('verify-request'); verify.add_argument('request', type=Path)
    encrypt = sub.add_parser('seal')
    encrypt.add_argument('payload', type=Path); encrypt.add_argument('recipient', type=Path); encrypt.add_argument('destination', type=Path)
    package = sub.add_parser('package')
    package.add_argument('destination', type=Path); package.add_argument('build_tools', type=Path)
    args = parser.parse_args()
    try:
        if args.command == 'verify-request':
            outputs = verify_request(args.request)
            with open(os.environ['GITHUB_OUTPUT'], 'a', encoding='utf-8') as target:
                for key, value in outputs.items(): target.write(f'{key}={value}\n')
            print('owner_request=verified')
        elif args.command == 'seal':
            seal(args.payload, args.recipient, args.destination)
            digest = hashlib.sha256(args.destination.read_bytes()).hexdigest()
            args.destination.with_suffix('.sha256').write_text(f'{digest}  {args.destination.name}\n')
            print('owner_delivery=encrypted')
        else:
            package_owner(args.destination, args.build_tools)
            print('owner_payload=verified')
    except Exception:
        # Neither subprocess output, environment values, nor exception repr are diagnostics here.
        print('Owner delivery validation failed; no plaintext artifact will be published.', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__': raise SystemExit(main())
