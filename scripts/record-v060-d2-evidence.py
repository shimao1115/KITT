"""Record fresh D2 outputs. Fails closed on stale reports, failed checks or leaked local paths."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[1]
os.chdir(ROOT)
OUT = Path('artifacts/d2')
START = datetime.fromisoformat((OUT / 'run-start.txt').read_text(encoding='utf-8-sig').strip())
BASE = '76377555bf3e00be1060a1d03cdf3d4eda8be768'


def git(*args):
    return subprocess.check_output(['git', *args], encoding='utf-8').strip()


def load(path):
    return json.loads(Path(path).read_text(encoding='utf-8-sig'))


def info(path, fresh=True):
    p = Path(path)
    if fresh:
        assert p.stat().st_mtime >= START.timestamp(), f'stale: {path}'
    return dict(path=p.as_posix(), bytes=p.stat().st_size, sha256=hashlib.sha256(p.read_bytes()).hexdigest())


def suites(directory):
    rows = []
    for path in sorted(Path(directory).glob('TEST-*.xml')):
        root = ET.parse(path).getroot()
        counts = {k: int(root.attrib[k]) for k in ('tests', 'failures', 'errors', 'skipped')}
        assert counts['tests'] and not any(counts[k] for k in ('failures', 'errors', 'skipped')), path
        rows.append(dict(name=root.attrib['name'], **counts, report=info(path),
                         cases=[c.attrib['name'] for c in root.findall('testcase')]))
    assert rows
    return dict(tests=sum(r['tests'] for r in rows), failures=0, errors=0, skipped=0, suites=rows)


def scrub(value):
    if isinstance(value, dict):
        return {k: scrub(v) for k, v in value.items()}
    if isinstance(value, list):
        return [scrub(v) for v in value]
    if not isinstance(value, str):
        return value
    text = value.replace('\\', '/')
    for root, replacement in [(str(ROOT), '<WORKSPACE>'), (os.environ['TEMP'], '<TEMP>'),
                              (str(Path.home()), '<HOME>')]:
        text = re.sub(re.escape(root.replace('\\', '/')), replacement, text, flags=re.I)
    return text


def main():
    assert not git('status', '--porcelain'), 'commit the recorder before producing evidence'
    assert not git('diff', BASE, '--', 'app/src/main', 'content/yangshen'), 'protected production/content changed'
    tracked = git('ls-files').splitlines()
    assert not any(p.endswith(('.pem', '.jks', '.keystore', '.p12')) or p.startswith('artifacts/') for p in tracked)
    marker = '-----BEGIN ' + 'PRIVATE KEY-----'
    for name in tracked:
        assert marker.encode() not in Path(name).read_bytes(), f'private key marker in {name}'
    debug = suites('app/build/test-results/testDebugUnitTest')
    release = suites('app/build/test-results/testReleaseUnitTest')
    packtool = suites('packtool/build/test-results/test')
    for variant in (debug, release):
        d2 = next(s for s in variant['suites'] if s['name'] == 'com.kitt.reader.d1.ReleaseD2Test')
        assert d2['tests'] >= 14
        assert any(s['name'] == 'com.kitt.reader.d1.ReleaseD1Test' for s in variant['suites'])
    checks = load(OUT / 'checks.json')
    assert {r['name'] for r in checks} == {'debug', 'release', 'packtool', 'build-lint'}
    assert all(r['exit'] == 0 for r in checks)
    for check in checks:
        assert 'BUILD SUCCESSFUL' in (OUT / (check['name'] + '.log')).read_text(encoding='utf-8-sig')
        check['log'] = info(OUT / (check['name'] + '.log'))
    python = load(OUT / 'python-checks.json')
    assert python == dict(python_tests_exit=0, content_check_exit=0)
    assert 'Ran 7 tests' in (OUT / 'python-tests.log').read_text(encoding='utf-8-sig')
    assert '\nOK' in (OUT / 'python-tests.log').read_text(encoding='utf-8-sig')
    assert '0 eligible narratives' in (OUT / 'content-check.log').read_text(encoding='utf-8-sig')
    signing = load(OUT / 'signing.json')
    assert signing['accepted'] == 2 and signing['rejected'] == 6 and signing['exit'] == 0
    repro = load(OUT / 'reproducibility.json')
    assert repro['command'] == 'prepare' and repro['unsigned_bytes_identical'] and repro['signed_region_bytes_identical']
    assert repro['builder_project_is_worktree']
    assert len({row['unsigned_sha256'] for row in repro['clean_directories']}) == 1
    assert len({row['pack_sha256'] for row in repro['clean_directories']}) == 1
    assert all(row['container_sha256'] == row['pack_sha256'] for row in repro['clean_directories'])
    guard = load(OUT / 'repro-guard.json')
    assert guard['stopped'] and guard['sentinel_intact'] and not guard['created_directory_deleted']
    assert guard['original_worktrees_unchanged']
    cli = {}
    for path in sorted((OUT / 'cli').glob('*.log')):
        fields = {}
        for line in path.read_text(encoding='utf-8-sig').splitlines():
            match = re.fullmatch(r'([a-z_]+)=(.*)', line)
            if match:
                fields[match[1]] = match[2]
        assert 'status' in fields and 'exit_code' in fields, path
        cli[path.stem] = dict(**fields, log=info(path))
    for name, status in {'prepare': 'PREPARED_TEST_ONLY', 'preview': 'PREVIEW_TEST_ONLY',
                         'verify': 'ACCEPTED_TEST_ONLY', 'audit-newdu': 'BLOCKED'}.items():
        assert cli[name]['status'] == status
    assert cli['preview']['ready_to_publish'] == 'false' and cli['preview']['stories'] == '0'
    assert cli['preview-story']['stories'] == '2' and cli['preview-story']['ready_to_publish'] == 'false'
    for name, code in {'tamper-verify': 'bad_signature', 'wrong-county': 'expected_area',
                       'wrong-version': 'expected_factversion', 'rollback': 'sequence_rollback',
                       'unknown-key': 'unknown_key_id', 'production': 'production_not_authorized',
                       'relabel-verify': 'inner_binding'}.items():
        assert cli[name]['code'] == code and cli[name]['exit_code'] == '3'
    assert cli['audit-newdu']['sources_unclear'] == '2' and cli['audit-newdu']['published_stories'] == '0'
    key_metadata = load(OUT / 'key-public.json')
    assert key_metadata['scope'] == 'TEST_ONLY'
    simulations = [info(Path('app/build/acceptance', name)) for name in
                   ('full-simulation.txt', 'accelerated-simulation.txt', 'batch-simulation.txt',
                    'landmark-simulation.txt', 'dossier-simulation.txt', 'driving-area-resolution.txt')]
    evidence = dict(milestone='V0.6.0 D2', issue=40, base_commit_d1=BASE,
                    implementation_commit=git('log', '-1', '--format=%H', '--', 'packtool', 'app/src/test/java/com/kitt/reader/d1'),
                    evidence_source_commit=git('rev-parse', 'HEAD'), source_commit=repro['commit'],
                    run_started_at=START.isoformat(), recorded_at=datetime.now(timezone.utc).isoformat(),
                    tests=dict(debug=debug, release=release, packtool=packtool),
                    totals=dict(debug=debug['tests'], release=release['tests'], packtool=packtool['tests'],
                                all=debug['tests'] + release['tests'] + packtool['tests']),
                    checks=checks, python=dict(tests=7, test_log=info(OUT / 'python-tests.log'),
                                               content_log=info(OUT / 'content-check.log')),
                    key_material=dict(public_metadata=key_metadata, storage='temporary directory outside all Git worktrees',
                                      private_key_in_git=False, apk_key_reused_for_knowledge_packs=False),
                    simulations=simulations, cli=cli, reproducibility=repro, reproducibility_guard=guard,
                    signing_regression=signing, wrapper_checks=load(OUT / 'wrapper-checks.json'),
                    supporting_logs=[info(OUT / name) for name in ('signing.log', 'reproducibility.log',
                        'repro-guard.log', 'wrapper-success.log', 'wrapper-refusal.log')],
                    protected_files=[info(p, False) for parent in ('app/src/main/assets/offline', 'content/yangshen')
                                     for p in sorted(Path(parent).iterdir()) if p.is_file()],
                    production_app_and_content_unchanged=True, published_stories=0,
                    real_xindu_external_release='BLOCKED: 2 of 11 research sources have unclear rights',
                    production_signing_cli='NOT ENABLED; first identity and publication require separate authorization',
                    physical_two_computers='PENDING; two clean worktrees on one Windows computer only',
                    pending=['office/home actual cross-device comparison', 'user-selected signing computer and production identity',
                             'real Xindu rights and Yangshen Visitor approval', 'production signing/publication authorization and tooling enablement',
                             'phone JCA, pinned public key, download/import/atomic installation and persisted rollback floor (V0.6.1)'],
                    failed_attempts=[dict(stage='initial compilation', reason='JDK-only APIs unavailable on Android test compile classpath; fixed using compatible APIs', counted=False),
                                     dict(stage='first focused D2 run', tests=14, failures=1,
                                          reason='test helper duplicated --input; corrected helper to replace the option', counted=False)])
    assert evidence['wrapper_checks']['success'] and evidence['wrapper_checks']['refusal_propagated']
    text = json.dumps(scrub(evidence), ensure_ascii=False, indent=2) + '\n'
    assert not re.search(r'[A-Za-z]:[/\\]', text) and 'AppData' not in text, 'local path leakage'
    Path('docs/evidence/v060-d2-verification.json').write_text(text, encoding='utf-8')
    print(f'D2 evidence written: {evidence["totals"]["all"]} tests')


if __name__ == '__main__':
    main()
