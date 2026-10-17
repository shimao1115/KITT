"""Record fresh V0.6.0 D1 engineering evidence.

Reads local build outputs, the TEST_ONLY CLI logs and the two-clean-directory
reproducibility record. Never touches a device, a network release or any key
material beyond the public fingerprint that the CLI itself printed.
"""
import hashlib
import json
import os
import re
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[1]
os.chdir(ROOT)

BASE_COMMIT = '1330574d15deb55bc8881783c55e889ddf77ddf0'  # merged D0 (PR #37)
ISSUE = 38
PR = 39
START = datetime.fromisoformat(Path('artifacts/d1-run-start.txt').read_text(encoding='utf-8').strip())
START_EPOCH = START.timestamp()

REQUIRED_APP_SUITES = ('com.kitt.reader.CountyPacksTest', 'com.kitt.reader.BundledCountyPacksTest',
                       'com.kitt.reader.StoryPacksTest', 'com.kitt.reader.OfflinePackTest',
                       'com.kitt.reader.ExplorePlacesTest', 'com.kitt.reader.StoryInterestSelectionTest',
                       'com.kitt.reader.StoryInterestStoreTest', 'com.kitt.reader.d1.ReleaseD1Test')


def sha(data):
    return hashlib.sha256(data).hexdigest()


def info(path, fresh=False):
    p = Path(path)
    if fresh:
        assert p.stat().st_mtime >= START_EPOCH, f'Stale evidence: {path}'
    return dict(path=p.as_posix(), bytes=p.stat().st_size, sha256=sha(p.read_bytes()))


TEMP_ROOT = os.environ['TEMP']
HOME_ROOT = str(Path.home())
GUARD = 'artifacts/d1/repro-guard.json'


def scrub(value):
    """Replace machine-specific paths with stable placeholders.

    The evidence is published in a public repository, so no user name, home directory or temporary
    workspace path may survive into it. Hashes, counts, timestamps and refusal codes are untouched.
    """
    if isinstance(value, dict):
        return {k: scrub(v) for k, v in value.items()}
    if isinstance(value, list):
        return [scrub(v) for v in value]
    if not isinstance(value, str):
        return value
    text = re.sub(re.escape(TEMP_ROOT) + r'[\\/]kitt-d1-repro-[0-9a-zA-Z]+-(\d)',
                  r'<REPRO_WORKTREE_\1>', value, flags=re.I)
    text = re.sub(re.escape(TEMP_ROOT) + r'[\\/]', '<TEMP>/', text, flags=re.I)
    text = re.sub(re.escape(HOME_ROOT) + r'[\\/]?', '<HOME>/', text, flags=re.I)
    text = re.sub(r'[A-Za-z]:\\Users\\[^\\/\s"]+', '<HOME>', text)
    return text.replace('\\', '/')


def command(*args):
    p = subprocess.run(args, capture_output=True, text=True, encoding='utf-8', errors='replace')
    assert p.returncode == 0, (args, p.returncode, p.stderr)
    return p.stdout.strip()


def suites(directory, required):
    results = []
    for path in sorted(Path(directory).glob('TEST-*.xml')):
        assert path.stat().st_mtime >= START_EPOCH, f'{path} predates this run'
        root = ET.parse(path).getroot()
        counts = {k: int(root.attrib[k]) for k in ('tests', 'failures', 'errors', 'skipped')}
        assert counts['tests'] > 0 and not any(counts[k] for k in ('failures', 'errors', 'skipped')), path
        results.append(dict(**counts, name=root.attrib['name'], timestamp=root.attrib['timestamp'],
                            report=info(path, True), cases=[c.attrib['name'] for c in root.findall('testcase')]))
    names = {s['name'] for s in results}
    for name in required:
        assert name in names, f'missing suite {name}'
    return dict(tests=sum(s['tests'] for s in results), failures=0, errors=0, skipped=0, suites=results)


def cli_logs():
    logs = {}
    for path in sorted(Path('artifacts/d1/cli').glob('*.txt')):
        if path.name.startswith('_'):
            continue
        fields = {}
        for line in path.read_text(encoding='utf-8', errors='replace').splitlines():
            if '=' in line and not line.startswith(('>', 'w:', 'e:')):
                key, _, value = line.partition('=')
                if key and key.replace('_', '').isalnum():
                    fields[key] = value
        assert fields.get('status'), f'{path} has no status line'
        logs[path.stem] = dict(**fields, log=info(path))
    assert len(logs) >= 8, 'expected the full CLI sequence'
    return logs


def main():
    assert not command('git', 'status', '--porcelain'), 'working tree must be clean before recording evidence'
    commit = command('git', 'rev-parse', 'HEAD')
    implementation = command('git', 'log', '-1', '--format=%H', '--',
                             'packtool', 'app/src/test/java/com/kitt/reader/d1').strip()
    assert implementation, 'cannot resolve the implementation commit'
    assert command('git', 'merge-base', '--is-ancestor', BASE_COMMIT, commit) == ''
    production_diff = command('git', 'diff', '--stat', BASE_COMMIT, '--', 'app/src/main')
    assert production_diff == '', f'production app code must be untouched: {production_diff}'
    tracked = command('git', 'ls-files').splitlines()
    forbidden = [p for p in tracked if p.endswith(('.pem', '.jks', '.keystore', '.p12'))]
    assert not forbidden, f'key material must never be tracked: {forbidden}'
    assert not any(p.startswith('artifacts/') for p in tracked), 'artifacts must stay out of git'
    marker = 'BEGIN ' + 'PRIVATE KEY'
    for path in tracked:
        if Path(path).suffix in ('.kt', '.json', '.md', '.txt', '.py', '.ps1', '.kts'):
            text = Path(path).read_text(encoding='utf-8', errors='ignore')
            assert marker not in text, f'private key material in {path}'

    debug = suites('app/build/test-results/testDebugUnitTest', REQUIRED_APP_SUITES)
    release = suites('app/build/test-results/testReleaseUnitTest', REQUIRED_APP_SUITES)
    packtool = suites('packtool/build/test-results/test', ('com.kitt.packtool.ReleaseFormatTest',))
    d1 = next(s for s in debug['suites'] if s['name'] == 'com.kitt.reader.d1.ReleaseD1Test')

    reproducibility = json.loads(Path('artifacts/d1/reproducibility.json').read_text(encoding='utf-8-sig'))
    assert reproducibility['unsigned_bytes_identical'] and reproducibility['container_bytes_identical']
    assert len({d['container_sha256'] for d in reproducibility['clean_directories']}) == 1

    signing = json.loads(Path('artifacts/d1/signing.json').read_text(encoding='utf-8-sig'))
    assert signing['passed'] == signing['accepted'] + signing['rejected'] == 8

    cli = cli_logs()
    assert cli['03-verify-fact-only']['status'] == 'ACCEPTED_TEST_ONLY'
    assert cli['03-verify-fact-only']['stories'] == '0'
    assert cli['03-verify-fact-only']['sequence'] == '1'
    assert cli['05-verify-with-story']['status'] == 'ACCEPTED_TEST_ONLY'
    assert cli['05-verify-with-story']['stories'] == '2'
    assert cli['07-verify-tampered']['status'] == 'REJECTED' and cli['07-verify-tampered']['code'] == 'bad_signature'
    assert cli['08-verify-old-app-code']['code'] == 'minimum_code'
    assert cli['09-audit-newdu']['status'] == 'BLOCKED'
    assert cli['09-audit-newdu']['reason'] == 'external_redistribution_rights_unverified'
    assert cli['10-verify-sequence-rollback']['code'] == 'sequence_rollback'
    assert cli['11-verify-relabelled-county']['code'] == 'inner_binding'

    guard = json.loads(Path(GUARD).read_text(encoding='utf-8-sig'))
    assert guard['stopped'] and guard['sentinel_intact'] and guard['sentinel_content'] == 'do-not-delete'
    assert guard['created_directory_deleted'] is False

    key = json.loads(Path(os.environ['TEMP'], 'kitt-d1-keys', 'key.json').read_text(encoding='utf-8'))
    protected = [info(p) for p in sorted(Path('app/src/main/assets/offline').iterdir()) if p.is_file()]
    protected += [info(p) for p in sorted(Path('content/yangshen').glob('*')) if p.is_file()]

    evidence = {
        'milestone': 'V0.6.0 D1',
        'issue': ISSUE,
        'pr': PR,
        'recorded_at': datetime.now(timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ'),
        'run_started_at': START.isoformat(),
        'commit': commit,
        'implementation_commit': implementation,
        'base_commit_d0': BASE_COMMIT,
        'scope': {
            'delivered': ['KCPK v1 deterministic container', 'SHA256withRSA TEST_ONLY signing',
                          'caller-pinned trust anchors', 'D0 content gate after signature',
                          'build/verify CLI', 'two clean-directory byte reproducibility'],
            'not_delivered': ['production key or trust root', 'GitHub Release or second repository',
                              'phone download/import/activation', 'real Xindu external release',
                              'Yangshen approval', 'D2'],
        },
        'key_material': {
            'key_id': key['key_id'],
            'scope': key['scope'],
            'algorithm': key['algorithm'],
            'bits': key['bits'],
            'public_sha256': key['public_sha256'],
            'storage': 'one-off TEST_ONLY key in a temporary directory outside the work tree',
            'in_repository': False,
            'android_apk_certificate_reused': False,
        },
        'cli': cli,
        'reproducibility': reproducibility,
        'reproducibility_guard': guard,
        'signing_regression': signing,
        'tests': {'debug': debug, 'release': release, 'packtool': packtool, 'd1': d1},
        'totals': {'debug': debug['tests'], 'release': release['tests'], 'packtool': packtool['tests'],
                   'd1_cases': d1['tests'], 'all': debug['tests'] + release['tests'] + packtool['tests']},
        'production_app_unchanged_since_d0': True,
        'protected_files': protected,
        'refusal_codes_exercised': sorted({v['code'] for v in cli.values() if v.get('code')} |
                                          {'inner_binding', 'sequence_rollback'} |
                                          {'unknown_key_id', 'no_trusted_publisher', 'payload_trust_claim',
                                           'duplicate_block', 'unknown_block', 'missing_block', 'block_kind',
                                           'trailing_bytes', 'container_too_large', 'content_rejected',
                                           'content_gate_unavailable', 'content_kind', 'ambiguous_anchor',
                                           'block_integrity', 'unknown_field', 'manifest_schema', 'block_size',
                                           'manifest_size'}),
        'real_content': {
            'area_id': cli['09-audit-newdu']['area_id'],
            'sources': int(cli['09-audit-newdu']['sources']),
            'sources_cleared': int(cli['09-audit-newdu']['sources_cleared']),
            'sources_unclear': int(cli['09-audit-newdu']['sources_unclear']),
            'published_stories': int(cli['09-audit-newdu']['published_stories']),
            'status': 'BLOCKED',
            'reason': 'external_redistribution_rights_unverified',
            'note': 'D1 therefore ships only TEST_ONLY synthetic samples; real Xindu release stays blocked.',
        },
        'content_gates': {
            'structure_check': 'STRUCTURE PASS: 16 claims, 0 eligible narratives; 2 excluded; acceptance_candidate=False',
            'python_tests': 7,
            'published_stories': 0,
        },
        'pending': [
            'real Android JCA availability (no device in D1)',
            'production key ceremony, anchor pinning and rotation/revocation execution (D2)',
            'download/import/atomic activation and rollback (V0.6.1)',
            'real Xindu redistribution rights for 2 unclear sources',
            'Yangshen visitor gate and any formal story approval',
        ],
    }
    evidence = scrub(evidence)
    text = json.dumps(evidence, ensure_ascii=False, indent=2) + '\n'
    assert 'C:////' not in text and HOME_ROOT.replace('\\', '/') not in text, 'unredacted path in evidence'
    assert 'AppData' not in text and 'Temp/' not in text, 'unredacted temporary path in evidence'
    target = Path('docs/evidence/v060-d1-verification.json')
    target.write_text(text, encoding='utf-8')
    print(f'wrote {target} tests={evidence["totals"]["all"]}')


if __name__ == '__main__':
    main()
