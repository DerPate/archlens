"""Validate that changed public Java declarations carry proper Javadoc.

This module implements the pure-parsing core only: it finds public
declarations (types, records, constructors, methods) in Java source text and
checks them against ArchLens' Javadoc convention:

- A public type requires a Javadoc block.
- A public record additionally requires one ``@param`` tag per component.
- A public method or constructor requires a Javadoc block, one ``@param``
  tag per named parameter, and ``@return`` for non-void methods.
- Annotations (and other comments) between a Javadoc block and the
  declaration it documents must not hide that Javadoc.

The checker only inspects declarations that intersect a diff's added lines
(computed via ``git diff --unified=0``, shelled out to the ``git`` CLI);
untouched public backlog remains allowed. Run as a script:

    python3 scripts/check-public-javadoc.py --base REF [--head REF]

Without ``--head``, the diff is taken against the working tree (so it picks
up committed history since ``--base`` plus any uncommitted changes); with
``--head``, the diff is taken over ``BASE...HEAD``.
"""

import argparse
import re
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class Declaration:
    """An immutable record of one public Java declaration found in source.

    ``line``/``end_line`` bound the declaration's *header* (from its first
    modifier through the character that opens its body or terminates its
    signature), used to test whether the declaration intersects a set of
    changed line numbers.
    """

    kind: str
    name: str
    line: int
    end_line: int
    parameters: tuple[str, ...]
    returns_value: bool
    record_components: tuple[str, ...]


_MODIFIERS = (
    "static",
    "final",
    "abstract",
    "default",
    "synchronized",
    "native",
    "strictfp",
    "sealed",
)

_TYPE_KEYWORDS = ("class", "interface", "enum", "record")

_STOP_CHARS = set("(=;,{")


def _is_ident_char(ch: str | None) -> bool:
    return ch is not None and (ch.isalnum() or ch in "_$")


def _match_keyword(s: str, pos: int, word: str) -> bool:
    end = pos + len(word)
    if s[pos:end] != word:
        return False
    before_ok = pos == 0 or not _is_ident_char(s[pos - 1])
    after_ok = end >= len(s) or not _is_ident_char(s[end])
    return before_ok and after_ok


def _skip_ws(s: str, i: int) -> int:
    n = len(s)
    while i < n and s[i].isspace():
        i += 1
    return i


def _skip_balanced(s: str, i: int, open_ch: str, close_ch: str) -> int:
    """Return the index just past the char matching s[i] == open_ch."""
    depth = 0
    n = len(s)
    j = i
    while j < n:
        if s[j] == open_ch:
            depth += 1
        elif s[j] == close_ch:
            depth -= 1
            if depth == 0:
                return j + 1
        j += 1
    return j


def _find_matching_paren(s: str, open_pos: int) -> int:
    """Return the index of the ')' matching the '(' at open_pos."""
    depth = 0
    n = len(s)
    j = open_pos
    while j < n:
        if s[j] == "(":
            depth += 1
        elif s[j] == ")":
            depth -= 1
            if depth == 0:
                return j
        j += 1
    return n - 1


def _skip_modifiers(s: str, j: int) -> int:
    while True:
        j = _skip_ws(s, j)
        matched = None
        for kw in _MODIFIERS:
            if _match_keyword(s, j, kw):
                matched = kw
                break
        if matched is None:
            return j
        j += len(matched)


def _consume_identifier(s: str, pos: int) -> tuple[str, int]:
    n = len(s)
    start = pos
    while pos < n and (s[pos].isalnum() or s[pos] in "_$"):
        pos += 1
    return s[start:pos], pos


def _parse_name_before_parens(s: str, pos: int) -> tuple[list[str], int]:
    """Tokenize whitespace-separated words up to '(' / '=' / ';' / ',' / '{'.

    Balanced '<...>' (generics) and '[...]' (arrays) are consumed as part of
    a single token so that e.g. ``Map<String, List<T>>`` or ``String[]``
    never get split on their internal characters.
    """
    tokens: list[str] = []
    n = len(s)
    i = pos
    while True:
        i = _skip_ws(s, i)
        if i >= n or s[i] in _STOP_CHARS:
            break
        start = i
        while i < n and s[i] not in _STOP_CHARS and not s[i].isspace():
            if s[i] == "<":
                i = _skip_balanced(s, i, "<", ">")
            elif s[i] == "[":
                i = _skip_balanced(s, i, "[", "]")
            else:
                i += 1
        tokens.append(s[start:i])
    return tokens, i


def _split_top_level(text: str, sep: str) -> list[str]:
    """Split text on sep, ignoring seps nested inside <>, [] or ()."""
    parts: list[str] = []
    depth_angle = depth_square = depth_paren = 0
    start = 0
    for i, c in enumerate(text):
        if c == "<":
            depth_angle += 1
        elif c == ">":
            depth_angle = max(0, depth_angle - 1)
        elif c == "[":
            depth_square += 1
        elif c == "]":
            depth_square = max(0, depth_square - 1)
        elif c == "(":
            depth_paren += 1
        elif c == ")":
            depth_paren = max(0, depth_paren - 1)
        elif c == sep and depth_angle == 0 and depth_square == 0 and depth_paren == 0:
            parts.append(text[start:i])
            start = i + 1
    parts.append(text[start:])
    return parts


def _extract_param_names(text: str) -> tuple[str, ...]:
    text = text.strip()
    if not text:
        return ()
    names = []
    for chunk in _split_top_level(text, ","):
        chunk = chunk.strip()
        if not chunk:
            continue
        # Append a sentinel '(' so the tokenizer has a stop char to end on;
        # parameter text itself never legally contains one of _STOP_CHARS.
        tokens, _ = _parse_name_before_parens(chunk + "(", 0)
        if tokens:
            names.append(tokens[-1])
    return tuple(names)


def _find_comments_and_strings(source: str) -> list[tuple[int, int, str]]:
    """Find (start, end, kind) spans for comments, strings, and char literals.

    kind is one of "line", "block", "javadoc", "string". Text-block strings
    (\"\"\"...\"\"\") are reported as kind "string" too.
    """
    spans: list[tuple[int, int, str]] = []
    n = len(source)
    i = 0
    while i < n:
        c = source[i]
        if c == "/" and source.startswith("//", i):
            start = i
            i += 2
            while i < n and source[i] != "\n":
                i += 1
            spans.append((start, i, "line"))
        elif source.startswith("/*", i):
            start = i
            is_doc = source.startswith("/**", i) and not source.startswith("/**/", i)
            i += 2
            end_idx = source.find("*/", i)
            if end_idx == -1:
                i = n
            else:
                i = end_idx + 2
            spans.append((start, i, "javadoc" if is_doc else "block"))
        elif source.startswith('"""', i):
            start = i
            i += 3
            end_idx = source.find('"""', i)
            if end_idx == -1:
                i = n
            else:
                i = end_idx + 3
            spans.append((start, i, "string"))
        elif c == '"':
            start = i
            i += 1
            while i < n and source[i] != '"' and source[i] != "\n":
                if source[i] == "\\" and i + 1 < n:
                    i += 2
                else:
                    i += 1
            if i < n and source[i] == '"':
                i += 1
            spans.append((start, i, "string"))
        elif c == "'":
            start = i
            i += 1
            while i < n and source[i] != "'" and source[i] != "\n":
                if source[i] == "\\" and i + 1 < n:
                    i += 2
                else:
                    i += 1
            if i < n and source[i] == "'":
                i += 1
            spans.append((start, i, "string"))
        else:
            i += 1
    return spans


def _blank_spans(text: str, spans: list[tuple[int, int, str]]) -> str:
    """Replace every character in each span with a space, keeping newlines."""
    chars = list(text)
    for start, end, *_rest in spans:
        for idx in range(start, end):
            if chars[idx] != "\n":
                chars[idx] = " "
    return "".join(chars)


_ANNOTATION_RE = re.compile(r"@[A-Za-z_][\w]*(?:\.[A-Za-z_][\w]*)*")


def _find_annotation_spans(masked: str) -> list[tuple[int, int, str]]:
    """Find (start, end, "annotation") spans on a comment/string-blanked source."""
    spans: list[tuple[int, int, str]] = []
    n = len(masked)
    for m in _ANNOTATION_RE.finditer(masked):
        start = m.start()
        j = m.end()
        j = _skip_ws(masked, j)
        if j < n and masked[j] == "(":
            end = _find_matching_paren(masked, j) + 1
        else:
            end = m.end()
        spans.append((start, end, "annotation"))
    return spans


def _sync_region_idx(regions: list[tuple[int, int, str]], region_idx: int, upto: int) -> int:
    """Advance past any regions fully behind `upto` (skipped over by a jump)."""
    while region_idx < len(regions) and regions[region_idx][0] < upto:
        region_idx += 1
    return region_idx


def _find_declarations_with_javadoc(source: str) -> list[tuple[Declaration, str | None]]:
    comment_spans = _find_comments_and_strings(source)
    masked1 = _blank_spans(source, comment_spans)
    annotation_spans = _find_annotation_spans(masked1)
    masked2 = _blank_spans(masked1, annotation_spans)

    doc_kind_spans = [s for s in comment_spans if s[2] in ("line", "block", "javadoc")]
    regions = sorted(doc_kind_spans + annotation_spans, key=lambda r: r[0])

    results: list[tuple[Declaration, str | None]] = []
    type_stack: list[tuple[int, str]] = []  # (brace depth at which type opened, name)
    depth = 0
    pending_javadoc: str | None = None
    region_idx = 0
    i = 0
    n = len(masked2)
    line = 1

    while i < n:
        if region_idx < len(regions) and regions[region_idx][0] == i:
            rstart, rend, rkind = regions[region_idx]
            if rkind == "javadoc":
                pending_javadoc = source[rstart:rend]
            region_idx += 1
            line += masked2.count("\n", i, rend)
            i = rend
            continue

        c = masked2[i]
        if c == "\n":
            line += 1
            i += 1
            continue
        if c.isspace():
            i += 1
            continue

        current_pending = pending_javadoc
        pending_javadoc = None

        if c == "{":
            depth += 1
            i += 1
            continue
        if c == "}":
            if type_stack and type_stack[-1][0] == depth:
                type_stack.pop()
            depth -= 1
            i += 1
            continue

        if _match_keyword(masked2, i, "public"):
            decl_start = i
            decl_line = line
            j = _skip_modifiers(masked2, i + len("public"))

            type_kw = None
            for kw in _TYPE_KEYWORDS:
                if _match_keyword(masked2, j, kw):
                    type_kw = kw
                    break

            if type_kw is not None:
                j += len(type_kw)
                j = _skip_ws(masked2, j)
                name, j = _consume_identifier(masked2, j)
                j = _skip_ws(masked2, j)
                if j < n and masked2[j] == "<":
                    j = _skip_balanced(masked2, j, "<", ">")
                    j = _skip_ws(masked2, j)
                record_components: tuple[str, ...] = ()
                if type_kw == "record" and j < n and masked2[j] == "(":
                    close = _find_matching_paren(masked2, j)
                    record_components = _extract_param_names(masked2[j + 1 : close])
                    j = close + 1
                    j = _skip_ws(masked2, j)
                brace_pos = masked2.find("{", j)
                if brace_pos == -1:
                    brace_pos = n - 1
                end_line = decl_line + masked2.count("\n", decl_start, brace_pos)
                decl = Declaration(
                    kind=type_kw,
                    name=name,
                    line=decl_line,
                    end_line=end_line,
                    parameters=(),
                    returns_value=False,
                    record_components=record_components,
                )
                results.append((decl, current_pending))
                type_stack.append((depth + 1, name))
                line += masked2.count("\n", i, brace_pos)
                region_idx = _sync_region_idx(regions, region_idx, brace_pos)
                i = brace_pos
                continue

            if j < n and masked2[j] == "<":
                j = _skip_balanced(masked2, j, "<", ">")
                j = _skip_ws(masked2, j)
            tokens, j2 = _parse_name_before_parens(masked2, j)
            if not tokens or j2 >= n or masked2[j2] != "(":
                # Not a method/constructor signature (e.g. a field) -- resume
                # ordinary scanning from wherever the token scan stopped.
                newpos = j2 if j2 > i else i + 1
                line += masked2.count("\n", i, newpos)
                region_idx = _sync_region_idx(regions, region_idx, newpos)
                i = newpos
                continue

            name = tokens[-1]
            type_tokens = tokens[:-1]
            close = _find_matching_paren(masked2, j2)
            params_text = masked2[j2 + 1 : close]
            param_names = _extract_param_names(params_text)
            k = _skip_ws(masked2, close + 1)
            if _match_keyword(masked2, k, "throws"):
                brace_idx = masked2.find("{", k)
                semi_idx = masked2.find(";", k)
                candidates = [x for x in (brace_idx, semi_idx) if x != -1]
                k = min(candidates) if candidates else n - 1
            end_offset = k
            end_line = decl_line + masked2.count("\n", decl_start, end_offset)
            is_ctor = (
                not type_tokens and bool(type_stack) and name == type_stack[-1][1]
            )
            if is_ctor:
                kind = "constructor"
                returns_value = False
            else:
                kind = "method"
                return_type_text = type_tokens[-1] if type_tokens else ""
                returns_value = return_type_text != "void"
            decl = Declaration(
                kind=kind,
                name=name,
                line=decl_line,
                end_line=end_line,
                parameters=param_names,
                returns_value=returns_value,
                record_components=(),
            )
            results.append((decl, current_pending))
            line += masked2.count("\n", i, end_offset)
            region_idx = _sync_region_idx(regions, region_idx, end_offset)
            i = end_offset
            continue

        i += 1

    return results


def find_public_declarations(source: str) -> list[Declaration]:
    """Return every public type/record/constructor/method declaration."""
    return [decl for decl, _javadoc in _find_declarations_with_javadoc(source)]


_PARAM_TAG_RE = re.compile(r"@param\s+(\S+)")
_RETURN_TAG_RE = re.compile(r"@return\b")


def _validate_declaration(path: str, decl: Declaration, javadoc: str | None) -> list[str]:
    if javadoc is None:
        return [f"{path}:{decl.line}: public {decl.kind} {decl.name} is missing Javadoc"]

    messages: list[str] = []
    tagged_params = set(_PARAM_TAG_RE.findall(javadoc))

    if decl.kind == "record":
        for component in decl.record_components:
            if component not in tagged_params:
                messages.append(
                    f"{path}:{decl.line}: public {decl.kind} {decl.name} is missing @param {component}"
                )

    if decl.kind in ("method", "constructor"):
        for param in decl.parameters:
            if param not in tagged_params:
                messages.append(
                    f"{path}:{decl.line}: public {decl.kind} {decl.name} is missing @param {param}"
                )
        if decl.kind == "method" and decl.returns_value and not _RETURN_TAG_RE.search(javadoc):
            messages.append(
                f"{path}:{decl.line}: public {decl.kind} {decl.name} is missing @return"
            )

    return messages


def validate_source(path: str, source: str, changed_lines: set[int]) -> list[str]:
    """Validate the public declarations in source that intersect changed_lines.

    Declarations whose [line, end_line] range does not intersect
    changed_lines are out of scope and never produce diagnostics, matching
    the convention that untouched public backlog need not be documented.
    """
    diagnostics: list[str] = []
    for decl, javadoc in _find_declarations_with_javadoc(source):
        decl_range = range(decl.line, decl.end_line + 1)
        if changed_lines.isdisjoint(decl_range):
            continue
        diagnostics.extend(_validate_declaration(path, decl, javadoc))
    return diagnostics


class GitError(RuntimeError):
    """Raised when a Git command invoked on behalf of this checker fails."""


def _run_git(args: list[str], repo: Path) -> str:
    """Run ``git`` with ``args`` in ``repo`` and return its stdout.

    Raises `GitError` (carrying the process's stderr) if git exits non-zero.
    """
    result = subprocess.run(
        ["git", *args],
        cwd=repo,
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        message = (result.stderr or result.stdout).strip() or f"git {' '.join(args)} failed"
        raise GitError(message)
    return result.stdout


def _unquote_git_path(raw: str) -> str:
    """Undo Git's C-style quoting of a path containing unusual characters.

    Git leaves ordinary paths (including ones with spaces) unquoted in diff
    output, only wrapping a path in double quotes -- with C-style escapes
    inside -- when it contains characters like embedded quotes or non-ASCII
    bytes (subject to ``core.quotePath``).
    """
    if len(raw) >= 2 and raw[0] == '"' and raw[-1] == '"':
        return raw[1:-1].encode("latin1").decode("unicode_escape").encode("latin1").decode(
            "utf-8", "replace"
        )
    return raw


_HUNK_HEADER_RE = re.compile(r"^@@ -\d+(?:,\d+)? \+(\d+)(?:,(\d+))? @@")


def changed_java_lines(base: str, head: str | None, repo: Path) -> dict[Path, set[int]]:
    """Return, per changed ``.java`` file, the set of added/modified line numbers.

    Without ``head``, this compares ``base`` to the working tree (so it
    picks up both committed history since ``base`` and any uncommitted
    changes). With ``head``, it compares ``base...head`` (Git's
    merge-base-relative range), i.e. what ``head`` added since it diverged
    from ``base``. Only files ending in ``.java`` are considered; a deleted
    file contributes no lines, since there is nothing left to validate.
    """
    diff_range = [base] if head is None else [f"{base}...{head}"]
    diff_output = _run_git(["diff", "--unified=0", "--no-color", *diff_range], repo)

    changed: dict[Path, set[int]] = {}
    current_path: str | None = None
    for line in diff_output.splitlines():
        if line.startswith("+++ "):
            raw = line[len("+++ ") :]
            # Git appends a single trailing tab to disambiguate paths that
            # contain a space (or other unusual character) from the rest of
            # the line; strip it before further parsing.
            if raw.endswith("\t"):
                raw = raw[:-1]
            if raw == "/dev/null":
                current_path = None
                continue
            if raw.startswith("b/"):
                raw = raw[2:]
            raw = _unquote_git_path(raw)
            current_path = raw if raw.endswith(".java") else None
            continue
        if current_path is None or not line.startswith("@@"):
            continue
        match = _HUNK_HEADER_RE.match(line)
        if not match:
            continue
        start = int(match.group(1))
        count = int(match.group(2)) if match.group(2) is not None else 1
        if count == 0:
            continue
        changed.setdefault(Path(current_path), set()).update(range(start, start + count))
    return changed


def _read_source(path: Path, head: str | None, repo: Path) -> str | None:
    """Return the text of `path` at `head` (or on disk, when head is None).

    Returns None if the file does not exist there, e.g. it was deleted.
    """
    if head is None:
        full = repo / path
        if not full.is_file():
            return None
        return full.read_text(encoding="utf-8", errors="replace")
    try:
        return _run_git(["show", f"{head}:{path.as_posix()}"], repo)
    except GitError:
        return None


def check_changed_javadoc(base: str, head: str | None, repo: Path) -> list[str]:
    """Validate Javadoc on every public declaration touched by the diff.

    Files are visited in sorted path order, so the returned diagnostics are
    deterministic regardless of the order Git reports changed files in.
    """
    changed = changed_java_lines(base, head, repo)
    diagnostics: list[str] = []
    for path in sorted(changed, key=lambda p: p.as_posix()):
        source = _read_source(path, head, repo)
        if source is None:
            continue
        diagnostics.extend(validate_source(path.as_posix(), source, changed[path]))
    return diagnostics


def _build_arg_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Check that public Java declarations touched by a diff carry Javadoc.",
    )
    parser.add_argument("--base", required=True, help="Git ref/commit to diff from.")
    parser.add_argument(
        "--head",
        default=None,
        help="Git ref/commit to diff to. Defaults to the working tree.",
    )
    return parser


def main(argv: list[str] | None = None) -> int:
    """CLI entry point: parse args, run the check, and print the summary."""
    args = _build_arg_parser().parse_args(argv)
    repo = Path.cwd()

    try:
        diagnostics = check_changed_javadoc(args.base, args.head, repo)
    except GitError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 2

    for diagnostic in diagnostics:
        print(diagnostic)
    if diagnostics:
        print(f"Public Javadoc check failed: {len(diagnostics)} violation(s)")
        return 1
    print("Public Javadoc check passed: 0 violations")
    return 0


if __name__ == "__main__":
    sys.exit(main())
