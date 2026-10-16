"""Record actual code18 local verification outputs; never access a connected device."""
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
        assert suite.attrib.get('timestamp', '').startswith('2026-10-16'), 'Historical XML cannot count as code18 execution'
        item = {k: int(suite.attrib[k]) for k in ('tests', 'failures', 'errors', 'skipped')}
        assert item['tests'] > 0 and not any(item[k] for k in ('failures', 'errors', 'skipped'))
        item.update(name=suite.attrib['name'], timestamp=suite.attrib.get('timestamp'),
                    report_sha256=digest(path), cases=[c.attrib['name'] for c in suite.findall('testcase')])
        results.append(item)
    assert len(results) > 60
    return dict(tests=sum(s['tests'] for s in results), failures=0, errors=0, skipped=0, suites=results)


def apk(path, package):
    sdk = Path(os.environ.get('ANDROID_HOME') or os.environ['ANDROID_SDK_ROOT']) / 'build-tools/35.0.0'
    badging = command(str(sdk / 'aapt.exe'), 'dump', 'badging', path)
    header = badging.splitlines()[0]
    assert f"name='{package}'" in header
    if package != 'com.kitt.reader.runtimecheck.test':
        assert "versionCode='18'" in header and "versionName='0.5.1'" in header
    verify = command(str(sdk / 'apksigner.bat'), 'verify', '--verbose', '--print-certs', path)
    cert = re.search(r'Signer #1 certificate SHA-256 digest: ([a-f0-9]+)', verify).group(1)
    assert cert == CERT
    result = {**file_info(path), 'package_header': header, 'certificate_sha256': cert}
    if not package.endswith('.test'):
        with zipfile.ZipFile(path) as z:
            vcs_path = 'META-INF/version-control-info.textproto'
            if vcs_path in z.namelist():
                result['embedded_vcs_base'] = z.read(vcs_path).decode('utf-8')
            facts = json.loads(z.read('assets/offline/xindu-facts.json'))
            narratives = json.loads(z.read('assets/offline/ys-narratives.json'))
            assert len(facts['objects']) == 6 and sum(len(o['facts']) for o in facts['objects']) == 22
            assert not narratives['narratives']
            result.update(browsable_objects=6, browsable_basic_facts=22, formal_stories=0,
                          narrative_asset_sha256=hashlib.sha256(z.read('assets/offline/ys-narratives.json')).hexdigest())
    return result


def main():
    verification = json.loads(Path('artifacts/verification.json').read_text(encoding='utf-8-sig'))
    assert verification['version'] == '0.5.1'
    variants = {v: suites(v) for v in ('Debug', 'Release')}
    for v in variants:
        assert variants[v]['tests'] == verification['variants'][v]['tests']
    lint = {}
    for v in ('debug', 'release', 'runtimecheck'):
        path = Path('artifacts/runtime-smoke-workspace/app/build/reports/lint-results-debug.xml') if v == 'runtimecheck' else Path(f'app/build/reports/lint-results-{v}.xml')
        issues = ET.parse(path).getroot().findall('issue')
        assert not any(i.attrib['severity'] in ('Error', 'Fatal') for i in issues)
        lint[v] = {**file_info(path), 'errors': 0, 'warnings': len(issues),
                   'warning_ids': [i.attrib['id'] for i in issues]}
    simulations = []
    for name in ('full-simulation', 'accelerated-simulation', 'batch-simulation', 'landmark-simulation',
                 'dossier-simulation', 'driving-area-resolution'):
        path = Path(f'app/build/acceptance/{name}.txt')
        text = path.read_text(encoding='utf-8-sig')
        assert datetime.fromtimestamp(path.stat().st_mtime).date().isoformat() == '2026-10-16'
        if name == 'driving-area-resolution':
            assert text.count('minInterval=16000ms') == 4
            assert all(any(s['name'] == 'com.kitt.reader.DrivingAreaResolutionTest' for s in v['suites'])
                       for v in variants.values())
        else:
            assert 'PASS' in text
        simulations.append({**file_info(path), 'summary': text[:2200],
                            'scope': 'deterministic/Fake research/director/voice, not physical GPS or human hearing'})
    outputs = [apk(f'artifacts/kitt-v0.5.1-{variant}.apk', package) for variant, package in
               [('debug', 'com.kitt.reader'), ('release', 'com.kitt.reader'),
                ('runtimecheck', 'com.kitt.reader.runtimecheck')]]
    outputs.append(apk('artifacts/runtime-smoke-workspace/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk',
                       'com.kitt.reader.runtimecheck.test'))
    baseline = Path('artifacts/kitt-v0.5.0-runtimecheck.apk')
    assert digest(baseline) == '9d2d2b312c669e0469113e80a54164e3217519c77a6692c80037b4b98c776a6d'
    sdk = Path(os.environ.get('ANDROID_HOME') or os.environ['ANDROID_SDK_ROOT']) / 'build-tools/35.0.0'
    baseline_header = command(str(sdk / 'aapt.exe'), 'dump', 'badging', str(baseline)).splitlines()[0]
    assert "name='com.kitt.reader.runtimecheck'" in baseline_header and "versionCode='17'" in baseline_header
    baseline_signature = command(str(sdk / 'apksigner.bat'), 'verify', '--print-certs', str(baseline))
    assert f'Signer #1 certificate SHA-256 digest: {CERT}' in baseline_signature
    upgrade = {**file_info(baseline), 'package_header': baseline_header, 'certificate_sha256': CERT,
               'result': 'Same package/certificate; versionCode17 to18 is monotonic. Static compatibility only; installation NOT RUN.'}
    inputs = {}
    for folder in ('app/src/main/java', 'app/src/main/assets/offline', 'content/yangshen'):
        for path in sorted(Path(folder).rglob('*')):
            if path.is_file():
                inputs[path.as_posix()] = digest(path)
    mismatches = [p for p, sha in inputs.items() if digest(Path('artifacts/runtime-smoke-workspace') / p) != sha]
    assert not mismatches
    logs = {}
    for name in ('verify', 'runtime-build', 'signing', 'content'):
        path = Path(f'artifacts/v051-{name}.log')
        logs[name] = file_info(path)
        text = path.read_text(encoding='utf-8-sig', errors='replace')
        if name in ('verify', 'runtime-build'):
            assert 'BUILD SUCCESSFUL' in text and 'BUILD FAILED' not in text
        if name == 'signing':
            assert 'PASS: signing regression (2 accepted cases, 6 rejected cases).' in text
        if name == 'content':
            assert 'Ran 7 tests' in text and 'EXPECTED BLOCKED' in text and 'EXPECTED REJECTED' in text
    assert not command('git', 'diff', '465d582', '--', 'content', 'app/src/main/assets/offline',
                       'app/src/main/java/com/kitt/reader/TripStore.kt',
                       'app/src/main/java/com/kitt/reader/SettingsStore.kt').strip()
    result = dict(
        date='2026-10-16', version='0.5.1', version_code=18, status='ENGINEERING-STOP-USER-ACCEPTANCE-PENDING',
        implementation_commit=command('git', 'rev-parse', 'HEAD').strip(),
        build_provenance='APKs built from the implementation working tree before its milestone commit. source_sha256 records those actual production bytes; embedded AGP VCS metadata records the earlier branch base.',
        recorded_at=datetime.now(timezone(timedelta(hours=8))).isoformat(),
        variants=variants, total_tests=sum(v['tests'] for v in variants.values()), python_tests=7,
        content=dict(browsable_counties=1, browsable_objects=6, browsable_basic_facts=22, loaded_legacy_sources=6,
                     formal_stories=0, fixture_stories=2, release_gate='BLOCKED',
                     formal_require_playable_exit=1, candidate_without_separate_output_exit=2,
                     manuscripts='candidate / visitor_gate=pending; unchanged',
                     fixture_scope='in-memory TEST_ONLY approval; no promotion in source or APK'),
        lint=lint, simulations=simulations, apks=outputs, logs=logs, upgrade_baseline=upgrade,
        signing=dict(accepted_cases=2, rejected_cases=6, certificate_sha256=CERT),
        source_sha256=inputs, runtime_copy_mismatches=mismatches,
        compatibility='TripStore/SettingsStore and content bytes unchanged from main; synthetic 20-history/settings hash regression. No phone data accessed.',
        layout='Compose/Robolectric: 411x914 portrait, 914x411 landscape, 1280x800 tablet landscape, 320x568 + fontScale1.5. Not physical screenshots.',
        device_actions='NONE; no adb, install, phone access or physical hearing tests',
        github='Draft PR31 remains draft/unmerged; no release or APK upload; Issues23/24/27 remain open',
        pending=['User engineering review', 'Separately authorized code17 to code18 in-place phone upgrade and device UI verification',
                 'Physical GPS automatic chain and recovery (#23/#24/#27)', 'Formal story Visitor Gate and explicit release approval'])
    Path('docs/evidence/v051-verification.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(f"Recorded {result['total_tests']} tests; 6 objects / 22 facts / 0 formal stories. STOP; no device actions.")


if __name__ == '__main__':
    main()
