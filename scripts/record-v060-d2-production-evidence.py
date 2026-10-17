"""Record only fresh Issue42 checks. No key bytes, local paths or inherited test totals."""
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
OUT = Path('artifacts/d2-production')
BASE = 'baea614ba02f1aad7776e65ed6766833ee451b3f'
START = datetime.fromisoformat((OUT / 'run-start.txt').read_text(encoding='utf-8-sig').strip())


def git(*args):
    return subprocess.check_output(['git', *args], encoding='utf-8').strip()


def load(path):
    return json.loads(Path(path).read_text(encoding='utf-8-sig'))


def info(path, fresh=True):
    p = Path(path)
    assert p.is_file(), path
    if fresh:
        assert p.stat().st_mtime >= START.timestamp(), f'stale {path}'
    return dict(path=p.as_posix(), bytes=p.stat().st_size, sha256=hashlib.sha256(p.read_bytes()).hexdigest())


def suites(directory):
    rows = []
    for path in sorted(Path(directory).glob('TEST-*.xml')):
        suite = ET.parse(path).getroot()
        counts = {k: int(suite.attrib[k]) for k in ('tests', 'failures', 'errors', 'skipped')}
        assert counts['tests'] > 0 and not any(counts[k] for k in ('failures', 'errors', 'skipped')), path
        row = dict(name=suite.attrib['name'], **counts, report=info(path))
        if row['name'].startswith('com.kitt.reader.d1.') or row['name'] == 'com.kitt.packtool.ReleaseFormatTest':
            row['cases'] = [c.attrib['name'] for c in suite.findall('testcase')]
        rows.append(row)
    assert rows
    return dict(tests=sum(r['tests'] for r in rows), failures=0, errors=0, skipped=0, suites=rows)


def scrub(value):
    if isinstance(value, dict):
        return {k: scrub(v) for k, v in value.items()}
    if isinstance(value, list):
        return [scrub(v) for v in value]
    if isinstance(value, str):
        value = value.replace('\\', '/')
        for root, label in ((str(ROOT), '<WORKSPACE>'), (os.environ['TEMP'], '<TEMP>'), (str(Path.home()), '<HOME>')):
            value = re.sub(re.escape(root.replace('\\', '/')), label, value, flags=re.I)
    return value


def main():
    assert not git('status', '--porcelain'), 'commit implementation/docs/recorder before recording'
    assert not git('diff', BASE, '--', 'app/src/main', 'content/yangshen')
    assert load('config/pack-publishers.json') == [], 'no actual official roots in this task'
    before = load(OUT / 'protected-before.json')
    for row in before:
        assert info(row['path'], False)['sha256'] == row['sha256'], row['path']
    tracked = git('ls-files').splitlines()
    assert not any(p.endswith(('.pem', '.p12', '.jks', '.keystore')) for p in tracked)
    marker = ('-----BEGIN ' + 'PRIVATE KEY-----').encode()
    assert all(marker not in Path(p).read_bytes() for p in tracked)
    tests = dict(debug=suites('app/build/test-results/testDebugUnitTest'),
                 release=suites('app/build/test-results/testReleaseUnitTest'),
                 packtool=suites('packtool/build/test-results/test'))
    for variant in ('debug', 'release'):
        counts = {s['name']: s['tests'] for s in tests[variant]['suites']}
        assert counts['com.kitt.reader.d1.ProductionD2Test'] >= 58
        assert counts['com.kitt.reader.d1.ReleaseD2Test'] == 19
        assert counts['com.kitt.reader.d1.ReleaseD1Test'] == 27
    checks = load(OUT / 'checks.json')
    assert {c['name'] for c in checks} == {'debug', 'release', 'packtool', 'build-lint'}
    for check in checks:
        assert check['exit'] == 0
        check['log'] = info(OUT / (check['name'] + '.log'))
        assert 'BUILD SUCCESSFUL' in (OUT / (check['name'] + '.log')).read_text(encoding='utf-8-sig')
        if check['name'] in tests:
            threshold = datetime.fromisoformat(check['started_at']).timestamp()
            assert all(Path(s['report']['path']).stat().st_mtime >= threshold for s in tests[check['name']]['suites'])
    py = (OUT / 'python-tests.log').read_text(encoding='utf-8-sig')
    assert 'Ran 7 tests' in py and '\nOK' in py
    assert '0 eligible narratives' in (OUT / 'content-check.log').read_text(encoding='utf-8-sig')
    assert load(OUT / 'python-checks.json') == dict(python_tests_exit=0, content_check_exit=0)
    signing = load(OUT / 'signing.json')
    assert signing == dict(exit=0, accepted=2, rejected=6)
    signing['log'] = info(OUT / 'signing.log')
    signing['static_runtime_fixture'] = info('artifacts/kitt-v0.6.0-runtimecheck.apk', False)
    assert signing['static_runtime_fixture']['sha256'] == '2d8326eb295e642a8247715bdd13fa5c89c2081cdab071ba325735fea4fe5d60'
    fake = load('app/build/acceptance/d2-production-fake.json')
    assert fake['publish']['status'] == 'SIMULATED_PUBLISHED'
    assert fake['publish']['human_decisions'] == 1 and fake['publish']['signature_verified']
    assert fake['publish']['simulation'] and not fake['publish']['upload_performed']
    assert not fake['real_human_approval'] and not fake['private_material_exported']
    wrapper = load(OUT / 'wrapper-checks.json')
    assert all(wrapper.values())
    host_input = []
    for name, shell in (('host-input-final7', 'PowerShell 7'), ('host-input-final5', 'Windows PowerShell 5')):
        row = load(OUT / name / 'result.json')
        assert all(row[k] for k in ('utf8_single_decision_forwarded', 'simulated_flag_wrong_hash_and_multiple_lines_refused',
                                   'cli_refusal_propagated', 'host_encoding_restored', 'fake_process_only'))
        assert not row['real_user_approval_recorded'] and not row['real_upload_performed']
        host_input.append(dict(shell=shell, checks=row, log=info(OUT / (name + '.log'))))
    simulations = [info(Path('app/build/acceptance', p)) for p in ('full-simulation.txt', 'accelerated-simulation.txt',
                   'batch-simulation.txt', 'landmark-simulation.txt', 'dossier-simulation.txt', 'driving-area-resolution.txt')]
    audit = load(OUT / 'remote-audit.json')
    identity_boundary = load(OUT / 'identity-boundary.json')
    assert not identity_boundary['default_production_identity_exists']
    assert not identity_boundary['tracked_private_files'] and identity_boundary['original_worktree_count'] == 1
    evidence = dict(milestone='V0.6.0 D2 production capability (simulation only)', issue=42,
                    baseline_commit=BASE, evidence_source_commit=git('rev-parse', 'HEAD'),
                    implementation_commit=git('log', '-1', '--format=%H', '--', 'app/src/test/java/com/kitt/reader/d1', 'scripts/d2-pack.ps1', 'scripts/test-d2-host-input.ps1'),
                    started_at=START.isoformat(), recorded_at=datetime.now(timezone.utc).isoformat(),
                    tests=tests, totals={**{k: v['tests'] for k, v in tests.items()}, 'all': sum(v['tests'] for v in tests.values())},
                    checks=checks, fake_gh_acceptance=fake, fake_report=info('app/build/acceptance/d2-production-fake.json'),
                    python=dict(tests=7, test_log=info(OUT / 'python-tests.log'), content_log=info(OUT / 'content-check.log')),
                    signing=signing, simulations=simulations, wrapper_checks=wrapper, host_stdin_process_checks=host_input,
                    wrapper_logs=[info(OUT / p) for p in ('wrapper-prepare.log', 'wrapper-refusal.log', 'audit-newdu.log')],
                    preliminary_full_runs=load(OUT / 'preliminary-totals.json'),
                    preliminary_logs=[info(OUT / p) for p in ('preliminary-debug.log', 'preliminary-release.log')],
                    protected_files=before, production_app_and_yangshen_unchanged=True,
                    remote_readonly_audit=audit, identity_boundary=identity_boundary, production_public_registry=[],
                    real_identity_initialized=False, real_human_publication_approval=False,
                    real_tag_release_upload_performed=False, phone_adb_install_performed=False,
                    actual_gh_available_in_path=False, apk_keystore_reused_for_pack_signing=False,
                    key_storage='JUnit temporary directories outside Git worktrees; virtual flag; owner ACL; cleaned by JUnit',
                    physical_two_computers='PENDING; two virtual local identities share one source commit, fake gh only',
                    real_xindu='BLOCKED: 2/11 source rights unclear; Yangshen candidate/Visitor pending; formal stories 0',
                    limits=['stdin consent authenticates bytes, not human identity; host must relay actual user decision',
                            'gh create internally stages draft/uploads/publishes, not one server transaction; errors require read-only remote diagnosis',
                            'no distributed lock for simultaneous different sequences of same county; do not publish that county concurrently',
                            'fresh local engineering run, not external CI, phone JCA or actual GitHub Release test'],
                    pending=['separately authorized first real workstation identity and public registration',
                             'gh installation/authentication and separately authorized first actual publication',
                             'office/home physical test', 'phone trust/download/import/atomic install V0.6.1'],
                    failed_attempts=[dict(stage='initial compile', reason='Files.readString unavailable on Android test compile API; replaced with readAllBytes', counted=False),
                                     dict(stage='test compile', reason='ambiguous Kotlin property/function name; renamed fixture helper', counted=False),
                                     dict(stage='expanded focused tests', tests=73, failures=5, reason='fake remote assertion assumed ls-remote had no ref filters; corrected argument index', counted=False),
                                     dict(stage='host pipe process test', reason='PowerShell advanced script needed a ValueFromPipeline binding; added hidden stdin binding', counted=False),
                                     dict(stage='Windows PowerShell 5 process test', reason='native stdin used global ASCII encoding; added scoped UTF8 global encoding with restoration and UTF8 BOM script files', counted=False)])
    text = json.dumps(scrub(evidence), ensure_ascii=False, indent=2) + '\n'
    assert not re.search(r'[A-Za-z]:[/\\]', text) and 'AppData' not in text and 'PRIVATE KEY' not in text
    Path('docs/evidence/v060-d2-production-verification.json').write_text(text, encoding='utf-8')
    print(f'Issue42 evidence: {evidence["totals"]["all"]} fresh passing tests')


if __name__ == '__main__':
    main()
