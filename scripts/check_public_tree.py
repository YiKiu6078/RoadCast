#!/usr/bin/env python3
"""Fail CI if credential containers or private-key blocks enter the source tree."""
from pathlib import Path
import re
import subprocess
import zipfile

root = Path(__file__).resolve().parents[1]
names = subprocess.check_output(['git', 'ls-files', '-z'], cwd=root).decode().split('\0')
blocked_suffixes = {'.pk8', '.p7b', '.pem', '.key', '.p12', '.pfx', '.jks', '.keystore', '.apk', '.aab'}
private_block = re.compile(rb'-----BEGIN (?:[A-Z0-9 ]+ )?PRIVATE KEY-----\s+[A-Za-z0-9+/=\r\n]{40,}')
credential_name = re.compile(r'\.(?:pk8|p7b|pem|key|p12|pfx|jks|keystore)(?:\.|$)', re.I)
failures = []
for name in filter(None, names):
    path = root / name
    if not path.is_file():
        continue
    if '.private' in path.relative_to(root).parts or path.suffix.lower() in blocked_suffixes or credential_name.search(path.name):
        failures.append(name)
    elif private_block.search(path.read_bytes()):
        failures.append(name)
    elif zipfile.is_zipfile(path):
        with zipfile.ZipFile(path) as archive:
            for entry in archive.infolist():
                entry_path = Path(entry.filename)
                if 'offline-mfi' in entry_path.parts or entry_path.suffix.lower() in blocked_suffixes or credential_name.search(entry_path.name):
                    failures.append(name + ' (contains credential/distribution entry)')
                    break
                if entry.file_size > 20 * 1024 * 1024:
                    failures.append(name + ' (archive entry exceeds review limit)')
                    break
                if not entry.is_dir() and private_block.search(archive.read(entry)):
                    failures.append(name + ' (contains private-key block)')
                    break
if failures:
    raise SystemExit('Credential or distribution files are forbidden in the public tree:\n' + '\n'.join(failures))
print('Public tree check passed: no credential containers or private-key blocks.')
