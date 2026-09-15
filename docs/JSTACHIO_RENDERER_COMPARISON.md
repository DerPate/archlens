# Pre-JStachio renderer comparison

Verified on 2026-09-15 against pre-migration commit `ca5e37f`, the parent of
JStachio migration commit `a19a8a3`. The baseline was built from a separate git
worktree using `mvn -q clean package` (851 tests passed). The current jar was
clean-built from this branch (858 tests passed), after the Mermaid C4-keyword
escaping was narrowed to fix an id-collision bug found in review (it now
splits only the five literal Mermaid C4 keywords instead of every
digit-then-capital boundary). Re-running the comparison after that change
reproduced the identical 81/33/3 split recorded below, confirming the
narrower escaping still fully addresses the case it was written for.

Both jars indexed **identical current source paths**, rather than comparing
different historical versions of the projects. Inputs were the Quarkus, Java EE,
Spring pipeline, event-bus, store-handoff and WAR/module fixtures, plus ArchLens
itself. This isolates renderer changes from changes in the indexed source.

## Results

| Comparison | Cases |
| --- | ---: |
| Byte-for-byte identical output | 81 |
| Only the existing subgraph spacing fix differs | 33 |
| Subgraph spacing plus the new C4-name escaping differs | 3 |
| Unexplained differences | 0 |
| Total | 117 |

Coverage includes universal system/container/component/module flowcharts,
component architecture views, source overviews, dependency maps and slices,
use-case timelines, pipelines, call-flow sequences, Mermaid C4 system/container
diagrams, and LikeC4 workspace/component models. One of the identical cases is
the event-bus fixture's pre-existing “No call-graph data available” pipeline
error; it is not counted as a successfully rendered pipeline.

The three C4-escaping differences occur in ArchLens's component flowchart,
source overview and component architecture view. They preserve visible names
while preventing `LikeC4DynamicStep` and `LikeC4DynamicView` from selecting
Mermaid's C4 parser. **The pre-JStachio renderer also emits these problematic
names on the same current source tree.** The evidence does not identify the
template migration as the cause of that failure.

Five representative before/after pairs were rendered with the same local
Mermaid CLI and Chromium: Quarkus component flowchart, component architecture
view, source overview, call-flow sequence, and C4 container diagram. All ten
SVGs rendered successfully and matched in dimensions, element positions and
labels. SVG path bytes are not a stable equality check because the renderer
generates varying curve control points.

This comparison checks raw renderer output. The documentation generator's new
identifier compaction and diagram partitioning are intentionally outside raw
output parity; their dependency preservation has separate Python tests.

## Repeat the comparison

Build `ca5e37f` in a separate checkout/archive and clean-build the current jar,
then run:

```sh
python3 scripts/compare-diagram-renderers.py /path/to/baseline/target/archlens.jar target/archlens.jar
```

The script defaults to the seven workspaces above. Additional positional paths
replace those defaults. It writes raw before/after outputs, a summary, and unified
diffs under `target/renderer-comparison/`. Any raw difference produces a nonzero
exit status, including the intentional differences documented here; none are
silently normalized away. Generated artifacts are not committed.
