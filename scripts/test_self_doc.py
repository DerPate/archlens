"""Regression checks for Mermaid diagrams embedded by self-doc.py."""

import importlib.util
import re
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("self_doc", Path(__file__).with_name("self-doc.py"))
self_doc = importlib.util.module_from_spec(spec)
spec.loader.exec_module(self_doc)


class DiagramTests(unittest.TestCase):
    def test_large_graph_retains_all_edges_in_renderable_parts(self):
        nodes = [f"dev_example_long_package_Component{i}" for i in range(232)]
        declarations = "".join(f'    {n}["{n}"]\n' for n in nodes)
        edges = "".join(
            f"    {nodes[i % 232]} -->|dependency {i}| {nodes[(i + 1) % 232]}\n"
            for i in range(591)
        )
        source = 'flowchart TD\n    subgraph pkg ["Package"]\n' + declarations + "    end\n" + edges
        markdown = "# Architecture\n\n```mermaid\n" + source + "```\n"

        result = self_doc.renderable_markdown(markdown)

        diagrams = re.findall(r"```mermaid\n(.*?)```", result, re.S)
        self.assertGreater(len(diagrams), 1)
        for diagram in diagrams:
            self.assertLessEqual(len(diagram), 50_000)
            self.assertLessEqual(diagram.count("-->"), 500)
            self.assertIn('subgraph ', diagram)
            self.assertIn('    end\n', diagram)
        for i in range(591):
            self.assertEqual(result.count(f"|dependency {i}|"), 1)
        for node in nodes:
            self.assertIn(f'["{node}"]', result)

    def test_small_diagram_is_unchanged(self):
        markdown = '# Title\n\n```mermaid\nflowchart TD\n    a["A"]\n```\n\nNotes.\n'
        self.assertEqual(self_doc.renderable_markdown(markdown), markdown)

    def test_long_edge_labels_are_partitioned_by_text_size(self):
        label = "x" * 1_000
        edges = "".join(f"    a -->|{i} {label}| b\n" for i in range(100))
        markdown = '```mermaid\nflowchart TD\n    a["A"]\n    b["B"]\n' + edges + '```'
        result = self_doc.renderable_markdown(markdown)
        diagrams = re.findall(r"```mermaid\n(.*?)```", result, re.S)
        self.assertGreater(len(diagrams), 1)
        self.assertEqual(result.count("-->"), 100)
        for diagram in diagrams:
            self.assertLessEqual(len(diagram), 50_000)

    def test_unrenderable_node_declarations_fail_explicitly(self):
        markdown = '```mermaid\nflowchart TD\n    a["' + "x" * 50_000 + '"]\n```'
        with self.assertRaisesRegex(ValueError, "node declarations"):
            self_doc.renderable_markdown(markdown)

    def test_compaction_preserves_labels_comments_and_styles(self):
        source = ('flowchart TD\n    subgraph package_id ["package_id"]\n'
                  '    long_id["long_id & #quot;quoted#quot;"]\n    end\n'
                  '    long_id -->|"long_id"| long_id\n'
                  '    classDef service fill:#fff\n    class long_id service\n'
                  '%% long_id remains a comment\n')
        compact = self_doc.compact_identifiers(source)
        self.assertIn('["long_id & #quot;quoted#quot;"]', compact)
        self.assertIn('|"long_id"|', compact)
        self.assertIn('%% long_id remains a comment', compact)
        self.assertIn('classDef service fill:#fff', compact)
        self.assertRegex(compact, r'class n\d+ service')
        self.assertNotIn('    long_id[', compact)


if __name__ == "__main__":
    unittest.main()
