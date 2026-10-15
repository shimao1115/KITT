"""Seal independently versioned text packs; drafts stay in content/, never in APK assets.

This validates structure/integrity, not historical truth or a person's approval.
Run --require-playable before creating a runtimecheck candidate.
"""
import argparse
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'content/yangshen'
ASSETS = ROOT / 'app/src/main/assets/offline'
CATEGORIES = {'NATURE', 'ENGINEERING', 'CULTURE', 'LOCAL_LIFE', 'HISTORY', 'TRAVEL'}

def canonical(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':')).encode('utf-8')

def fingerprint(value, field):
    return hashlib.sha256(canonical({k: v for k, v in value.items() if k != field})).hexdigest()

def read(path):
    return json.loads(path.read_text(encoding='utf-8'))

def encode(value):
    return (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode('utf-8')

def build(require_playable=False, check=False):
    facts = read(SOURCE / 'facts.json')
    stories = read(SOURCE / 'narratives.json')
    approvals = read(SOURCE / 'approvals.json')['approvals']
    assert facts['schema_version'] == stories['schema_version'] == 1
    sources = {s['source_id']: s for s in facts['sources']}
    claims = {c['claim_id']: c for c in facts['claims']}
    assert len(sources) == len(facts['sources']) and len(claims) == len(facts['claims'])
    for s in sources.values():
        assert s['registry_revision'] == fingerprint(s, 'registry_revision'), s['source_id']
    for c in claims.values():
        assert c['revision'] == fingerprint(c, 'revision'), c['claim_id']
        assert c['status'] in {'verified', 'contested', 'lead', 'rejected'}
        for e in c['evidence']:
            assert e['source_id'] in sources and e['locator'] and e['relation'] in {'supports', 'contradicts'}
            assert e['accepted_source_revision'] == sources[e['source_id']]['registry_revision']
    playable = []
    for n in stories['narratives']:
        assert n['primary_category'] in CATEGORIES
        assert len(set(n['secondary_categories'])) == len(n['secondary_categories'])
        assert set(n['secondary_categories']) <= CATEGORIES - {n['primary_category']}
        assert n['story_type'] in {'main', 'followup'}
        assert n['text_sha256'] == hashlib.sha256(n['text'].encode('utf-8')).hexdigest()
        used = set()
        for r in n['supported_claim_refs']:
            c = claims[r['claim_id']]
            assert r['accepted_revision'] == c['revision'] and c['status'] == 'verified'
            assert all(sources[e['source_id']]['access_status'] == 'read' for e in c['evidence'])
            used.add(c['claim_id'])
        assert used and used == {c for p in n['paragraph_claims'] for c in p['claim_ids']}
        if n['review_status'] != 'approved':
            continue
        # A content editor must record real, reviewable user acceptance. Never synthesize one.
        approval = next((a for a in approvals if a['approval_ref'] == n['approval_ref']), None)
        assert approval and approval['narrative_id'] == n['narrative_id']
        assert approval['text_sha256'] == n['text_sha256']
        assert approval['claim_refs_sha256'] == hashlib.sha256(canonical(n['supported_claim_refs'])).hexdigest()
        assert approval['reviewer'] and approval['evidence'] and approval['reviewed_at']
        assert n['fact_gate'] == n['visitor_gate'] == 'passed'
        playable.append(n)
    emitted = {**stories, 'narratives': playable}
    outputs = {}
    for kind, data in [('facts', facts), ('narratives', emitted)]:
        payload = encode(data)
        outputs[f'ys-{kind}.json'] = payload
        outputs[f'ys-{kind}-manifest.json'] = encode(dict(
            schema_version=1, minimum_code=17, pack_id=data['pack_id'],
            content_version=data['content_version'], bytes=len(payload),
            sha256=hashlib.sha256(payload).hexdigest()))
    for name, payload in outputs.items():
        path = ASSETS / name
        if check:
            assert path.read_bytes() == payload, f'Stale output: {name}'
        else:
            path.write_bytes(payload)
    print(f'STRUCTURE PASS: {len(claims)} claims, {len(playable)} approved narratives; '
          f'{len(stories["narratives"]) - len(playable)} drafts excluded. Not a historical/human review.')
    mains = [n for n in playable if n['story_type'] == 'main']
    followups = [n for n in playable if n['story_type'] == 'followup']
    complete_pair = any(m['entity_id'] == e['entity_id'] and
                        {r['claim_id'] for r in e['supported_claim_refs']} -
                        {r['claim_id'] for r in m['supported_claim_refs']}
                        for m in mains for e in followups)
    if require_playable and not complete_pair:
        raise SystemExit('BLOCKED: require user-approved main + followup with new facts. '
                         'Do not produce code17 runtimecheck candidate.')

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    parser.add_argument('--require-playable', action='store_true')
    args = parser.parse_args()
    build(args.require_playable, args.check)
