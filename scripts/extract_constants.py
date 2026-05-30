#!/usr/bin/env python3
"""Extract duplicated string literals into private static final constants (Sonar S1192).

Usage: extract_constants.py <file> lit1=NAME1 lit2=NAME2 ...
Inserts the declarations after the class opening brace and replaces every exact-token
occurrence of the literal except on the declaration lines themselves.
"""
import re
import sys

path = sys.argv[1]
pairs = [a.split("=", 1) for a in sys.argv[2:]]

with open(path) as f:
    lines = f.readlines()

# find the class/enum/record declaration line ending in '{'
decl_idx = None
for i, ln in enumerate(lines):
    if re.search(r"\b(class|enum|record|interface)\b", ln) and ln.rstrip().endswith("{"):
        decl_idx = i
        break
if decl_idx is None:
    sys.exit(f"no class declaration found in {path}")

block = ["\n"]
for lit, name in pairs:
    block.append(f'    private static final String {name} = "{lit}";\n')

out = lines[: decl_idx + 1] + block + lines[decl_idx + 1 :]

# replace occurrences (skip declaration lines we just inserted)
decl_names = {name for _, name in pairs}
for i, ln in enumerate(out):
    stripped = ln.strip()
    if stripped.startswith("private static final String ") and any(
        stripped.startswith(f"private static final String {n} =") for n in decl_names
    ):
        continue
    for lit, name in pairs:
        out[i] = out[i].replace(f'"{lit}"', name)

with open(path, "w") as f:
    f.writelines(out)
print(f"updated {path}: {len(pairs)} constants")
