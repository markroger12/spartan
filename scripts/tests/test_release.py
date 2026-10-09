import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('release', Path(__file__).parents[1] / 'release.py')
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)

class CandidateIntegrityTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.payload = self.root / 'plugin.jar'
        self.payload.write_bytes(b'candidate payload')
        manifest = dict(stage='development-candidate', status={'folia_enabled': False}, files={'plugin.jar':release.digest(self.payload)})
        (self.root / 'release-manifest.json').write_text(json.dumps(manifest))
        self.checksums()
    def checksums(self):
        (self.root / 'SHA256SUMS').write_text(''.join(f'{release.digest(p)}  {p.name}\n' for p in sorted(self.root.iterdir()) if p.name != 'SHA256SUMS'))
    def test_valid_inventory(self):
        self.assertEqual('development-candidate', release.verify(self.root)['stage'])
    def test_tampered_missing_and_unexpected_files_fail(self):
        self.payload.write_bytes(b'tampered')
        with self.assertRaises(ValueError): release.verify(self.root)
        self.payload.unlink()
        with self.assertRaises(ValueError): release.verify(self.root)
        self.payload.write_bytes(b'candidate payload')
        (self.root / 'unexpected').write_text('unlisted')
        with self.assertRaises(ValueError): release.verify(self.root)
    def test_traversal_and_symlink_fail(self):
        (self.root / 'SHA256SUMS').write_text('0'*64+'  ../outside\n')
        with self.assertRaises(ValueError): release.verify(self.root)
        self.payload.unlink(); self.payload.symlink_to('/etc/hosts'); self.checksums()
        with self.assertRaises(ValueError): release.verify(self.root)
    def test_duplicated_checksum_and_false_certification_fail(self):
        p=self.root / 'SHA256SUMS';p.write_text(p.read_text()+p.read_text())
        with self.assertRaises(ValueError):release.verify(self.root)
        manifest=json.loads((self.root/'release-manifest.json').read_text());manifest['status']['folia_enabled']=True
        (self.root/'release-manifest.json').write_text(json.dumps(manifest));self.checksums()
        with self.assertRaises(ValueError):release.verify(self.root)

if __name__ == '__main__': unittest.main()
