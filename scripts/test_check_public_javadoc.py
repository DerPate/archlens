"""Unit tests for the public-Javadoc declaration parser and validator.

These tests exercise only the pure-parsing core (Declaration,
find_public_declarations, validate_source). Git/CLI integration is added in
a later task and is intentionally not exercised here.
"""

import importlib.util
import unittest
from dataclasses import FrozenInstanceError
from pathlib import Path

spec = importlib.util.spec_from_file_location(
    "check_public_javadoc", Path(__file__).with_name("check-public-javadoc.py")
)
check_public_javadoc = importlib.util.module_from_spec(spec)
spec.loader.exec_module(check_public_javadoc)

Declaration = check_public_javadoc.Declaration
find_public_declarations = check_public_javadoc.find_public_declarations
validate_source = check_public_javadoc.validate_source


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
