"""Unit tests for the public-Javadoc declaration parser and validator.

The `ValidationTests` and `DeclarationImmutabilityTests` classes exercise
only the pure-parsing core (Declaration, find_public_declarations,
validate_source). `GitIntegrationTests` drives the Git-diff/CLI layer
end-to-end against real temporary Git repositories.
"""

import contextlib
import importlib.util
import io
import subprocess
import sys
import tempfile
import unittest
from dataclasses import FrozenInstanceError
from pathlib import Path
from unittest import mock

spec = importlib.util.spec_from_file_location(
    "check_public_javadoc", Path(__file__).with_name("check-public-javadoc.py")
)
check_public_javadoc = importlib.util.module_from_spec(spec)
spec.loader.exec_module(check_public_javadoc)

Declaration = check_public_javadoc.Declaration
find_public_declarations = check_public_javadoc.find_public_declarations
validate_source = check_public_javadoc.validate_source

SCRIPT_PATH = Path(__file__).with_name("check-public-javadoc.py")

_DOCUMENTED_BASELINE = (
    "/**\n"
    " * Example type.\n"
    " */\n"
    "public class Example {\n"
    "}\n"
)

_WITH_UNDOCUMENTED_METHOD = (
    "/**\n"
    " * Example type.\n"
    " */\n"
    "public class Example {\n"
    "    public String parse(String value) {\n"
    "        return value;\n"
    "    }\n"
    "}\n"
)


def _git(args: list[str], cwd: Path) -> str:
    result = subprocess.run(
        ["git", *args],
        cwd=cwd,
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        raise AssertionError(f"git {args} failed: {result.stderr}")
    return result.stdout


def _init_repo(repo: Path) -> None:
    _git(["init"], repo)
    _git(["config", "user.email", "test@example.com"], repo)
    _git(["config", "user.name", "Test"], repo)


def _run_checker(repo: Path, *args: str) -> subprocess.CompletedProcess:
    return subprocess.run(
        [sys.executable, str(SCRIPT_PATH), *args],
        cwd=repo,
        capture_output=True,
        text=True,
    )


class GitIntegrationTests(unittest.TestCase):
    def test_clean_tree_passes(self):
        with tempfile.TemporaryDirectory() as tmp:
            repo = Path(tmp)
            _init_repo(repo)
            (repo / "Example.java").write_text(_DOCUMENTED_BASELINE)
            _git(["add", "Example.java"], repo)
            _git(["commit", "-m", "baseline"], repo)

            result = _run_checker(repo, "--base", "HEAD")

            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(
                result.stdout.strip(), "Public Javadoc check passed: 0 violations"
            )

    def test_working_tree_undocumented_added_method_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            repo = Path(tmp)
            _init_repo(repo)
            (repo / "Example.java").write_text(_DOCUMENTED_BASELINE)
            _git(["add", "Example.java"], repo)
            _git(["commit", "-m", "baseline"], repo)

            # Undocumented method added, but not committed.
            (repo / "Example.java").write_text(_WITH_UNDOCUMENTED_METHOD)

            result = _run_checker(repo, "--base", "HEAD")

            self.assertEqual(result.returncode, 1, result.stderr)
            self.assertIn(
                "Example.java:5: public method parse is missing Javadoc",
                result.stdout,
            )
            self.assertIn(
                "Public Javadoc check failed: 1 violation(s)", result.stdout
            )

    def test_explicit_head_undocumented_added_method_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            repo = Path(tmp)
            _init_repo(repo)
            (repo / "Example.java").write_text(_DOCUMENTED_BASELINE)
            _git(["add", "Example.java"], repo)
            _git(["commit", "-m", "baseline"], repo)
            base_sha = _git(["rev-parse", "HEAD"], repo).strip()

            (repo / "Example.java").write_text(_WITH_UNDOCUMENTED_METHOD)
            _git(["add", "Example.java"], repo)
            _git(["commit", "-m", "add parse"], repo)

            result = _run_checker(repo, "--base", base_sha, "--head", "HEAD")

            self.assertEqual(result.returncode, 1, result.stderr)
            self.assertIn(
                "Example.java:5: public method parse is missing Javadoc",
                result.stdout,
            )
            self.assertIn(
                "Public Javadoc check failed: 1 violation(s)", result.stdout
            )

    def test_path_with_spaces_is_reported_correctly(self):
        with tempfile.TemporaryDirectory() as tmp:
            repo = Path(tmp)
            _init_repo(repo)
            sub = repo / "My Module"
            sub.mkdir()
            java_file = sub / "My Class.java"
            java_file.write_text(
                "/**\n"
                " * Example type.\n"
                " */\n"
                "public class MyClass {\n"
                "}\n"
            )
            _git(["add", "My Module/My Class.java"], repo)
            _git(["commit", "-m", "baseline"], repo)

            java_file.write_text(
                "/**\n"
                " * Example type.\n"
                " */\n"
                "public class MyClass {\n"
                "    public String parse(String value) {\n"
                "        return value;\n"
                "    }\n"
                "}\n"
            )

            result = _run_checker(repo, "--base", "HEAD")

            self.assertEqual(result.returncode, 1, result.stderr)
            self.assertIn(
                "My Module/My Class.java:5: public method parse is missing Javadoc",
                result.stdout,
            )

    def test_deleted_file_does_not_crash_or_report(self):
        with tempfile.TemporaryDirectory() as tmp:
            repo = Path(tmp)
            _init_repo(repo)
            java_file = repo / "Example.java"
            java_file.write_text(_DOCUMENTED_BASELINE)
            _git(["add", "Example.java"], repo)
            _git(["commit", "-m", "baseline"], repo)

            java_file.unlink()

            result = _run_checker(repo, "--base", "HEAD")

            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(
                result.stdout.strip(), "Public Javadoc check passed: 0 violations"
            )

    def test_non_ascii_filename_is_reported_correctly(self):
        # With core.quotePath=true (Git's default), a path containing
        # non-ASCII characters is emitted C-style-quoted in diff headers
        # (e.g. `"b/\303\234n..."`), not as a bare `b/...` path. Regression
        # test for a bug where the `b/` prefix was stripped before
        # unquoting, so the strip was skipped (the raw text starts with a
        # quote, not `b/`) and a bogus literal `b/` ended up baked into the
        # unquoted path, which then failed to resolve to the real file.
        with tempfile.TemporaryDirectory() as tmp:
            repo = Path(tmp)
            _init_repo(repo)
            java_file = repo / "Ünïcödé Cläss.java"
            java_file.write_text(
                "/**\n"
                " * Example type.\n"
                " */\n"
                "public class UnicodeClass {\n"
                "}\n"
            )
            _git(["add", "Ünïcödé Cläss.java"], repo)
            _git(["commit", "-m", "baseline"], repo)

            java_file.write_text(
                "/**\n"
                " * Example type.\n"
                " */\n"
                "public class UnicodeClass {\n"
                "    public String parse(String value) {\n"
                "        return value;\n"
                "    }\n"
                "}\n"
            )

            result = _run_checker(repo, "--base", "HEAD")

            self.assertEqual(result.returncode, 1, result.stderr)
            self.assertIn(
                "Ünïcödé Cläss.java:5: public method parse is missing Javadoc",
                result.stdout,
            )

    def test_untracked_new_java_file_is_checked_in_working_tree_mode(self):
        with tempfile.TemporaryDirectory() as tmp:
            repo = Path(tmp)
            _init_repo(repo)
            (repo / "Example.java").write_text(_DOCUMENTED_BASELINE)
            _git(["add", "Example.java"], repo)
            _git(["commit", "-m", "baseline"], repo)

            # Brand-new file, never `git add`-ed: invisible to `git diff`.
            (repo / "NewThing.java").write_text(
                _WITH_UNDOCUMENTED_METHOD.replace("Example", "NewThing")
            )

            result = _run_checker(repo, "--base", "HEAD")

            self.assertEqual(result.returncode, 1, result.stderr)
            self.assertIn(
                "NewThing.java:5: public method parse is missing Javadoc",
                result.stdout,
            )

    def test_untracked_new_java_file_is_ignored_in_explicit_head_mode(self):
        # Untracked working-tree files aren't part of a base...head
        # comparison, so --head mode must not pick them up.
        with tempfile.TemporaryDirectory() as tmp:
            repo = Path(tmp)
            _init_repo(repo)
            (repo / "Example.java").write_text(_DOCUMENTED_BASELINE)
            _git(["add", "Example.java"], repo)
            _git(["commit", "-m", "baseline"], repo)
            base_sha = _git(["rev-parse", "HEAD"], repo).strip()

            (repo / "NewThing.java").write_text(
                _WITH_UNDOCUMENTED_METHOD.replace("Example", "NewThing")
            )

            result = _run_checker(repo, "--base", base_sha, "--head", "HEAD")

            self.assertEqual(result.returncode, 0, result.stderr)

    def test_deleted_file_produces_no_unreadable_warning(self):
        with tempfile.TemporaryDirectory() as tmp:
            repo = Path(tmp)
            _init_repo(repo)
            java_file = repo / "Example.java"
            java_file.write_text(_DOCUMENTED_BASELINE)
            _git(["add", "Example.java"], repo)
            _git(["commit", "-m", "baseline"], repo)

            java_file.unlink()

            result = _run_checker(repo, "--base", "HEAD")

            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertNotIn("warning", result.stderr)

    def test_diffed_unreadable_non_deleted_file_warns_and_exits_2(self):
        # A path that came out of the diff (not a `/dev/null` deletion) but
        # that fails to read must not be silently treated as if it were
        # deleted: it should produce a stderr warning and a non-zero,
        # Git-failure exit code, distinguishing "couldn't read this" from
        # "this genuinely doesn't exist any more". A real, portable
        # repro of an unreadable-but-present file (e.g. permission denied)
        # is unreliable across environments (may run as root), so this
        # drives the actual read-dispatch code directly instead.
        with tempfile.TemporaryDirectory() as tmp:
            repo = Path(tmp)
            _init_repo(repo)
            (repo / "Example.java").write_text(_DOCUMENTED_BASELINE)
            _git(["add", "Example.java"], repo)
            _git(["commit", "-m", "baseline"], repo)

            (repo / "Example.java").write_text(_WITH_UNDOCUMENTED_METHOD)

            real_read_source = check_public_javadoc._read_source

            def flaky_read_source(path, head, repo_arg):
                if path == Path("Example.java"):
                    return None
                return real_read_source(path, head, repo_arg)

            stderr_capture = io.StringIO()
            with contextlib.redirect_stderr(stderr_capture):
                with mock.patch.object(
                    check_public_javadoc,
                    "_read_source",
                    side_effect=flaky_read_source,
                ):
                    with self.assertRaises(check_public_javadoc.GitError) as ctx:
                        check_public_javadoc.check_changed_javadoc(
                            "HEAD", None, repo
                        )

            self.assertIn("Example.java", str(ctx.exception))
            self.assertIn(
                "warning: cannot read Example.java", stderr_capture.getvalue()
            )

    def test_invalid_base_ref_exits_with_code_2(self):
        with tempfile.TemporaryDirectory() as tmp:
            repo = Path(tmp)
            _init_repo(repo)
            (repo / "Example.java").write_text(_DOCUMENTED_BASELINE)
            _git(["add", "Example.java"], repo)
            _git(["commit", "-m", "baseline"], repo)

            result = _run_checker(repo, "--base", "not-a-real-ref")

            self.assertEqual(result.returncode, 2, result.stdout)

    def test_missing_base_argument_exits_with_code_2(self):
        with tempfile.TemporaryDirectory() as tmp:
            repo = Path(tmp)
            _init_repo(repo)

            result = _run_checker(repo)

            self.assertEqual(result.returncode, 2, result.stdout)


class ValidationTests(unittest.TestCase):
    def test_changed_public_method_requires_all_tags(self):
        source = """
public class Example {
    /** Parses a value. @param value input */
    public String parse(String value) { return value; }
}
"""
        self.assertEqual(
            validate_source("Example.java", source, {4}),
            ["Example.java:4: public method parse is missing @return"],
        )

    def test_unchanged_declaration_is_out_of_scope(self):
        source = "public class Example {}\n"
        self.assertEqual(validate_source("Example.java", source, set()), [])

    def test_documented_public_class_has_no_violations(self):
        source = (
            "/**\n"
            " * Example type.\n"
            " */\n"
            "public class Example {\n"
            "}\n"
        )
        self.assertEqual(validate_source("Example.java", source, {4}), [])

    def test_undocumented_public_interface_reports_missing_javadoc(self):
        source = "public interface Thing {\n}\n"
        self.assertEqual(
            validate_source("Thing.java", source, {1}),
            ["Thing.java:1: public interface Thing is missing Javadoc"],
        )

    def test_multiline_annotation_does_not_hide_javadoc(self):
        source = (
            "public class Example {\n"
            "    /**\n"
            "     * Computes something.\n"
            "     * @param value input\n"
            "     * @return computed\n"
            "     */\n"
            "    @Deprecated\n"
            "    @SuppressWarnings({\n"
            '        "unchecked"\n'
            "    })\n"
            "    public String compute(String value) {\n"
            "        return value;\n"
            "    }\n"
            "}\n"
        )
        self.assertEqual(validate_source("Example.java", source, {11}), [])

    def test_multiline_annotation_without_javadoc_still_reports_missing_javadoc(self):
        source = (
            "public class Example {\n"
            "    @Deprecated\n"
            "    @SuppressWarnings({\n"
            '        "unchecked"\n'
            "    })\n"
            "    public String compute(String value) {\n"
            "        return value;\n"
            "    }\n"
            "}\n"
        )
        self.assertEqual(
            validate_source("Example.java", source, {6}),
            ["Example.java:6: public method compute is missing Javadoc"],
        )

    def test_constructor_requires_param_tags(self):
        source = (
            "public class Widget {\n"
            "    /**\n"
            "     * Creates a widget.\n"
            "     * @param name the name\n"
            "     */\n"
            "    public Widget(String name, int size) {\n"
            "    }\n"
            "}\n"
        )
        self.assertEqual(
            validate_source("Widget.java", source, {6}),
            ["Widget.java:6: public constructor Widget is missing @param size"],
        )

    def test_void_method_does_not_require_return_tag(self):
        source = (
            "public class Example {\n"
            "    /**\n"
            "     * Logs a message.\n"
            "     * @param message the message\n"
            "     */\n"
            "    public void log(String message) {\n"
            "    }\n"
            "}\n"
        )
        self.assertEqual(validate_source("Example.java", source, {6}), [])

    def test_non_void_method_requires_return_tag(self):
        source = (
            "public class Example {\n"
            "    /**\n"
            "     * Builds a value.\n"
            "     * @param value input\n"
            "     */\n"
            "    public String build(String value) {\n"
            "        return value;\n"
            "    }\n"
            "}\n"
        )
        self.assertEqual(
            validate_source("Example.java", source, {6}),
            ["Example.java:6: public method build is missing @return"],
        )

    def test_parameter_names_handle_generics_arrays_and_varargs(self):
        source = (
            "public class Example {\n"
            "    /**\n"
            "     * Converts values.\n"
            "     * @param input the input map\n"
            "     * @param names the names\n"
            "     * @param labels the labels\n"
            "     * @return converted map\n"
            "     */\n"
            "    public <T> Map<String, List<T>> convert(\n"
            "            Map<String, List<Integer>> input, String[] names, String... labels) {\n"
            "        return null;\n"
            "    }\n"
            "}\n"
        )
        declarations = find_public_declarations(source)
        methods = [d for d in declarations if d.kind == "method"]
        self.assertEqual(len(methods), 1)
        self.assertEqual(methods[0].parameters, ("input", "names", "labels"))
        self.assertTrue(methods[0].returns_value)
        self.assertEqual(methods[0].line, 9)
        self.assertEqual(methods[0].end_line, 10)
        self.assertEqual(validate_source("Example.java", source, {9}), [])

    def test_record_requires_component_param_tags(self):
        source = (
            "/**\n"
            " * A point.\n"
            " * @param x the x coordinate\n"
            " */\n"
            "public record Point(int x, int y) {\n"
            "}\n"
        )
        self.assertEqual(
            validate_source("Point.java", source, {5}),
            ["Point.java:5: public record Point is missing @param y"],
        )

    def test_documented_record_has_no_violations(self):
        source = (
            "/**\n"
            " * A point.\n"
            " * @param x the x coordinate\n"
            " * @param y the y coordinate\n"
            " */\n"
            "public record Point(int x, int y) {\n"
            "}\n"
        )
        self.assertEqual(validate_source("Point.java", source, {6}), [])

    def test_private_declarations_are_ignored(self):
        source = (
            "public class Example {\n"
            "    private String helper(String value) {\n"
            "        return value;\n"
            "    }\n"
            "}\n"
        )
        self.assertEqual(validate_source("Example.java", source, {2}), [])
        self.assertEqual([d.kind for d in find_public_declarations(source)], ["class"])

    def test_implicit_public_interface_method_without_modifier_reports_missing_javadoc(self):
        source = (
            "public interface Detector {\n"
            "    Optional<Thing> detect(File root);\n"
            "}\n"
        )
        declarations = find_public_declarations(source)
        methods = [d for d in declarations if d.kind == "method"]
        self.assertEqual(len(methods), 1)
        self.assertEqual(methods[0].name, "detect")
        self.assertEqual(methods[0].parameters, ("root",))
        self.assertTrue(methods[0].returns_value)
        self.assertEqual(
            validate_source("Detector.java", source, {2}),
            ["Detector.java:2: public method detect is missing Javadoc"],
        )

    def test_implicit_public_interface_method_with_javadoc_has_no_violations(self):
        source = (
            "public interface Detector {\n"
            "    /**\n"
            "     * Detects a thing.\n"
            "     * @param root the root\n"
            "     * @return the thing, if found\n"
            "     */\n"
            "    Optional<Thing> detect(File root);\n"
            "}\n"
        )
        self.assertEqual(validate_source("Detector.java", source, {7}), [])

    def test_implicit_public_interface_default_method_without_public_is_checked(self):
        source = (
            "public interface Detector {\n"
            "    default void run() {\n"
            "    }\n"
            "}\n"
        )
        self.assertEqual(
            validate_source("Detector.java", source, {2}),
            ["Detector.java:2: public method run is missing Javadoc"],
        )

    def test_private_interface_method_remains_ignored(self):
        source = (
            "public interface Detector {\n"
            "    private void helper() {\n"
            "    }\n"
            "}\n"
        )
        self.assertEqual(validate_source("Detector.java", source, {2}), [])
        self.assertEqual([d.kind for d in find_public_declarations(source)], ["interface"])

    def test_package_private_class_method_remains_ignored(self):
        # Regression guard: implicit-public detection is scoped to
        # interface bodies only. A no-modifier method on a *class* is
        # genuinely package-private and must stay unchecked.
        source = (
            "public class Example {\n"
            "    String helper(String value) {\n"
            "        return value;\n"
            "    }\n"
            "}\n"
        )
        self.assertEqual(validate_source("Example.java", source, {2}), [])
        self.assertEqual([d.kind for d in find_public_declarations(source)], ["class"])

    def test_documented_annotation_type_has_no_violations(self):
        source = (
            "/**\n"
            " * Marks a thing.\n"
            " */\n"
            "public @interface Marker {\n"
            "}\n"
        )
        self.assertEqual(validate_source("Marker.java", source, {4}), [])

    def test_undocumented_annotation_type_reports_missing_javadoc(self):
        source = "public @interface Marker {\n}\n"
        self.assertEqual(
            validate_source("Marker.java", source, {1}),
            ["Marker.java:1: public @interface Marker is missing Javadoc"],
        )

    def test_unchanged_public_method_is_ignored_even_when_undocumented(self):
        source = (
            "/**\n"
            " * Example type.\n"
            " */\n"
            "public class Example {\n"
            "    public String parse(String value) {\n"
            "        return value;\n"
            "    }\n"
            "}\n"
        )
        # Only line 4 (the documented class) changed; the undocumented
        # method on line 5 is untouched and must not be reported.
        self.assertEqual(validate_source("Example.java", source, {4}), [])


class DeclarationImmutabilityTests(unittest.TestCase):
    def test_declaration_fields_cannot_be_reassigned(self):
        source = "public class Example {}\n"
        decl = find_public_declarations(source)[0]
        with self.assertRaises(FrozenInstanceError):
            decl.name = "Other"


if __name__ == "__main__":
    unittest.main()
