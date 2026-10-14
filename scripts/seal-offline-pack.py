"""After human source review, seal the two committed Xindu assets. No network or credentials.
Hashes detect accidental corruption, not malicious replacement of a manifest; update via signed APK.
"""
import hashlib
import json
from pathlib import Path

root = Path(__file__).resolve().parents[1] / 'app/src/main/assets/offline'
manifest = {
    'schema_version': 1, 'version': 'xindu-2026.10.14-1', 'minimum_code': 14,
    'geometry_year': '2017', 'boundary_margin_meters': 1000,
    'geometry_source': 'https://www.geoboundaries.org/api/current/gbOpen/CHN/ADM3/',
    'geometry_release': '9469f09 / CHN-ADM3-62558664 / 62558664B25738263309049',
    'geometry_license': 'ODbL-1.0',
    'attribution': '© OpenStreetMap contributors; Lee Beryman; geoBoundaries',
    'license_url': 'https://opendatacommons.org/licenses/odbl/1-0/',
    'geometry_limit': '2017 community boundary, not official surveyed or verified current; no township geometry',
    'updated_at': '2026-10-14',
}
for kind, name in [('facts', 'xindu-facts.json'), ('geometry', 'xindu-geometry.geojson')]:
    data = (root / name).read_bytes()
    json.loads(data)
    manifest[kind + '_sha256'] = hashlib.sha256(data).hexdigest()
    manifest[kind + '_bytes'] = len(data)
facts = json.loads((root / 'xindu-facts.json').read_bytes())
manifest['object_count'] = len(facts['objects'])
manifest['fact_count'] = sum(len(obj['facts']) for obj in facts['objects'])
(root / 'xindu-manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(json.dumps(manifest, ensure_ascii=True, indent=2))
