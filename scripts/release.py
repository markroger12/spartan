#!/usr/bin/env python3
"""Prepare and verify development candidates using Python's standard library."""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import shutil
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
DOCS = ['README.md', 'INSTALLATION.md', 'CONFIGURATION.md', 'CHECKS.md', 'API.md',
        'BEDROCK.md', 'FOLIA.md', 'TROUBLESHOOTING.md', 'DEPENDENCIES.md',
        'ARCHITECTURE.md', 'PROJECT_TREE.md', 'VALIDATION.md', 'PHASE12.md',
        'COMPATIBILITY.md', 'UPGRADING.md', 'OPERATIONS.md', 'RELEASE.md', 'LICENSE']
DOCS += [f'PHASE{n}.md' for n in range(2, 12)]

def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()

def safe_name(name):
    p = PurePosixPath(name)
    if p.is_absolute() or '..' in p.parts or '\\' in name or not name or any(c.isspace() for c in name):
        raise ValueError('Unsafe release path')
    return p

def verify(directory):
    directory = Path(directory)
    if directory.is_symlink() or (directory / 'SHA256SUMS').is_symlink():
        raise ValueError('Release symlink rejected')
    rows = (directory / 'SHA256SUMS').read_text().splitlines()
    if not rows or len(rows) > 128:
        raise ValueError('Invalid checksum count')
    names = set()
    for row in rows:
        match = re.fullmatch(r'([0-9a-f]{64})  (.+)', row)
        if not match:
            raise ValueError('Invalid checksum line')
        sha, name = match.groups()
        parts = safe_name(name).parts
        target = directory
        for part in parts:
            target = target / part
            if target.is_symlink():
                raise ValueError('Release symlink rejected')
        if name in names or not target.is_file() or digest(target) != sha:
            raise ValueError('Missing, duplicated or damaged release file: ' + name)
        names.add(name)
    actual = {p.relative_to(directory).as_posix() for p in directory.rglob('*') if p.is_file()}
    if actual != names | {'SHA256SUMS'}:
        raise ValueError('Release file inventory mismatch')
    manifest = json.loads((directory / 'release-manifest.json').read_text())
    if manifest['stage'] != 'development-candidate' or manifest['status']['folia_enabled']:
        raise ValueError('Unsupported release certification')
    for name, sha in manifest['files'].items():
        if name not in names or digest(directory / name) != sha:
            raise ValueError('Manifest mismatch')
    return manifest

def check_docs():
    errors = []
    for name in DOCS:
        path = ROOT / name
        if not path.is_file():
            errors.append('Missing ' + name)
            continue
        for link in re.findall(r'\]\(([^)]+)\)', path.read_text()):
            target = link.strip('<>').split('#', 1)[0]
            if not target or re.match(r'[a-zA-Z]+:', target) or target.startswith('/'):
                continue
            if not (path.parent / target).exists():
                errors.append(f'{name}: missing link target {target}')
    if errors:
        raise ValueError('\n'.join(errors))

def prepare(output, allow_dirty=False):
    output = Path(output).absolute()
    if output.exists():
        raise ValueError('Choose a new output directory; existing candidates are never overwritten')
    check_docs()
    version = re.search(r'version = "([^"]+)"', (ROOT / 'build.gradle.kts').read_text())[1]
    dirty = bool(subprocess.check_output(['git', 'status', '--porcelain'], cwd=ROOT, text=True))
    if dirty and not allow_dirty:
        raise ValueError('Commit source first; --allow-dirty is for local validation only')
    commit = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    status = json.loads((ROOT / 'release/status.json').read_text())
    if status['stage'] != 'development-candidate' or status['folia_enabled']:
        raise ValueError('Candidate status must retain current acceptance gates')
    jar = ROOT / f'aegis-paper/build/libs/AegisAC-{version}.jar'
    tools = ROOT / f'aegis-tools/build/distributions/aegis-tools-{version}.zip'
    if not jar.is_file() or not tools.is_file():
        raise ValueError('Run a complete build before preparing a candidate')
    output.parent.mkdir(parents=True, exist_ok=True)
    stage = Path(tempfile.mkdtemp(prefix='.aegis-release-', dir=output.parent))
    try:
        shutil.copyfile(jar, stage / jar.name)
        shutil.copyfile(tools, stage / tools.name)
        with zipfile.ZipFile(jar) as archive:
            descriptor = archive.read('plugin.yml').decode()
            if f"version: '{version}'" not in descriptor or 'depend: [packetevents]' not in descriptor or 'folia-supported:' in descriptor:
                raise ValueError('Plugin descriptor violates release baseline')
            defaults = [n for n in archive.namelist() if n.startswith('defaults/') and n.endswith('.yml')]
            if len(defaults) != 24:
                raise ValueError('Missing packaged configuration')
            for name in defaults:
                safe_name(name)
                target = stage / name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(archive.read(name))
            for name in ['punishments.yml', 'setbacks.yml', 'webhooks.yml']:
                if not re.search(r'^enabled: false$', archive.read('defaults/' + name).decode(), re.M):
                    raise ValueError('Unsafe action/delivery default: ' + name)
        for name in DOCS:
            destination = stage / name
            shutil.copyfile(ROOT / name, destination)
            if name == 'README.md':
                destination.write_text(destination.read_text().replace('](downloads/README.md)', '](RELEASE.md)'))
        # Include local documentation link targets (reports, phase contracts and measurement evidence).
        shutil.copytree(ROOT / 'docs', stage / 'docs')
        files = {p.relative_to(stage).as_posix(): digest(p) for p in sorted(stage.rglob('*')) if p.is_file()}
        manifest = dict(schema=1, project='AegisAC', version=version, stage=status['stage'],
                        source_commit=commit, source_dirty=dirty, status=status, files=files,
                        provenance='Unsigned local preparation; verify GitHub Sigstore provenance separately.')
        (stage / 'release-manifest.json').write_text(json.dumps(manifest, indent=2, sort_keys=True) + '\n')
        all_files = sorted(p for p in stage.rglob('*') if p.is_file())
        (stage / 'SHA256SUMS').write_text(''.join(f'{digest(p)}  {p.relative_to(stage).as_posix()}\n' for p in all_files))
        verify(stage)
        stage.rename(output)
    finally:
        if stage.exists():
            shutil.rmtree(stage)
    return output

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='command', required=True)
    p = sub.add_parser('prepare'); p.add_argument('output'); p.add_argument('--allow-dirty', action='store_true')
    p = sub.add_parser('verify'); p.add_argument('directory')
    sub.add_parser('check-docs')
    args = parser.parse_args()
    if args.command == 'prepare':
        print(prepare(args.output, args.allow_dirty))
    elif args.command == 'verify':
        manifest = verify(args.directory)
        print(f"Verified {manifest['version']} candidate, source {manifest['source_commit']}, dirty={manifest['source_dirty']}")
    else:
        check_docs(); print('Release documentation links checked')

if __name__ == '__main__':
    main()
