# Tamboui Dashboard Design

## Context

ArchLens starts a standalone terminal dashboard when its jar is attached to an interactive
terminal and starts the stdio MCP server when input or output is redirected. The standalone mode
already shares MCP tool specifications with the server and provides a command-oriented REPL. This
design preserves that product boundary and supersedes only the terminal implementation described
in `2026-06-21-standalone-dashboard-repl-design.md`.

The current dashboard draws a two-column screen into a `StringBuilder`, pads and truncates lines by
Java string length, clears the terminal, and delegates command input to JLine. That implementation
cannot wrap or scroll long results, does not account for terminal cell width, and embeds layout and
clipping policy in backend code.

## Decision

Replace the handwritten dashboard renderer and JLine-driven screen loop with a focused TamboUI
application using TamboUI's Panama backend. Preserve the existing command language and application
logic. This is intentionally a small, command-first interface rather than a graphical tool browser
or parameter-form system.

TamboUI is an experimental, snapshot-only dependency. That trade-off is acceptable because the
standalone dashboard is a niche interface and the integration will be isolated behind one view
boundary. The implementation must not use TamboUI's JLine 3 backend: ArchLens currently depends on
JLine 4.4.3, and mixing the two major lines would create avoidable dependency and runtime risk.

## Dependency Strategy

- Add the Sonatype Central snapshots repository for TamboUI artifacts.
- Use `tamboui-tui` and `tamboui-panama-backend`, plus only the direct TamboUI modules required by
  the selected APIs.
- Resolve the available `0.5.0-SNAPSHOT` artifacts once during implementation and record the same
  immutable timestamped snapshot version for every direct TamboUI dependency. Do not use `LATEST`
  or a mutable `-SNAPSHOT` version in the finished `pom.xml`.
- Verify the resolved dependency tree contains no TamboUI JLine 3 backend and no JLine 3 artifacts.
- Remove ArchLens's direct JLine dependency if no production source uses it after migration.
- Keep all TamboUI types inside the dashboard presentation package so replacing the experimental
  framework does not affect command dispatch, tool registration, or MCP mode.

The first implementation step is a compile-and-render compatibility probe using Java 25 and the
chosen timestamped artifacts. If the Panama backend cannot initialize on the supported terminal
environment, stop before migrating dashboard code and reassess the backend choice. Do not silently
fall back to the JLine 3 backend.

## Architecture

The existing responsibilities remain separated as follows:

- `ReplCommandParser` parses the existing `<tool> key=value` language.
- `ReplEngine` owns tool lookup, help, completion candidates, dispatch, timing, and conversion of
  tool outcomes into `DashboardEvent` values.
- `DashboardState` remains the framework-independent application view-model containing the short
  system log and current command event.
- `Dashboard` remains the standalone-mode entry point and owns application lifetime. It creates the
  TamboUI runner/view and connects submitted commands to `ReplEngine`.
- A new `TambouiDashboardView` owns widget construction, layout, focus, key routing, scrolling, and
  conversion of `DashboardState` content into TamboUI text values.
- A small UI state object owns the current input buffer, focused region, command-history position,
  completion state, and independent traversal/result scroll positions. UI state must not leak into
  `DashboardState`.

`DashboardRenderer` is removed. No replacement class assembles a terminal screen as strings or
implements padding, substring truncation, display-width calculations, cursor movement, or manual
screen clearing. TamboUI's widgets, layout constraints, buffers, and terminal backend own those
responsibilities.

Text construction used to produce tool help or command result content inside `ReplEngine` is not
screen rendering and remains in scope as ordinary formatting.

## Layout and Responsive Behavior

The normal layout contains four regions:

1. A compact header with the ArchLens version and the latest workspace/indexing status.
2. A main content area with a traversal panel on the left and command/result panel on the right.
3. A persistent single-line command input.
4. A one-line status bar showing focus, busy/error state, and relevant shortcuts.

The main area uses layout constraints rather than calculated string widths. At widths where two
panes would no longer be usable, it switches to a vertical traversal/result stack. The breakpoint
is a presentation decision based on the frame width; content clipping and wrapping remain widget
responsibilities. At dimensions too small for the compact layout, the frame displays a single
`Terminal too small` message and minimum-size guidance.

Both content panels wrap text and retain independent scroll state. Borders and titles indicate the
focused panel. An idle dashboard shows the existing indexing hint and neutral empty-state content.
Errors use a distinct style in the result panel without writing outside the TUI frame.

## Interaction

- `Enter` submits the input when the command field is focused.
- `Up` and `Down` navigate command history while the input is focused.
- `Tab` completes tool names using candidates derived from `ReplEngine.tools()`; it does not move
  focus.
- `F6` cycles focus through input, traversal, and result regions.
- Arrow keys, Page Up, Page Down, Home, and End scroll the focused content panel.
- After a command completes, focus returns to the input and the new content panels start at their
  beginning. Users can focus either panel to inspect and scroll it.
- `Ctrl+C` exits. REPL command `:quit` continues to exit through the normal dispatch path.
- Terminal resize events trigger a frame redraw and responsive layout selection while preserving
  input, focus, command history, and valid scroll positions.

Mouse input, persistent history across launches, parameter forms, tool-selection menus, syntax
highlighting, and asynchronous command cancellation are not part of this migration.

## Command Execution and State Flow

The event path is:

1. TamboUI delivers an input event to `TambouiDashboardView`.
2. The view updates its UI-only state or emits a complete submitted command to `Dashboard`.
3. `Dashboard` marks the view busy and invokes `ReplEngine.dispatch()` synchronously.
4. A normal tool outcome or expected tool error becomes a `DashboardEvent` and is recorded in
   `DashboardState` exactly as today.
5. Indexing success also updates the system status with the first result line, preserving current
   behavior.
6. The next frame reads application and UI state and renders widgets into TamboUI's buffer.

Synchronous dispatch is deliberate for the first version. Commands are initiated explicitly, and
keeping dispatch on one thread avoids introducing cancellation, stale-result, and cross-thread UI
state. The status bar shows busy state before dispatch begins when the runner permits an immediate
draw. If long indexing makes terminal unresponsiveness materially harmful, background execution is
a separate follow-up design rather than an incidental part of this migration.

## Error and Terminal Lifecycle Handling

Expected parser, validation, and tool failures remain renderable events so the application stays
open. Unexpected framework, backend, or dispatch failures may terminate the dashboard, but closing
the TamboUI runner/backend must restore raw mode, mouse state if enabled by defaults, and the
alternate screen before the exception propagates.

Lifecycle cleanup uses structured resource ownership (`try` with resources or an equivalent
`finally` boundary) in `Dashboard.run()`. No application error path writes ad hoc ANSI escape
sequences. MCP stdio mode remains unchanged and must not initialize TamboUI or emit dashboard
output.

## Testing

Replace `DashboardRendererTest` with tests at the new boundaries:

- View-model/render-buffer tests for idle, successful, and error events.
- Long traversal and result content wraps and can be scrolled instead of being destructively
  truncated.
- Wide frames use side-by-side panels; narrow frames use the vertical stack; undersized frames use
  the minimum-size message.
- Focus changes and focused-panel scrolling update UI state without modifying `DashboardState`.
- Input submission, history navigation, tool-name completion, `F6`, and `Ctrl+C` route correctly.
- Resize preserves input and focus and clamps scroll offsets to the new content bounds.
- A terminal lifecycle test verifies backend closure on normal quit and exceptional exit through a
  replaceable runner/backend seam if TamboUI does not provide a ready-made test backend.

Existing `ReplCommandParserTest` and `ReplEngineTest` remain the regression suite for application
logic. Run the focused dashboard tests during development and the full Maven test suite plus
Spotless and SpotBugs before completion. Also inspect `mvn dependency:tree` to enforce the absence
of JLine 3.

## Documentation Changes

- Update the terminal dashboard section in `README.md` with the responsive layout and key bindings.
- Update the dashboard package responsibility in `docs/ARCHITECTURE.md` because TamboUI now owns
  layout, buffering, terminal input, and rendering.
- Do not change MCP tool documentation because the tool surface and command grammar are unchanged.

## Non-goals

- No changes to MCP transport, mode detection, tool schemas, graph access, or extraction behavior.
- No second network transport or concurrent MCP client and dashboard session.
- No parameter forms, mouse-first navigation, charts, tool catalog sidebar, or plugin system.
- No custom terminal layout engine, string-width utility, clipping detector, or ANSI renderer.
- No JLine 3 compatibility layer or custom TamboUI backend in the initial implementation.

## Acceptance Criteria

- Interactive startup presents the TamboUI dashboard through the Panama backend.
- Existing dashboard commands, REPL-only commands, tool completion, dispatch results, and traversal
  traces continue to work.
- Long content is wrapped and scrollable; resize and narrow layouts remain usable.
- Dashboard production code contains no manual screen-sized `StringBuilder` composition, line
  padding, substring clipping, ANSI cursor control, or full-screen clear/redraw loop.
- Closing normally or after an unexpected failure restores the user's terminal.
- MCP stdio mode behaves exactly as before.
- The dependency tree contains one pinned family of TamboUI artifacts and no JLine 3 artifacts.
- Focused tests and the full project verification suite pass.
