"""Business view generation: exact historical bytes, explicit extensions and rendered labels."""
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('business_spec_renderer', ROOT / 'tools/spec_plan/render.py')
renderer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(renderer)


class SnapshotTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.lf = json.dumps({'title': 'original snapshot', 'value': 1}, indent=2).encode() + b'\n'
        self.original = self.lf.replace(b'\n', b'\r\n')
        self.data = {'workflow_boundary_path': 'boundary.json'}
        self.boundary = {'source_snapshot': 'snapshot.json', 'source_snapshot_sha256': hashlib.sha256(self.original).hexdigest()}
        (self.root / 'boundary.json').write_text(json.dumps(self.boundary))

    def read_snapshot(self, raw):
        (self.root / 'snapshot.json').write_bytes(raw)
        with patch.object(renderer, 'ROOT', self.root):
            return renderer.snapshot(self.data)

    def test_original_crlf_and_git_checkout_lf_share_the_exact_history(self):
        for raw in [self.original, self.lf]:
            with self.subTest(line_endings=raw):
                try: actual = self.read_snapshot(raw)
                except renderer.PlanError as error:
                    self.fail('Git LF/CRLF conversion must not invalidate the same historical snapshot: ' + str(error))
                self.assertEqual({'title': 'original snapshot', 'value': 1}, actual)
        self.assertEqual(self.boundary, json.loads((self.root / 'boundary.json').read_text()))

    def test_field_tampering_and_reformatting_do_not_pass_as_newlines(self):
        for raw in [self.lf.replace(b'original snapshot', b'changed snapshot'),
                    self.lf.replace(b'  "value"', b'    "value"'), self.lf.replace(b'\n', b'\r')]:
            with self.subTest(raw=raw):
                with self.assertRaisesRegex(renderer.PlanError, '历史来源已变更'):
                    self.read_snapshot(raw)


class CurrentCatalogTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.requirements = [{'id': 'FIXTURE', 'acceptance': ['original acceptance']}]
        self.lf = (json.dumps({'requirements': self.requirements}, indent=2) + chr(10)).encode()
        self.crlf = self.lf.replace(bytes([10]), bytes([13, 10]))

    def load_catalog(self, raw, expected):
        index = self.root / 'index.json'
        index.write_text(json.dumps({'catalog_path': 'catalog.json', 'catalog_sha256': hashlib.sha256(expected).hexdigest()}))
        (self.root / 'catalog.json').write_bytes(raw)
        with patch.object(renderer, 'ROOT', self.root), patch.object(renderer, 'INDEX', index):
            return renderer.load()

    def test_current_catalog_accepts_only_equivalent_lf_and_crlf_for_either_recorded_digest(self):
        for expected in [self.lf, self.crlf]:
            for raw in [self.lf, self.crlf]:
                with self.subTest(expected=expected, actual=raw):
                    try: index, requirements = self.load_catalog(raw, expected)
                    except renderer.PlanError as error:
                        self.fail('Equivalent Git line endings must preserve the recorded current catalog: ' + str(error))
                    self.assertEqual(self.requirements, requirements)
                    self.assertEqual(hashlib.sha256(expected).hexdigest(), index['catalog_sha256'])
                    self.assertEqual(raw, (self.root / 'catalog.json').read_bytes(), 'Validation must not rewrite the source')

    def test_current_catalog_rejects_acceptance_tampering_reformatting_and_bare_cr(self):
        for raw in [self.lf.replace(b'original acceptance', b'changed acceptance'),
                    self.lf.replace(b'  "requirements"', b'    "requirements"'),
                    self.lf.replace(bytes([10]), bytes([13]))]:
            with self.subTest(raw=raw):
                with self.assertRaisesRegex(renderer.PlanError, '需求来源已变更'):
                    self.load_catalog(raw, self.crlf)


class CurrentBusinessViewsTests(unittest.TestCase):
    def current(self):
        return renderer.load()

    def test_history_routes_remain_exact_and_six_registered_points_are_counted_once(self):
        data, requirements = self.current()
        historical = renderer.boundary(data)
        current = renderer.current_boundary(data, requirements)
        self.assertEqual(170, len(historical['acceptance_routes']))
        for ref, row in historical['acceptance_routes'].items():
            self.assertEqual(row, current['acceptance_routes'][ref])
        self.assertEqual(176, len(current['acceptance_routes']))
        summary, covered, _ = renderer.validate(data, requirements)
        self.assertEqual((170, 6, 176), (summary['original_acceptance_points'], summary['new_acceptance_points'], summary['registered_acceptance_points']))
        for requirement, expected_spec in [('IAM-07', 'S081'), ('SUP-04', 'S082')]:
            for criterion in range(1, 4):
                self.assertTrue(covered[requirement + '.AC' + str(criterion)])
                self.assertEqual({expected_spec}, {scene.split('.')[0] for scene in covered[requirement + '.AC' + str(criterion)]})
        for ref in ['SUP-01.AC1', 'SUP-01.AC2', 'SUP-01.AC3']:
            self.assertTrue(covered[ref], 'Original notification acceptance must remain covered')
            self.assertFalse(any(scene.startswith('S081.') for scene in covered[ref]), 'Rebinding is provenance, not double business coverage')
        self.assertEqual(170, len(historical['acceptance_routes']), 'The historical routing object must not be mutated')

    def test_unregistered_new_requirement_cannot_be_implicitly_approved(self):
        data, requirements = self.current()
        data['business_acceptance_refs'].append('UNAPPROVED.AC1')
        requirements.append({'id': 'UNAPPROVED', 'acceptance': ['Not approved']})
        with self.assertRaisesRegex(renderer.PlanError, '新增要求未明确登记'):
            renderer.validate(data, requirements)

    def test_missing_extension_registration_is_rejected(self):
        data, requirements = self.current()
        data.pop('current_catalog_extension')
        with self.assertRaisesRegex(renderer.PlanError, '新增要求未明确登记'):
            renderer.validate(data, requirements)

    def test_unregistered_new_specification_is_rejected(self):
        data, requirements = self.current()
        extra = copy.deepcopy(next(row for row in data['specifications'] if row['id'] == 'S082'))
        extra['id'] = 'S083'
        data['specifications'].append(extra)
        with self.assertRaisesRegex(renderer.PlanError, '新增任务未明确登记'):
            renderer.validate(data, requirements)

    def test_missing_technical_reference_is_rejected(self):
        data, requirements = self.current()
        next(row for row in data['specifications'] if row['id'] == 'S082')['scenarios'][0].pop('technical_reference')
        with self.assertRaisesRegex(renderer.PlanError, '精确约束缺失'):
            renderer.validate(data, requirements)

    def test_historical_source_rebinding_cannot_drift(self):
        data, requirements = self.current()
        next(row for row in data['specifications'] if row['id'] == 'S081')['scenarios'][0]['source_acceptance_refs'] = ['SUP-01.AC1']
        with self.assertRaisesRegex(renderer.PlanError, '来源重绑依据不匹配'):
            renderer.validate(data, requirements)

    def test_confirmed_statuses_render_in_markdown_and_actual_html_policy_function(self):
        node = shutil.which('node')
        if not node: self.skipTest('Node is required to evaluate the HTML policy function')
        data, requirements = self.current()
        summary, covered, levels = renderer.validate(data, requirements)
        outputs = renderer.views(data, requirements, summary, covered, levels)
        markdown = outputs['spec/policies.md']
        for label in ['用户已确认（2026-11-15）', '用户已确认（2026-11-16）', '默认值已确认；外部数据时效仍为示例', '待评审建议']:
            self.assertIn('- 状态：' + label, markdown)
        html = outputs['docs/spec-topology.html']
        declarations = re.search(r'const policyStatuses=.*?;', html).group()
        escape = next(line for line in html.splitlines() if line.startswith('const esc='))
        function = html[html.index('function policyText('):html.index('function fixtures(')]
        program = declarations + '\nconst policies=new Map(' + json.dumps(data['policies'], ensure_ascii=False) + '.map(p=>[p.id,p]));\n' + escape + '\n' + function + "\nconsole.log(policyText(['P02','P06','P10','P16']));"
        run = subprocess.run([node, '-e', program], capture_output=True, text=True, encoding='utf-8', timeout=10)
        self.assertEqual(0, run.returncode, run.stderr)
        for label in ['用户已确认（2026-11-15）', '用户已确认（2026-11-16）', '默认值已确认；外部数据时效仍为示例', '待评审建议']:
            self.assertIn(label, run.stdout)
        self.assertNotIn('undefined', run.stdout)
        self.assertIn('S082', outputs['spec/README.md'])
        self.assertIn('S082.SC03', html)
        self.assertNotIn('统计计算方式仍按[待确认规则]', outputs['docs/17-spec-topology-and-assignment.md'])
        self.assertIn('当前合计登记176条', outputs['docs/17-spec-topology-and-assignment.md'])


if __name__ == '__main__': unittest.main()
