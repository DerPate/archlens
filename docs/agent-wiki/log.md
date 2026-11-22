# Update Log

## 2026-11-22
- **Verification**: `human:dominik` approved the corrected
  [export_graph_viewer](tools/export-graph-viewer.md), which is `stable` again.
- **Update**: `human:dominik` approved the `maxDepth` wording of
  [render_pipeline](tools/render-pipeline.md). Two other claims there were then found wrong and
  corrected (messaging shape, diagnostics when filters match nothing), so it stays `draft` until
  those are reviewed.
- **Update**: Corrected [export_graph_viewer](tools/export-graph-viewer.md) (its libraries load from
  CDNs) and [render_pipeline](tools/render-pipeline.md) (`maxDepth` default). Both are `status: draft`
  and their 2026-09-17 verification is history until reviewed again.
- **Verification**: `human:dominik` approved the corrected 3.0 wording of
  [MCP Server](modules/mcp-server.md): a wrong cross-reference is removed and `workspaceId` covers
  every tool except `index_workspace`. The concept is `stable` again.
- **Migration**: Moved the bundle to OKF v0.3. Path fields are now repository URLs, and three
  descriptions that were invalid YAML are quoted. No concept body changed, so `generated` and
  `verified` are unchanged.
- **Update**: Rewrote [MCP Server](modules/mcp-server.md) for 3.0. Its content changed, so it is
  back to `status: draft` and its 2026-09-17 verification is history until reviewed again.

## 2026-09-17
- **Verification**: `human:dominik` reviewed the bundle, caught that Main#main's
  Dashboard branch was undocumented, and confirmed the fix. All concepts marked
  `verified` and promoted to `status: stable`.
- **Creation**: Initial bundle generated from `index_workspace` (232 components, 1
  entrypoint) plus `docs/TOOLS.md` and `docs/GENERATED_ARCHITECTURE.md`. Added
  Application, Entrypoint, 16 Module concepts, 24 MCP Tool concepts, and the
  Workflow Prompt catalog.
