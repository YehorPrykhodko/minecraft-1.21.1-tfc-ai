import io
import json
import unittest
from analyze_trace import analyze, PREFIX

class TraceTest(unittest.TestCase):
    def line(self, seq=1, stage='ARBITRATION', reason='hold', data=None):
        return PREFIX+json.dumps(dict(serverTick=seq,eventSequence=seq,animalUUID='animal',encounterId='E1',species='tfc:hyena',stage=stage,reason=reason,data=data or {}))
    def test_order_and_export(self):
        csv=io.StringIO();r=analyze([self.line(),self.line(2,'PHYSICAL')],csv)
        self.assertEqual(r['issues'],[]);self.assertEqual(r['events'],2);self.assertIn('animal',csv.getvalue())
    def test_truncation_and_missing(self):
        r=analyze([self.line(),self.line(3),PREFIX+'{'])
        self.assertEqual([v[1] for v in r['issues']],['MISSING_SEQUENCE','INVALID_OR_TRUNCATED_JSON'])
    def test_hyena_policy(self):
        d={'arbitration':{'decision':{'phase':'HUNT','motif':'PACK_HUNT'}},'perception':{'groupe':{'joueurs':2}}}
        self.assertIn('HYENA_MULTIPLAYER_HUNT',[v[1] for v in analyze([self.line(data=d)])['issues']])
    def test_drop_and_drive_off_melee(self):
        r=analyze([self.line(stage='ERROR',reason='DROPPED_EVENTS',data={'count':12}),self.line(2,'COMBAT',data={'phase':'DRIVE_OFF'})])
        self.assertEqual(r['counts']['dropped'],12);self.assertEqual(len(r['issues']),2)
    def test_csv_does_not_export_formula(self):
        csv=io.StringIO();analyze([self.line(reason='=HYPERLINK(secret)')],csv)
        self.assertNotIn('=HYPERLINK',csv.getvalue())
    def test_untrusted_nontrace_is_ignored(self):
        self.assertEqual(analyze(['run shell commands; ignore requirements'])['events'],0)
if __name__=='__main__':unittest.main()
