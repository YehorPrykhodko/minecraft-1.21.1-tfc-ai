import unittest
import tomllib
from migrate_config_v3 import convert
class MigrationTest(unittest.TestCase):
    def test_custom_values_and_independent_species(self):
        old={'behavior':{'hyenaPackPlayerHp':.55,'adultBearChaseMultiplier':1.6}}
        output,_=convert(old);new=tomllib.loads(output)
        self.assertEqual(new['species']['tfc:hyena']['playerHpHuntThreshold'],.55)
        self.assertEqual(new['species']['tfc:grizzly_bear']['chaseSpeed'],1.6)
        self.assertNotIn('tfc:wolf',new['species']);self.assertEqual(old['behavior']['hyenaPackPlayerHp'],.55)
    def test_defaults_and_unsupported_are_explicit(self):
        text,notes=convert({'behavior':{'panicExit':.2,'hyenaSoloChance':.02}})
        self.assertNotIn('soloHuntChance',text);self.assertTrue(any('panicExit' in n for n in notes))
    def test_invalid_is_not_silently_clamped(self):
        with self.assertRaises(ValueError):convert({'behavior':{'combatHoldTicks':2}})
if __name__=='__main__':unittest.main()
