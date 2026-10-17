"""Fresh PR43 trust-source repair evidence; previous 1288 tests remain historical."""
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
OUT = Path('artifacts/d2-production-review-fix')
BASE = 'baea614ba02f1aad7776e65ed6766833ee451b3f'


def git(*args):
    return subprocess.check_output(['git', *args], encoding='utf-8').strip()


def load(path):
    return json.loads(Path(path).read_text(encoding='utf-8-sig'))


def info(path, since=None):
    p = Path(path)
    assert p.is_file(), path
    if since is not None:
        assert p.stat().st_mtime >= since, f'stale {path}'
    return dict(path=p.as_posix(), bytes=p.stat().st_size, sha256=hashlib.sha256(p.read_bytes()).hexdigest())


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
    assert not git('status', '--porcelain'), 'commit repair/docs/recorder first'
    assert not git('diff', BASE, '--', 'app/src/main', 'content/yangshen')
    assert load('config/pack-publishers.json') == []
    started = datetime.fromisoformat((OUT / 'run-start.txt').read_text(encoding='utf-8-sig').strip())
    checks = load(OUT / 'checks.json')
    assert {c['name'] for c in checks} == {'debug', 'release', 'packtool', 'build-lint'}
    tests = {}
    for check in checks:
        assert check['exit'] == 0
        check['log'] = info(OUT / (check['name'] + '.log'), started.timestamp())
        assert 'BUILD SUCCESSFUL' in (OUT / (check['name'] + '.log')).read_text(encoding='utf-8-sig')
        if check['name'] == 'build-lint':
            continue
        directory = 'packtool/build/test-results/test' if check['name'] == 'packtool' else f'app/build/test-results/test{check["name"].title()}UnitTest'
        rows = []
        for path in sorted(Path(directory).glob('TEST-*.xml')):
            suite = ET.parse(path).getroot()
            counts = {k: int(suite.attrib[k]) for k in ('tests', 'failures', 'errors', 'skipped')}
            assert counts['tests'] and not any(counts[k] for k in ('failures', 'errors', 'skipped')), path
            row = dict(name=suite.attrib['name'], **counts,
                       report=info(path, datetime.fromisoformat(check['started_at']).timestamp()))
            if row['name'].startswith('com.kitt.reader.d1.'):
                row['cases'] = [c.attrib['name'] for c in suite.findall('testcase')]
            rows.append(row)
        assert rows
        if check['name'] != 'packtool':
            counts = {r['name']: r['tests'] for r in rows}
            assert counts['com.kitt.reader.d1.ProductionD2Test'] == 66
            assert counts['com.kitt.reader.d1.ReleaseD2Test'] == 19
            assert counts['com.kitt.reader.d1.ReleaseD1Test'] == 27
        tests[check['name']] = dict(tests=sum(r['tests'] for r in rows), failures=0, errors=0, skipped=0, suites=rows)
    before = load(OUT / 'protected-before.json')
    for row in before:
        assert info(row['path'])['sha256'] == row['sha256'], row['path']
    tracked = git('ls-files').splitlines()
    assert not any(p.endswith(('.pem', '.p12', '.jks', '.keystore')) for p in tracked)
    assert all(('-----BEGIN ' + 'PRIVATE KEY-----').encode() not in Path(p).read_bytes() for p in tracked)
    auxiliary = load(OUT / 'auxiliary.json')
    assert all(v == 0 for v in auxiliary.values())
    assert 'Ran 7 tests' in (OUT / 'python.log').read_text(encoding='utf-8-sig')
    assert '\nOK' in (OUT / 'python.log').read_text(encoding='utf-8-sig')
    assert '0 eligible narratives' in (OUT / 'content.log').read_text(encoding='utf-8-sig')
    signing_log = (OUT / 'signing.log').read_text(encoding='utf-8-sig')
    assert signing_log.count('PASS: rejected') == 6 and 'versionCode=20' in signing_log
    fake = load('app/build/acceptance/d2-production-fake.json')
    assert fake['publish']['simulation'] and not fake['publish']['upload_performed']
    assert fake['publish']['human_decisions'] == 1 and fake['publish']['signature_verified']
    assert not fake['real_human_approval'] and not fake['private_material_exported']
    assert fake['registry_requests'] and all(r[:6] == ['gh', 'api', '--hostname', 'github.com', '--method', 'GET'] for r in fake['registry_requests'])
    boundary = load(OUT / 'boundary.json')
    assert not boundary['default_production_identity_exists']
    evidence = dict(issue=42, pull_request=43, review='https://github.com/shimao1115/KITT/pull/43#issuecomment-6236842986',
                    reviewed_head='4911a485c41fa83e8c5baf4facef23fefa6dc6c7', source_commit=git('rev-parse', 'HEAD'),
                    started_at=started.isoformat(), recorded_at=datetime.now(timezone.utc).isoformat(),
                    trust_source='fixed authenticated github.com/shimao1115/KITT remote main; contents pinned to returned main SHA; reread after decision; no candidate/local-ref fallback',
                    tests=tests, totals={**{k: v['tests'] for k, v in tests.items()}, 'all': sum(v['tests'] for v in tests.values())},
                    checks=checks, auxiliary=auxiliary,
                    auxiliary_logs=[info(OUT / p, started.timestamp()) for p in ('python.log', 'content.log', 'signing.log')],
                    static_runtime_fixture=info('artifacts/kitt-v0.6.0-runtimecheck.apk'),
                    fake_gh=fake, fake_report=info('app/build/acceptance/d2-production-fake.json', started.timestamp()),
                    simulations=[info(Path('app/build/acceptance', p), started.timestamp()) for p in ('full-simulation.txt', 'accelerated-simulation.txt', 'batch-simulation.txt', 'landmark-simulation.txt', 'dossier-simulation.txt', 'driving-area-resolution.txt')],
                    protected_files=before, protected_hashes_equal=True, registry=[], boundary=boundary,
                    public_releases_before=load(OUT / 'releases-before.json'), public_releases_after=load(OUT / 'releases-after.json'),
                    real_identity_or_root_created=False, real_human_publication_approval=False, real_tag_release_upload=False,
                    phone_adb_install=False, merge=False, physical_two_machines='PENDING', real_xindu='BLOCKED; formal stories 0',
                    previous_1288_tests='historical reviewed head only; neither inherited nor added',
                    unchanged_host_input_tests='PS5/PS7 wrapper unchanged; previous head evidence retained, not claimed rerun',
                    preliminary_focused=load(OUT / 'focused-debug-check.json'),
                    limitations=['local JVM/fake gh validation only, not external CI or actual gh Release',
                                 'authorization of main relies on repository access/merge policy; first actual registration separately authorized',
                                 'no distributed same-county lock; serialize publication',
                                 'stdin does not authenticate the human; host must relay actual consent'])
    assert evidence['public_releases_before'] == evidence['public_releases_after']
    assert evidence['static_runtime_fixture']['sha256'] == '2d8326eb295e642a8247715bdd13fa5c89c2081cdab071ba325735fea4fe5d60'
    text = json.dumps(scrub(evidence), ensure_ascii=False, indent=2) + '\n'
    assert not re.search(r'[A-Za-z]:[/\\]', text) and 'AppData' not in text and 'PRIVATE KEY' not in text
    Path('docs/evidence/v060-d2-production-review-fix.json').write_text(text, encoding='utf-8')
    print(f'PR43 repair: {evidence["totals"]["all"]} fresh passing tests')


if __name__ == '__main__':
    main()
