"""Offline publishing gate regression. Synthetic approvals are temporary fixtures only."""
import copy
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location('builder', Path(__file__).with_name('build-story-packs.py'))
builder = importlib.util.module_from_spec(spec)
spec.loader.exec_module(builder)
ORIGINAL = builder.SOURCE

class PublishingGate(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        builder.SOURCE = self.root / 'content'
        builder.ASSETS = self.root / 'assets'
        builder.SOURCE.mkdir(); builder.ASSETS.mkdir()
        for name in ['facts.json', 'narratives.json', 'approvals.json']:
            (builder.SOURCE / name).write_bytes((ORIGINAL / name).read_bytes())

    def tearDown(self):
        self.temp.cleanup()

    def stories(self):
        return builder.read(builder.SOURCE / 'narratives.json')

    def save(self, data):
        (builder.SOURCE / 'narratives.json').write_bytes(builder.encode(data))

    def test_drafts_never_enter_assets(self):
        builder.build()
        self.assertEqual([], builder.read(builder.ASSETS / 'ys-narratives.json')['narratives'])
        with self.assertRaises(SystemExit):
            builder.build(require_playable=True, check=True)

    def test_self_promotion_without_user_evidence_rejected(self):
        n = self.stories()
        n['narratives'][0].update(review_status='approved', fact_gate='passed', visitor_gate='passed', approval_ref='invented')
        self.save(n)
        with self.assertRaises(AssertionError):
            builder.build()

    def test_approval_cannot_survive_text_or_claim_dependency_change(self):
        n = self.stories(); story = n['narratives'][0]
        story.update(review_status='approved', fact_gate='passed', visitor_gate='passed', approval_ref='TEMPORARY_TEST_ONLY')
        a = dict(approval_ref='TEMPORARY_TEST_ONLY', narrative_id=story['narrative_id'], text_sha256=story['text_sha256'],
                 claim_refs_sha256=builder.hashlib.sha256(builder.canonical(story['supported_claim_refs'])).hexdigest(),
                 reviewer='SYNTHETIC TEST', evidence='SYNTHETIC TEST, not real approval', reviewed_at='fixture')
        (builder.SOURCE / 'approvals.json').write_bytes(builder.encode({'approvals': [a]}))
        self.save(n); builder.build()
        with self.assertRaises(SystemExit):
            builder.build(require_playable=True) # A main alone does not complete this task's pair.
        for key in ['text_sha256', 'claim_refs_sha256']:
            bad = copy.deepcopy(a); bad[key] = '0' * 64
            (builder.SOURCE / 'approvals.json').write_bytes(builder.encode({'approvals': [bad]}))
            with self.assertRaises(AssertionError):
                builder.build()

    def test_changed_source_claim_or_stale_output_detected(self):
        builder.build()
        file = builder.ASSETS / 'ys-narratives.json'
        file.write_bytes(file.read_bytes() + b' ')
        with self.assertRaises(AssertionError):
            builder.build(check=True)
        f = builder.read(builder.SOURCE / 'facts.json'); f['claims'][0]['text'] = 'unreviewed change'
        (builder.SOURCE / 'facts.json').write_bytes(builder.encode(f))
        with self.assertRaises(AssertionError):
            builder.build()

if __name__ == '__main__':
    unittest.main()
