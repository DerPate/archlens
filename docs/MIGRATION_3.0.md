# Migrating To 3.0

3.0 has three incompatible changes. Most users only need step 1.

## 1. The cache moved

`.archlens-cache/` in the working directory is no longer read or written. The cache now lives in
your user cache directory, separately for each working directory (see
[INSTALL.md](INSTALL.md) for the lookup order).

- Run `index_workspace` once after upgrading.
- Delete old `.archlens-cache/` directories.
- If your MCP client starts the server without write access to the home directory, set
  `ARCHLENS_CACHE_DIR` to an absolute path.

## 2. OKF concepts use `x-archlens-*` keys

Generated concepts follow OKF v0.3.

| 2.x | 3.0 |
| --- | --- |
| `archlens_family` | `x-archlens-family` |
| `archlens_status` | `x-archlens-status` |
| `archlens_semantic_key` | `x-archlens-semantic-key` |
| `archlens_project_path` | `x-archlens-project-path` |
| `archlens_generated` | `x-archlens-generated` |

- Nothing to do for existing bundles: old keys are read, and renamed when a concept is refreshed.
- Update anything else that reads the old keys.
- This is one-way. 2.x refuses to refresh a concept written by 3.0, so upgrade everyone who writes
  to a shared bundle together.

## 3. `ARCHLENS_MCP_EXPERIMENTAL_DRAFT` is gone

The variable is ignored and can be removed from client configuration. Collection results always
have an object root:

| Tool result | With the flag in 2.x | 3.0 |
| --- | --- | --- |
| `find_entrypoints` | `[...]` | `{"entrypoints": [...]}` |
| `find_components` | `[...]` | `{"components": [...]}` |
| `query_architecture_graph`, `find_nodes` | `[...]` | `{"action": "find_nodes", "nodes": [...]}` |

Other collection tools follow the same pattern, as declared in `tools/list`. Clients that did not
set the flag see no change.

## Changed behavior that needs no action

- Tool calls run one at a time, in arrival order.
- Call graphs can contain more edges and data-flow steps, because calls next to unresolved library
  types are now bound. Expect differences when comparing exports with 2.x.
- Export tools and the OKF writer refuse output paths where a symbolic link leads out of the
  project or bundle. If you link `docs/` elsewhere on purpose, pass an `outputPath` outside the
  project.
- Refreshing an OKF concept keeps human review and foreign keys.
- `index_workspace` returns a `workspaceId` that query tools accept optionally.
