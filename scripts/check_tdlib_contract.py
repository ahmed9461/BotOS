#!/usr/bin/env python3
"""Validate the pinned source contract and actual 64-bit ELF headers before device tests."""
from pathlib import Path
import hashlib
import json
import re
import struct

ROOT = Path(__file__).resolve().parents[1]
SCHEMA_SHA = '326b65b41442901ad6bf0ca2f7c356ae54365d6c343956a62e06a8b3cb305e87'
JAVA_SHA = 'e92666f288e599d1c55bf5d24774f5e3e890b0999b0629931a7cf627a4323510'
expected = json.loads((ROOT / 'native/dependencies.lock.json').read_text())
java = ROOT / 'core/tdlib/src/main/java/org/drinkless/tdlib/JsonClient.java'
assert hashlib.sha256(java.read_bytes()).hexdigest() == JAVA_SHA, 'Official Java binding changed'
for abi in expected['abis']:
    root = ROOT / 'native/output' / abi
    assert json.loads((root / 'metadata/dependencies.lock.json').read_text()) == expected, 'Native input lock mismatch'
    for line in (root / 'SHA256SUMS.txt').read_text().splitlines():
        digest, name = line.split(None, 1)
        target = (root / name).resolve()
        assert target.is_relative_to(root.resolve()), 'Unsafe checksum entry'
        assert hashlib.sha256(target.read_bytes()).hexdigest() == digest, f'Checksum mismatch: {abi}/{name}'
    schema = (root / 'metadata/td_api.tl').read_bytes()
    assert hashlib.sha256(schema).hexdigest() == SCHEMA_SHA, 'TDLib schema changed'
    assert (root / 'metadata/JsonClient.java').read_bytes() == java.read_bytes(), 'JNI Java source mismatch'
    text = schema.decode()
    for method in ['getAuthorizationState', 'setTdlibParameters', 'setAuthenticationPhoneNumber',
                   'checkAuthenticationCode', 'checkAuthenticationPassword', 'setAuthenticationEmailAddress',
                   'checkAuthenticationEmailCode', 'logOut', 'close', 'getOption', 'testCallString']:
        assert re.search(r'^' + method + r'(?: |\s*=)', text, re.M), f'Unknown TDLib method: {method}'
    data = (root / f'jniLibs/{abi}/libtdjsonjava.so').read_bytes()
    assert data[:6] == b'\x7fELF\x02\x01', 'Expected a 64-bit little-endian Android ELF'
    machine = struct.unpack_from('<H', data, 18)[0]
    assert machine == {'arm64-v8a': 183, 'x86_64': 62}[abi], 'Wrong native ABI'
    offset = struct.unpack_from('<Q', data, 32)[0]
    size, count = struct.unpack_from('<HH', data, 54)
    loads = []
    for index in range(count):
        entry = offset + size * index
        if struct.unpack_from('<I', data, entry)[0] == 1:
            loads.append(struct.unpack_from('<Q', data, entry + 48)[0])
    assert loads and all(align >= 16384 for align in loads), '16KB ELF alignment failed'
    print(f'{abi}: pinned schema, official Java, binary checksums and ELF alignment passed')
print('This is source/binary validation, not an account login or a 16KB runtime test.')
