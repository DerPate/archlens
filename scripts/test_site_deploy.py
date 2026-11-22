"""Regression checks for site/deploy.sh. rsync, npm and Doxygen are stubs, so nothing is deployed."""

import os
import re
import shutil
import subprocess
import tarfile
import tempfile
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent

DOXYGEN = """#!/bin/sh
echo "doxygen cwd=$PWD version=${ARCHLENS_VERSION:-}" >> "$CALLS"
[ -n "${EMPTY_API:-}" ] && exit 0
mkdir -p site/public/api
echo api > site/public/api/index.html
"""
NPM = """#!/bin/sh
echo "npm $*" >> "$CALLS"
[ -n "${FAIL_BUILD:-}" ] && exit 1
rm -rf dist && mkdir dist
cp -R public/. dist/
"""
RSYNC = """#!/bin/sh
echo "rsync $*" >> "$CALLS"
"""


def executable(path, content):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content)
    path.chmod(0o755)
    return path


class SiteDeployTests(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.temp = Path(temp.name)
        self.repo = self.temp / "repo"
        (self.repo / "site" / "public").mkdir(parents=True)
        for name in ("site/deploy.sh", "site/robots.txt", "Doxyfile", "pom.xml"):
            shutil.copy(REPO / name, self.repo / name)
        stubs = self.temp / "bin"
        self.doxygen = executable(stubs / "doxygen", DOXYGEN)
        executable(stubs / "npm", NPM)
        executable(stubs / "rsync", RSYNC)
        self.calls = self.temp / "calls"
        self.calls.touch()
        self.env = dict(os.environ, PATH=f"{stubs}{os.pathsep}{os.environ['PATH']}", CALLS=str(self.calls))
        self.env.pop("DOXYGEN", None)
        self.env.pop("ARCHLENS_VERSION", None)

    def deploy(self, script="site/deploy.sh", cwd=None, **env):
        result = subprocess.run(
            ["bash", script, "nobody@invalid", "/nowhere"],
            cwd=cwd or self.repo,
            env={**self.env, **env},
            capture_output=True,
            text=True,
            check=False,
        )
        return result.returncode, self.calls.read_text()

    def assert_not_deployed(self, outcome):
        code, calls = outcome
        self.assertNotEqual(code, 0)
        self.assertNotIn("rsync", calls)

    def test_runs_from_repo_root(self):
        code, calls = self.deploy()
        self.assertEqual(code, 0)
        self.assertIn(f"doxygen cwd={self.repo.resolve()} ", calls)
        self.assertIn("rsync", calls)

    def test_runs_from_site_directory(self):
        code, calls = self.deploy("./deploy.sh", cwd=self.repo / "site")
        self.assertEqual(code, 0)
        self.assertIn(f"doxygen cwd={self.repo.resolve()} ", calls)

    def test_uses_bundled_doxygen(self):
        bundled = executable(self.temp / "bundle" / "doxygen-9.9.9" / "bin" / "doxygen", DOXYGEN + "echo bundled >> \"$CALLS\"\n")
        with tarfile.open(self.repo / "doxygen-9.9.9.linux.bin.tar.gz", "w:gz") as archive:
            archive.add(bundled.parents[1], arcname="doxygen-9.9.9")
        code, calls = self.deploy()
        self.assertEqual(code, 0)
        self.assertIn("bundled", calls)

    def test_missing_doxygen_never_rsyncs(self):
        self.assert_not_deployed(self.deploy("./deploy.sh", cwd=self.repo / "site", DOXYGEN=str(self.temp / "no-such-doxygen")))

    def test_empty_api_output_never_rsyncs(self):
        self.assert_not_deployed(self.deploy("./deploy.sh", cwd=self.repo / "site", EMPTY_API="1"))

    def test_failed_build_never_rsyncs(self):
        self.assert_not_deployed(self.deploy("./deploy.sh", cwd=self.repo / "site", FAIL_BUILD="1"))

    def test_stale_api_is_not_shipped(self):
        stale = self.repo / "site" / "public" / "api" / "stale.html"
        stale.parent.mkdir(parents=True)
        stale.write_text("old")
        code, _ = self.deploy("./deploy.sh", cwd=self.repo / "site")
        self.assertEqual(code, 0)
        self.assertFalse(stale.exists())
        self.assertFalse((self.repo / "site" / "dist" / "api" / "stale.html").exists())

    def test_passes_project_version(self):
        version = re.search(r"<version>([^<]+)</version>", (REPO / "pom.xml").read_text()).group(1)
        _, calls = self.deploy("./deploy.sh", cwd=self.repo / "site")
        self.assertIn(f"version={version}\n", calls)
        self.assertRegex((REPO / "Doxyfile").read_text(), r"(?m)^PROJECT_NUMBER\s*=\s*\$\(ARCHLENS_VERSION\)")


if __name__ == "__main__":
    unittest.main()
