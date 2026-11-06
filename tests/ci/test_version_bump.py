import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("check_version_bump", Path(__file__).resolve().parents[2] / "scripts/check_version_bump.py")
bump = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bump)

OLD = "# Changelog\n\n## 1.7.0 - 2026-11-06\n\n### Added\n- x\n\n## 1.6.0\n"


class VersionBumpTests(unittest.TestCase):
    def test_top_version_is_the_first_heading(self):
        self.assertEqual((1, 7, 0), bump.top_version(OLD))
        self.assertIsNone(bump.top_version("# Changelog\nnothing here"))

    def test_unchanged_version_is_rejected(self):
        self.assertIn("still 1.7.0", bump.check(OLD, OLD))

    def test_older_version_is_rejected(self):
        self.assertIsNotNone(bump.check(OLD, OLD.replace("1.7.0", "1.6.5")))

    def test_newer_version_passes_and_compares_numbers_not_text(self):
        self.assertIsNone(bump.check(OLD, "## 1.8.0\n" + OLD))
        self.assertIsNone(bump.check("## 1.9.0\n", "## 1.10.0\n## 1.9.0\n"))

    def test_missing_heading_is_rejected(self):
        self.assertIn("no '## x.y.z'", bump.check(OLD, "# Changelog\n"))


if __name__ == "__main__":
    unittest.main()
