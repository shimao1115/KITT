"""Record fresh D0/code20 engineering evidence. Reads local build outputs, never devices/secrets."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET
import zipfile
from datetime import datetime, timezone, timedelta

ROOT = Path(__file__).resolve().parents[1]
os.chdir(ROOT)
CERT = '3139a293aaf9a233accba2489f10bb59c8f79102b631abfd51a7ec7f929502ba'
BASE = 'edab16f18afc2e23009616bd090fbabd00d4acb3'
REVIEW_BASE = '9f3ada22d7bf5cd3f5929e9804898f6f2b75c6b0'
REVIEW_URL = 'https://github.com/shimao1115/KITT/pull/37#issuecomment-6229804921'
FIX_CASES = ['resealedCountyCannotBeIndexedUnderAnotherPrefectureOrProvince',
             'specialAdministrativeLevelsNeedExplicitRulesRatherThanAnInventedPrefecture',
             'restrictedSourceRightsOverrideAllowedPermissionAndFreshAcceptedRevisions',
             'restrictedClaimRightsOverrideAllowedPermissionAndFreshAcceptedRevision',
             'explicitLicenseRemainsUsableWithFreshDependencyFingerprints',
             'existingCuratedSummaryScopesRemainDistinctFromUnclearedSourceRights']
START = json.loads(Path('artifacts/v060-run-start.json').read_text(encoding='utf-8'))
START_EPOCH = datetime.fromisoformat(START['started_at']).timestamp()


def sha(data):
    return hashlib.sha256(data).hexdigest()


def info(path, fresh=False):
    p = Path(path)
    if fresh:
        assert p.stat().st_mtime >= START_EPOCH, f'Stale evidence: {path}'
    return dict(path=p.as_posix(), bytes=p.stat().st_size, sha256=sha(p.read_bytes()))


def command(*args):
    p = subprocess.run(args, capture_output=True, text=True, encoding='utf-8', errors='replace')
    assert p.returncode == 0, (args[0], p.returncode, p.stderr)
    return p.stdout


def suites(variant):
    results = []
    for path in sorted(Path(f'app/build/test-results/test{variant}UnitTest').glob('TEST-*.xml')):
        root = ET.parse(path).getroot()
        counts = {k: int(root.attrib[k]) for k in ('tests', 'failures', 'errors', 'skipped')}
        assert counts['tests'] > 0 and not any(counts[k] for k in ('failures', 'errors', 'skipped'))
        results.append(dict(**counts, name=root.attrib['name'], timestamp=root.attrib['timestamp'],
                            report=info(path, True), cases=[c.attrib['name'] for c in root.findall('testcase')]))
    assert len(results) > 60
    for name in ('CountyPacksTest', 'BundledCountyPacksTest', 'StoryPacksTest', 'OfflinePackTest',
                 'ExplorePlacesTest', 'StoryInterestSelectionTest', 'StoryInterestStoreTest'):
        assert any(s['name'] == f'com.kitt.reader.{name}' for s in results), name
    return dict(tests=sum(s['tests'] for s in results), failures=0, errors=0, skipped=0, suites=results)


def apk(path, package, test=False):
    sdk = Path(os.environ.get('ANDROID_HOME') or os.environ['ANDROID_SDK_ROOT']) / 'build-tools/35.0.0'
    header = command(str(sdk / 'aapt.exe'), 'dump', 'badging', path).splitlines()[0]
    assert f"name='{package}'" in header
    if not test:
        assert "versionCode='20'" in header and "versionName='0.6.0'" in header
    verify = command(str(sdk / 'apksigner.bat'), 'verify', '--verbose', '--print-certs', path)
    certificate = re.search(r'Signer #1 certificate SHA-256 digest: ([a-f0-9]+)', verify).group(1)
    assert certificate == CERT
    result = dict(**info(path, True), package_header=header, certificate_sha256=certificate,
                  permissions=command(str(sdk / 'aapt.exe'), 'dump', 'permissions', path).splitlines())
    if not test:
        with zipfile.ZipFile(path) as z:
            assets = [n for n in z.namelist() if n.startswith('assets/offline/') and not n.endswith('/')]
            for name in assets:
                assert z.read(name) == Path('app/src/main', name).read_bytes(), name
            f = json.loads(z.read('assets/offline/xindu-facts.json'))
            n = json.loads(z.read('assets/offline/ys-narratives.json'))
            assert len(f['objects']) == 6 and sum(len(o['facts']) for o in f['objects']) == 22
            assert len(f['sources']) == 6 and not n['narratives']
            for name in z.namelist():
                if re.fullmatch(r'classes[0-9]*\.dex', name):
                    dex = z.read(name)
                    for forbidden in (b'Lcom/kitt/reader/CountyFixture;', b'Lcom/kitt/reader/CountyPacksTest;',
                                      b'cn-420502', 'TEST_ONLY 合成工程夹具'.encode()):
                        assert forbidden not in dex, (name, forbidden)
            result.update(counties=1, basic_objects=6, basic_facts=22, basic_sources=6, formal_stories=0,
                          second_county_fixture_absent=True,
                          offline_assets={name: sha(z.read(name)) for name in assets})
    return result


def main():
    variants = {v: suites(v) for v in ('Debug', 'Release')}
    previous = json.loads(command('git', 'show', f'{REVIEW_BASE}:docs/evidence/v060-d0-verification.json'))
    for v, results in variants.items():
        cases = next(s['cases'] for s in results['suites'] if s['name'] == 'com.kitt.reader.CountyPacksTest')
        assert set(FIX_CASES).issubset(cases), f'{v} missing review regression cases'
        assert results['tests'] == previous['variants'][v]['tests'] + len(FIX_CASES)
    verification = json.loads(Path('artifacts/verification.json').read_text(encoding='utf-8-sig'))
    assert verification['version'] == '0.6.0'
    for v in variants:
        assert variants[v]['tests'] == verification['variants'][v]['tests']
    logs = {}
    for name in ('verify', 'runtime-build', 'signing', 'content', 'gate'):
        path = Path(f'artifacts/v060-{name}.log')
        logs[name] = info(path, True)
        text = path.read_text(encoding='utf-8-sig', errors='replace')
        if name in ('verify', 'runtime-build'):
            assert 'BUILD SUCCESSFUL' in text and 'BUILD FAILED' not in text
        if name == 'verify':
            for v in variants:
                assert re.search(rf'^> Task :app:test{v}UnitTest\s*$', text, re.M), f'{v} not actually executed'
        if name == 'signing':
            assert 'PASS: signing regression (2 accepted cases, 6 rejected cases).' in text
        if name == 'content':
            assert 'Ran 7 tests' in text and 'OK' in text and 'STRUCTURE PASS' in text
        if name == 'gate':
            assert 'BLOCKED' in text and 'exit=1' in text and 'exit=2' in text and 'never overwrite standard assets' in text
    lint = {}
    for v in ('debug', 'release', 'runtimecheck'):
        path = Path('artifacts/runtime-smoke-workspace/app/build/reports/lint-results-debug.xml') if v == 'runtimecheck' else Path(f'app/build/reports/lint-results-{v}.xml')
        issues = ET.parse(path).getroot().findall('issue')
        assert not any(i.attrib['severity'] in ('Error', 'Fatal') for i in issues)
        lint[v] = dict(**info(path, True), errors=0, warnings=len(issues), warning_ids=[i.attrib['id'] for i in issues])
    simulations = []
    for name in ('full-simulation', 'accelerated-simulation', 'batch-simulation', 'landmark-simulation', 'dossier-simulation', 'driving-area-resolution'):
        path = Path(f'app/build/acceptance/{name}.txt')
        text = path.read_text(encoding='utf-8-sig')
        assert 'PASS' in text if name != 'driving-area-resolution' else text.count('minInterval=16000ms') == 4
        simulations.append(dict(**info(path, True), summary=text[:2000], scope='deterministic/Fake only; no physical GPS or human hearing'))
    apks = [apk(f'artifacts/kitt-v0.6.0-{kind}.apk', package) for kind, package in
            [('debug', 'com.kitt.reader'), ('release', 'com.kitt.reader'), ('runtimecheck', 'com.kitt.reader.runtimecheck')]]
    apks.append(apk('artifacts/runtime-smoke-workspace/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk',
                    'com.kitt.reader.runtimecheck.test', test=True))
    source = {}
    for folder in ('app/src/main', 'app/src/test', 'content/yangshen'):
        for p in sorted(Path(folder).rglob('*')):
            if p.is_file():
                assert p.stat().st_mtime <= START_EPOCH, f'Build input changed after verification started: {p}'
                source[p.as_posix()] = sha(p.read_bytes())
    mismatches = [p for p, digest in source.items() if sha((Path('artifacts/runtime-smoke-workspace') / p).read_bytes()) != digest]
    manifest = 'app/src/main/AndroidManifest.xml'
    expected_manifest = Path(manifest).read_text(encoding='utf-8-sig').replace('android:label="沿途"', 'android:label="沿途 0.6.0 验收"').strip()
    actual_manifest = (Path('artifacts/runtime-smoke-workspace') / manifest).read_text(encoding='utf-8-sig').strip()
    assert actual_manifest == expected_manifest, 'Unexpected runtime manifest change'
    mismatches = [p for p in mismatches if p != manifest]
    assert not mismatches
    protected = ['content', 'app/src/main/assets', 'app/src/main/AndroidManifest.xml',
                 'app/src/main/java/com/kitt/reader/TripStore.kt', 'app/src/main/java/com/kitt/reader/SettingsStore.kt',
                 'app/src/main/java/com/kitt/reader/StoryInterestStore.kt', 'app/src/main/java/com/kitt/reader/DirectorLoop.kt']
    assert not command('git', 'diff', BASE, '--', *protected).strip()
    revision_changes = command('git', 'diff', '--name-only', REVIEW_BASE, '--', 'app/src/main', 'app/build.gradle.kts').splitlines()
    assert revision_changes == ['app/src/main/java/com/kitt/reader/CountyPacks.kt'], 'Revision expanded production scope'
    baseline = json.loads(Path('docs/evidence/v052-verification.json').read_text(encoding='utf-8'))
    old = next(a for a in baseline['apks'] if a['path'].endswith('runtimecheck.apk'))
    candidate = next(a for a in apks if a['path'].endswith('runtimecheck.apk'))
    assert old['certificate_sha256'] == CERT and "versionCode='19'" in old['package_header']
    sdk = Path(os.environ.get('ANDROID_HOME') or os.environ['ANDROID_SDK_ROOT']) / 'build-tools/35.0.0'
    local_old = info('artifacts/kitt-v0.5.2-runtimecheck.apk')
    assert local_old['sha256'] == old['sha256'] and local_old['bytes'] == old['bytes']
    old_header = command(str(sdk / 'aapt.exe'), 'dump', 'badging', local_old['path']).splitlines()[0]
    old_cert = command(str(sdk / 'apksigner.bat'), 'verify', '--verbose', '--print-certs', local_old['path'])
    assert CERT in old_cert and "versionCode='19'" in old_header and "name='com.kitt.reader.runtimecheck'" in old_header
    old_permissions = command(str(sdk / 'aapt.exe'), 'dump', 'permissions', local_old['path']).splitlines()
    uses = lambda lines: sorted(line for line in lines if line.startswith('uses-permission:'))
    assert uses(old_permissions) == uses(candidate['permissions']), 'Runtime candidate added/changed permissions'
    result = dict(date='2026-10-17', recorded_at=datetime.now(timezone(timedelta(hours=8))).isoformat(),
                  version='0.6.0', version_code=20, milestone='D0 only', issue=36, pull_request=37,
                  branch='feat/v060-county-pack-loader-d0', branch_base=BASE,
                  review_base=REVIEW_BASE, review_url=REVIEW_URL,
                  previous_round=dict(commit=REVIEW_BASE, tests=previous['total_tests'],
                                      variants={v: previous['variants'][v]['tests'] for v in variants},
                                      candidate=next(a for a in previous['apks'] if a['path'].endswith('runtimecheck.apk')),
                                      scope='Historical initial D0 evidence only; never added to this rerun count'),
                  validation_fixes=dict(parent='Exact county first-four-digit prefecture prefix + 00; unsupported 00/90 prefecture groups rejected',
                                        rights='Finite full-string license/facts-only scope allowlist; unknown/restricted/mixed notices rejected even with allowed permission and fresh revisions',
                                        new_tests_per_variant=len(FIX_CASES), cases=FIX_CASES,
                                        production_paths_changed=revision_changes),
                  implementation_commit=command('git', 'log', '-1', '--format=%H', '--', 'app/src/main', 'app/build.gradle.kts').strip(),
                  status='ENGINEERING-STOP-USER-REVIEW-PENDING', run_start=START, variants=variants,
                  total_tests=sum(v['tests'] for v in variants.values()), python_tests=7,
                  lint=lint, simulations=simulations, logs=logs, apks=apks,
                  provenance='Fresh XML and build task execution checked against run-start marker; source hashes match clean runtime copy. APKs built before delivery commit.',
                  clean_build=json.loads(Path('artifacts/v060-clean-build.json').read_text(encoding='utf-8')),
                  signing=dict(accepted_cases=2, rejected_cases=6, certificate_sha256=CERT),
                  static_upgrade=dict(baseline=old, local_baseline_reverified=local_old, permissions_unchanged=True,
                                      candidate=candidate['path'], from_code=19, to_code=20,
                                      result='Same package/certificate; monotonic code. Static compatibility only, no device access.'),
                  county=dict(production_counties=1, objects=6, basic_facts=22, basic_sources=6, registry_sources=17, claims=16,
                              formal_stories=0, fixture_areas=['cn-510114', 'cn-420502'], fixture_scope='TEST_ONLY synthetic, test directory only',
                              fixture_mains=6, fixture_followups=1, versions='Independent Fact 1 / Narrative 9 in fixture',
                              trust='Default reject; fixed APK assets only in production. In-memory RSA test verifier and key exclusively under src/test.',
                              external_publisher_root='NOT IMPLEMENTED; no external activation entry point',
                              invalid_claim_impact='Only explicitly referencing stories; unrelated stories and independent basic facts survive'),
                  content=dict(manuscripts='Unchanged: candidate / fact_gate=passed / visitor_gate=pending', release_gate='BLOCKED',
                               standard_require_playable_exit=1, candidate_without_output_dir_exit=2),
                  protected_paths_unchanged=protected, source_sha256=source, runtime_copy_mismatches=mismatches,
                  intentional_runtime_copy_changes=['applicationId com.kitt.reader.runtimecheck', 'App label 沿途 0.6.0 验收; normalized manifest compared explicitly'],
                  workspace_preflight=dict(initial_branch='feat/v060-county-pack-loader-d0', initial_worktree='clean',
                                           worktrees=1, fetched_head=REVIEW_BASE, preexisting_build_workspaces='Preserved under ignored artifacts; no deletion or overwrite'),
                  device_actions='NONE: no adb, phone access, installation, credential or historical device-data access',
                  pending=['Independent engineering re-review after the two validation fixes', 'Real device GPS/TTS and field gates #23/#24/#27/#30 unchanged',
                           'Formal story approval', 'Real publisher trust/signing/Release, downloads/import/atomic installation',
                           'settings.xml first-entry drift remains UNKNOWN; source dual mode DEFERRED'],
                  github='PR37 remains Draft/unmerged; PR29 untouched; no Release, APK upload or KITT-PACKS repository')
    Path('docs/evidence/v060-d0-verification.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(f"Recorded Debug {variants['Debug']['tests']} + Release {variants['Release']['tests']} = {result['total_tests']}; no device actions; STOP.")


if __name__ == '__main__':
    main()
