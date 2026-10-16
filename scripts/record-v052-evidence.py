"""Record the actual code19 local verification outputs. Never touches a connected device."""
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
RUN_DATE = '2026-10-16'
BRANCH_BASE = '9319c271f878000f79a71be92df7bee6ec92605f'
INTEREST_SUITES = ['com.kitt.reader.StoryRankingTest', 'com.kitt.reader.StoryInterestStoreTest',
                   'com.kitt.reader.StoryInterestSelectionTest', 'com.kitt.reader.StoryInterestUiTest']


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def file_info(path):
    path = Path(path)
    return dict(path=path.as_posix(), bytes=path.stat().st_size, sha256=digest(path))


def command(*args):
    result = subprocess.run(args, capture_output=True, text=True, encoding='utf-8', errors='replace')
    assert result.returncode == 0, (args[0], result.returncode, result.stderr)
    return result.stdout


def suites(variant):
    results = []
    for path in sorted(Path(f'app/build/test-results/test{variant}UnitTest').glob('TEST-*.xml')):
        suite = ET.parse(path).getroot()
        assert suite.attrib.get('timestamp', '').startswith(RUN_DATE), 'Historical XML cannot count as code19 execution'
        item = {k: int(suite.attrib[k]) for k in ('tests', 'failures', 'errors', 'skipped')}
        assert item['tests'] > 0 and not any(item[k] for k in ('failures', 'errors', 'skipped'))
        item.update(name=suite.attrib['name'], timestamp=suite.attrib.get('timestamp'),
                    report_sha256=digest(path), cases=[c.attrib['name'] for c in suite.findall('testcase')])
        results.append(item)
    assert len(results) > 60
    for name in INTEREST_SUITES:
        assert any(s['name'] == name for s in results), f'{name} missing from {variant}'
    return dict(tests=sum(s['tests'] for s in results), failures=0, errors=0, skipped=0, suites=results)


def apk(path, package, test_apk=False):
    sdk = Path(os.environ.get('ANDROID_HOME') or os.environ['ANDROID_SDK_ROOT']) / 'build-tools/35.0.0'
    badging = command(str(sdk / 'aapt.exe'), 'dump', 'badging', path)
    header = badging.splitlines()[0]
    assert f"name='{package}'" in header
    if not test_apk:
        assert "versionCode='19'" in header and "versionName='0.5.2'" in header
    verify = command(str(sdk / 'apksigner.bat'), 'verify', '--verbose', '--print-certs', path)
    cert = re.search(r'Signer #1 certificate SHA-256 digest: ([a-f0-9]+)', verify).group(1)
    assert cert == CERT
    result = {**file_info(path), 'package_header': header, 'certificate_sha256': cert}
    if not test_apk:
        with zipfile.ZipFile(path) as z:
            facts = json.loads(z.read('assets/offline/xindu-facts.json'))
            narratives = json.loads(z.read('assets/offline/ys-narratives.json'))
            assert len(facts['objects']) == 6 and sum(len(o['facts']) for o in facts['objects']) == 22
            assert not narratives['narratives'], 'A code19 APK must not carry promoted stories'
            result.update(browsable_objects=6, browsable_basic_facts=22, formal_stories=0,
                          narrative_asset_sha256=hashlib.sha256(z.read('assets/offline/ys-narratives.json')).hexdigest())
    return result


def main():
    verification = json.loads(Path('artifacts/verification.json').read_text(encoding='utf-8-sig'))
    assert verification['version'] == '0.5.2'
    variants = {v: suites(v) for v in ('Debug', 'Release')}
    for v in variants:
        assert variants[v]['tests'] == verification['variants'][v]['tests'], v
    lint = {}
    for v in ('debug', 'release', 'runtimecheck'):
        path = (Path('artifacts/runtime-smoke-workspace/app/build/reports/lint-results-debug.xml')
                if v == 'runtimecheck' else Path(f'app/build/reports/lint-results-{v}.xml'))
        issues = ET.parse(path).getroot().findall('issue')
        assert not any(i.attrib['severity'] in ('Error', 'Fatal') for i in issues)
        lint[v] = {**file_info(path), 'errors': 0, 'warnings': len(issues), 'warning_ids': [i.attrib['id'] for i in issues]}
    simulations = []
    for name in ('full-simulation', 'accelerated-simulation', 'batch-simulation', 'landmark-simulation',
                 'dossier-simulation', 'driving-area-resolution'):
        path = Path(f'app/build/acceptance/{name}.txt')
        text = path.read_text(encoding='utf-8-sig')
        assert datetime.fromtimestamp(path.stat().st_mtime).date().isoformat() == RUN_DATE
        if name == 'driving-area-resolution':
            assert text.count('minInterval=16000ms') == 4
            assert all(any(s['name'] == 'com.kitt.reader.DrivingAreaResolutionTest' for s in v['suites'])
                       for v in variants.values())
        else:
            assert 'PASS' in text
        simulations.append({**file_info(path), 'summary': text[:1800],
                            'scope': 'deterministic/Fake director and fake voice; not physical GPS or human hearing'})
    outputs = [apk(f'artifacts/kitt-v0.5.2-{variant}.apk', package) for variant, package in
               [('debug', 'com.kitt.reader'), ('release', 'com.kitt.reader'),
                ('runtimecheck', 'com.kitt.reader.runtimecheck')]]
    outputs.append(apk('artifacts/runtime-smoke-workspace/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk',
                       'com.kitt.reader.runtimecheck.test', test_apk=True))
    installed = json.loads(Path('docs/evidence/v051-verification.json').read_text(encoding='utf-8'))
    code18 = next(a for a in installed['apks'] if a['path'].endswith('kitt-v0.5.1-runtimecheck.apk'))
    assert code18['certificate_sha256'] == CERT and "versionCode='18'" in code18['package_header']
    candidate = next(o for o in outputs if o['path'].endswith('runtimecheck.apk'))
    upgrade = dict(baseline=code18['path'], baseline_bytes=code18['bytes'], baseline_sha256=code18['sha256'],
                   baseline_version="versionCode='18'", candidate=candidate['path'],
                   candidate_version="versionCode='19'", candidate_sha256=candidate['sha256'],
                   certificate_sha256=CERT,
                   result=('Same applicationId and same certificate; versionCode 18 to 19 is monotonic, so an '
                           'in-place upgrade is statically permitted. The code18 APK bytes are not present on this '
                           'PC, so the baseline side reuses the recorded phone read-back evidence. Installation NOT RUN.'))
    inputs = {}
    for folder in ('app/src/main/java', 'app/src/main/assets/offline', 'content/yangshen'):
        for path in sorted(Path(folder).rglob('*')):
            if path.is_file():
                inputs[path.as_posix()] = digest(path)
    mismatches = [p for p, sha in inputs.items() if digest(Path('artifacts/runtime-smoke-workspace') / p) != sha]
    assert not mismatches
    logs = {}
    for name in ('verify', 'runtime-build', 'signing', 'content', 'gate'):
        path = Path(f'artifacts/v052-{name}.log')
        logs[name] = file_info(path)
        text = path.read_text(encoding='utf-8-sig', errors='replace')
        if name in ('verify', 'runtime-build'):
            assert 'BUILD SUCCESSFUL' in text and 'BUILD FAILED' not in text
        if name == 'signing':
            assert 'PASS: signing regression (2 accepted cases, 6 rejected cases).' in text
        if name == 'content':
            assert 'Ran 7 tests' in text and 'OK' in text and 'STRUCTURE PASS' in text
        if name == 'gate':
            assert 'BLOCKED' in text and 'exit=1' in text
            assert 'never overwrite standard assets' in text and 'exit=2' in text
    assert not command('git', 'diff', BRANCH_BASE, '--', 'content', 'app/src/main/assets/offline',
                       'app/src/main/java/com/kitt/reader/TripStore.kt',
                       'app/src/main/java/com/kitt/reader/SettingsStore.kt').strip()
    result = dict(
        date=RUN_DATE, version='0.5.2', version_code=19, status='ENGINEERING-STOP-USER-ACCEPTANCE-PENDING',
        issue=34, pull_request=35, branch='feat/v052-story-preferences', branch_base=BRANCH_BASE,
        implementation_commit=command('git', 'log', '-1', '--format=%H', '--', 'app/src/main', 'app/build.gradle.kts').strip(),
        build_provenance='APKs built from the working tree before its delivery commit; source_sha256 records those '
                         'production bytes and the runtime copy is compared against them.',
        recorded_at=datetime.now(timezone(timedelta(hours=8))).isoformat(),
        variants=variants, total_tests=sum(v['tests'] for v in variants.values()), python_tests=7,
        interest=dict(
            categories=['NATURE', 'ENGINEERING', 'CULTURE', 'LOCAL_LIFE', 'HISTORY', 'TRAVEL'],
            levels=['MORE', 'NORMAL', 'LESS'], default='NORMAL',
            storage='separate SharedPreferences file story_interests; settings.xml keys untouched',
            ranking='post-gate stable soft priority; never filters, quotas or admits',
            automatic_only=True, explicit_request_and_explore_decoupled=True,
            suites=INTEREST_SUITES),
        content=dict(browsable_counties=1, browsable_objects=6, browsable_basic_facts=22, loaded_legacy_sources=6,
                     formal_stories=0, fixture_stories=2, release_gate='BLOCKED',
                     standard_require_playable_exit=1, candidate_without_separate_output_exit=2,
                     manuscripts='candidate / fact_gate=passed / visitor_gate=pending; unchanged',
                     fixture_scope='in-memory TEST_ONLY multi-category candidates; no promotion in source or APK'),
        lint=lint, simulations=simulations, apks=outputs, logs=logs,
        console_logs='v052-verify.log is the retained console tail of the full verify.ps1 run; the per-class Gradle '
                     'XML reports above carry the complete suite evidence.',
        upgrade_baseline=upgrade,
        signing=dict(accepted_cases=2, rejected_cases=6, certificate_sha256=CERT),
        source_sha256=inputs, runtime_copy_mismatches=mismatches,
        source_mode='DEFERRED: preferOffline is hard-wired to !simulation at KittRuntime.kt:88, so an instant-first '
                    'toggle would move automatic narration onto live cloud/account/network paths and start per-chapter '
                    'background research (DirectorLoop.kt:48,61) with no bounded local fallback for the automatic '
                    'branch today (DirectorLoop.kt:80-81). Real behavior needs a live account and a device, both out '
                    'of scope for this round, so the two-tier source mode is not implemented or claimed.',
        compatibility='SettingsStore/TripStore and all content and asset bytes unchanged from the branch base; '
                      '20 synthetic histories and the settings.xml byte regression run in tests. No phone data accessed.',
        layout='Compose/Robolectric: 411x914 portrait, 914x411 landscape, 1280x800 tablet landscape, 320x568 with '
               'fontScale 1.5. Synthetic layout, not physical screenshots.',
        device_actions='NONE; no adb, install, phone access, physical hearing or real GPS tests',
        pending=['User engineering review of Draft PR35', 'Real multi-category formal stories actually re-ranked on '
                 'device (0 formal stories today)', 'Source dual mode offline-first/instant-first',
                 'Physical GPS automatic chain and recovery (#23/#24/#27)',
                 'Formal story Visitor Gate and explicit release approval'],
        github='Draft PR35 stays draft and unmerged; no release, no APK upload; Issues 23/24/27/30 keep their state')
    Path('docs/evidence/v052-verification.json').write_text(
        json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(f"Recorded {result['total_tests']} tests, 6 categories x 3 levels, 0 formal stories. STOP; no device actions.")


if __name__ == '__main__':
    main()
