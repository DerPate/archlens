# Tamboui Dashboard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the handwritten JLine dashboard renderer with a responsive, scrollable, command-first TamboUI dashboard using the Panama backend.

**Architecture:** Keep parsing, tool dispatch, dashboard events, and application state framework-independent. Add a small UI-state object and a TamboUI view for input routing and buffer rendering, while `Dashboard` owns dispatch and a replaceable session boundary for terminal lifecycle testing. Use an explicit Panama backend so the application cannot silently load JLine 3.

**Tech Stack:** Java 25, Maven, TamboUI 0.5.0 (`tamboui-tui`, `tamboui-panama-backend`), JUnit 6, AssertJ, Palantir Java Format, SpotBugs

**Spec:** `docs/superpowers/specs/2026-09-16-tamboui-dashboard-design.md`

## Global Constraints

- Keep Java source compatible with Java 25.
- Pin TamboUI `0.5.0` from Maven Central; do not add a snapshot repository.
- Use the Panama backend explicitly; do not add `tamboui-jline3-backend` or any JLine 3 artifact.
- Preserve MCP stdio mode, tool schemas, graph access, extraction behavior, and the existing REPL command grammar.
- TamboUI types stay under `dev.dominikbreu.archlens.dashboard` and must not leak into MCP tools, cache, renderers, or extraction packages.
- Do not add manual screen-sized string composition, padding, substring clipping, ANSI cursor control, or full-screen clear/redraw code.
- Add Javadoc to every public type, method, or constructor touched by the implementation.
- Treat Javadoc as an implementation deliverable: public record components require `@param`, public methods require `@param`/`@return` where applicable, and the completed plan must include a Javadoc build/audit step before claiming completion.
- Preserve the user's pre-existing `README.md` work; merge the dashboard documentation into it without reverting unrelated lines.
- Do not commit generated `target/`, `.archlens-cache/`, or `dependency-reduced-pom.xml` content.

## File Structure

- Modify `pom.xml`: replace the direct JLine dependency with TamboUI 0.5.0 and enable native access in both executable-JAR manifests.
- Create `src/main/java/dev/dominikbreu/archlens/dashboard/DashboardUiState.java`: own input, history, completion, focus, and panel scroll offsets.
- Create `src/main/java/dev/dominikbreu/archlens/dashboard/DashboardAction.java`: package-private result of routing one input event (`redraw`, optional submitted command, quit).
- Create `src/main/java/dev/dominikbreu/archlens/dashboard/TambouiDashboardView.java`: translate events into `DashboardAction` values and render application/UI state into TamboUI widgets.
- Create `src/main/java/dev/dominikbreu/archlens/dashboard/DashboardSession.java`: package-private terminal-session seam used by `Dashboard` and lifecycle tests.
- Create `src/main/java/dev/dominikbreu/archlens/dashboard/TambouiDashboardSession.java`: explicit `PanamaBackend` plus `TuiRunner` adapter.
- Modify `src/main/java/dev/dominikbreu/archlens/dashboard/Dashboard.java`: dispatch submitted commands and own session lifetime.
- Delete `src/main/java/dev/dominikbreu/archlens/dashboard/DashboardRenderer.java`: remove handwritten screen rendering.
- Create `src/test/java/dev/dominikbreu/archlens/dashboard/DashboardUiStateTest.java`: state transitions, history, completion, focus, and scrolling.
- Create `src/test/java/dev/dominikbreu/archlens/dashboard/TambouiDashboardViewTest.java`: buffer rendering, responsive layout, errors, wrapping, and key routing.
- Create `src/test/java/dev/dominikbreu/archlens/dashboard/DashboardTest.java`: dispatch integration and terminal-session cleanup.
- Delete `src/test/java/dev/dominikbreu/archlens/dashboard/DashboardRendererTest.java`: superseded string-renderer tests.
- Modify `README.md`: document responsive panes, scrolling, focus, and shortcuts.
- Modify `docs/ARCHITECTURE.md`: update the dashboard package responsibility and generated class/dependency listings as required by this repository's architecture-doc workflow.

---

### Task 1: Add TamboUI 0.5.0 and prove the pinned API

**Files:**
- Modify: `pom.xml`
- Test: `src/test/java/dev/dominikbreu/archlens/dashboard/TambouiCompatibilityTest.java`

**Interfaces:**
- Consumes: Java 25 and the existing shaded executable-JAR build.
- Produces: TamboUI core/widgets/TUI APIs and `PanamaBackend` on the compile/runtime classpath; executable JAR manifest with `Enable-Native-Access: ALL-UNNAMED`.

- [ ] **Step 1: Write a compile-and-buffer-render compatibility test**

Create `TambouiCompatibilityTest` before adding the dependency so the test initially fails to compile:

```java
package dev.dominikbreu.archlens.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Overflow;
import dev.tamboui.widgets.paragraph.Paragraph;
import org.junit.jupiter.api.Test;

class TambouiCompatibilityTest {
    @Test
    void paragraph_wrapsIntoABoundedBuffer() {
        Rect area = new Rect(0, 0, 8, 2);
        Buffer buffer = Buffer.empty(area);
        Paragraph.builder()
                .text("alpha beta gamma")
                .overflow(Overflow.WRAP_WORD)
                .build()
                .render(area, buffer);

        assertThat(buffer.get(0, 0).symbol()).isEqualTo("a");
        assertThat(buffer.get(0, 1).symbol()).isEqualTo("b");
    }
}
```

- [ ] **Step 2: Run the compatibility test and verify the missing dependency failure**

Run:

```bash
mvn -Dtest=TambouiCompatibilityTest test
```

Expected: compilation fails because `dev.tamboui.*` packages are not on the classpath.

- [ ] **Step 3: Replace JLine with released TamboUI dependencies**

In `pom.xml`, replace `<jline.version>` with:

```xml
<tamboui.version>0.5.0</tamboui.version>
```

Remove the `org.jline:jline` dependency and add:

```xml
<dependency>
    <groupId>dev.tamboui</groupId>
    <artifactId>tamboui-tui</artifactId>
    <version>${tamboui.version}</version>
</dependency>
<dependency>
    <groupId>dev.tamboui</groupId>
    <artifactId>tamboui-panama-backend</artifactId>
    <version>${tamboui.version}</version>
</dependency>
```

Do not add a `<repositories>` section: `0.5.0` is on Maven Central.

- [ ] **Step 4: Enable Panama native access for `java -jar`**

Add this manifest entry under `maven-jar-plugin`'s `<archive>`:

```xml
<manifestEntries>
    <Enable-Native-Access>ALL-UNNAMED</Enable-Native-Access>
</manifestEntries>
```

Add the matching entry to the shade plugin's existing `ManifestResourceTransformer`:

```xml
<manifestEntries>
    <Enable-Native-Access>ALL-UNNAMED</Enable-Native-Access>
</manifestEntries>
```

This keeps both the ordinary and shaded executable JAR manifests correct.

- [ ] **Step 5: Run the compatibility test**

Run:

```bash
mvn -Dtest=TambouiCompatibilityTest test
```

Expected: PASS, proving the pinned 0.5.0 `Paragraph`, `Overflow`, `Buffer`, and `Rect` APIs compile and render.

- [ ] **Step 6: Verify dependency and manifest constraints**

Run:

```bash
mvn -DskipTests package
mvn dependency:tree -Dincludes=dev.tamboui:*,org.jline:*
unzip -p target/archlens.jar META-INF/MANIFEST.MF
```

Expected:

- Every `dev.tamboui` line is version `0.5.0`.
- No `org.jline` or `tamboui-jline3-backend` line appears.
- The manifest contains `Enable-Native-Access: ALL-UNNAMED`.

- [ ] **Step 7: Commit the dependency gate**

```bash
git add pom.xml src/test/java/dev/dominikbreu/archlens/dashboard/TambouiCompatibilityTest.java
git commit -m "build: add Tamboui Panama dashboard runtime"
```

---

### Task 2: Implement framework-local dashboard UI state

**Files:**
- Create: `src/main/java/dev/dominikbreu/archlens/dashboard/DashboardUiState.java`
- Test: `src/test/java/dev/dominikbreu/archlens/dashboard/DashboardUiStateTest.java`

**Interfaces:**
- Consumes: `dev.tamboui.widgets.input.TextInputState`.
- Produces: package-private `DashboardUiState`, `DashboardUiState.Focus`, and methods `input()`, `focus()`, `cycleFocus()`, `submit()`, `previousCommand()`, `nextCommand()`, `complete(List<String>)`, `resetCompletion()`, `scrollUp(int)`, `scrollDown(int)`, `scrollHome()`, `scrollEnd()`, `scrollOffset(Focus)`, and `resetPanelScroll()`.

- [ ] **Step 1: Write failing state tests**

Create focused tests covering all state behavior:

```java
class DashboardUiStateTest {
    private final DashboardUiState state = new DashboardUiState();

    @Test
    void submit_recordsNonBlankHistory_andClearsInput() {
        state.input().insert("list_apps");

        assertThat(state.submit()).contains("list_apps");
        assertThat(state.input().text()).isEmpty();

        state.previousCommand();
        assertThat(state.input().text()).isEqualTo("list_apps");
        assertThat(state.input().cursorPosition()).isEqualTo("list_apps".length());
    }

    @Test
    void tabCompletion_cyclesMatchingToolNames() {
        state.input().insert("find_");

        assertThat(state.complete(List.of("find_components", "find_entrypoints", "list_apps")))
                .isTrue();
        assertThat(state.input().text()).isEqualTo("find_components");

        state.complete(List.of("find_components", "find_entrypoints", "list_apps"));
        assertThat(state.input().text()).isEqualTo("find_entrypoints");
    }

    @Test
    void focusAndScroll_areIndependentPerPanel() {
        state.cycleFocus();
        assertThat(state.focus()).isEqualTo(DashboardUiState.Focus.TRAVERSAL);
        state.scrollDown(5);
        state.cycleFocus();
        assertThat(state.focus()).isEqualTo(DashboardUiState.Focus.RESULT);
        state.scrollDown(2);

        assertThat(state.scrollOffset(DashboardUiState.Focus.TRAVERSAL)).isEqualTo(5);
        assertThat(state.scrollOffset(DashboardUiState.Focus.RESULT)).isEqualTo(2);
    }
}
```

Also test blank submissions, duplicate consecutive history entries, forward history returning to a blank input, completion reset after editing, Page Up/Down-sized deltas, Home, End using `Integer.MAX_VALUE`, and `resetPanelScroll()`.

- [ ] **Step 2: Run the state tests and verify they fail**

Run:

```bash
mvn -Dtest=DashboardUiStateTest test
```

Expected: test compilation fails because `DashboardUiState` does not exist.

- [ ] **Step 3: Implement `DashboardUiState` minimally**

Use this structure:

```java
final class DashboardUiState {
    enum Focus { INPUT, TRAVERSAL, RESULT }

    private final TextInputState input = new TextInputState();
    private final List<String> history = new ArrayList<>();
    private Focus focus = Focus.INPUT;
    private int historyIndex;
    private int traversalScroll;
    private int resultScroll;
    private String completionPrefix;
    private List<String> completionCandidates = List.of();
    private int completionIndex = -1;

    Optional<String> submit() {
        String command = input.text().trim();
        if (command.isEmpty()) return Optional.empty();
        if (history.isEmpty() || !history.getLast().equals(command)) history.add(command);
        historyIndex = history.size();
        input.clear();
        resetCompletion();
        return Optional.of(command);
    }

    boolean complete(List<String> names) {
        if (!completionCandidates.isEmpty()
                && input.text().equals(completionCandidates.get(completionIndex))) {
            completionIndex = (completionIndex + 1) % completionCandidates.size();
        } else {
            completionPrefix = input.text();
            if (completionPrefix.chars().anyMatch(Character::isWhitespace)) return false;
            completionCandidates = names.stream()
                    .filter(name -> name.startsWith(completionPrefix))
                    .sorted()
                    .toList();
            if (completionCandidates.isEmpty()) return false;
            completionIndex = 0;
        }
        input.setText(completionCandidates.get(completionIndex));
        input.moveCursorToEnd();
        return true;
    }

    void cycleFocus() {
        focus = switch (focus) {
            case INPUT -> Focus.TRAVERSAL;
            case TRAVERSAL -> Focus.RESULT;
            case RESULT -> Focus.INPUT;
        };
    }

    void scrollDown(int amount) {
        long next = (long) focusedScroll() + Math.max(0, amount);
        setFocusedScroll((int) Math.min(Integer.MAX_VALUE, next));
    }

    void scrollUp(int amount) {
        setFocusedScroll(Math.max(0, focusedScroll() - Math.max(0, amount)));
    }

    void scrollHome() { setFocusedScroll(0); }
    void scrollEnd() { setFocusedScroll(Integer.MAX_VALUE); }
}
```

Whenever history or completion calls `input.setText(...)`, immediately call
`input.moveCursorToEnd()` because TamboUI preserves the prior cursor index when replacing text.
Use saturating integer addition for scroll offsets so repeated Page Down cannot overflow.

- [ ] **Step 4: Run and format the state tests**

Run:

```bash
mvn spotless:apply
mvn -Dtest=DashboardUiStateTest test
```

Expected: PASS.

- [ ] **Step 5: Commit UI state**

```bash
git add src/main/java/dev/dominikbreu/archlens/dashboard/DashboardUiState.java \
  src/test/java/dev/dominikbreu/archlens/dashboard/DashboardUiStateTest.java
git commit -m "feat: add dashboard interaction state"
```

---

### Task 3: Render the responsive TamboUI dashboard

**Files:**
- Create: `src/main/java/dev/dominikbreu/archlens/dashboard/TambouiDashboardView.java`
- Create: `src/test/java/dev/dominikbreu/archlens/dashboard/TambouiDashboardViewTest.java`

**Interfaces:**
- Consumes: `DashboardState`, `DashboardEvent`, `DashboardUiState`, server version, and a list of command completion names.
- Produces: package-private constructor `TambouiDashboardView(DashboardState, String, List<String>)`, `void render(Frame)`, `DashboardAction handle(Event)`, `void setBusy(boolean)`, and `void commandCompleted()`.
- Task 4 will supply `DashboardAction`; for this task create the record with render-neutral factories `none()`, `redraw()`, `submit(String)`, and `quit()` so render tests compile.

- [ ] **Step 1: Add failing wide, narrow, small, success, and error buffer tests**

Create a helper that renders without a real terminal:

```java
private static Buffer render(TambouiDashboardView view, int width, int height) {
    Buffer buffer = Buffer.empty(new Rect(0, 0, width, height));
    view.render(Frame.forTesting(buffer));
    return buffer;
}

private static String content(Buffer buffer) {
    StringBuilder text = new StringBuilder();
    for (int y = 0; y < buffer.height(); y++) {
        for (int x = 0; x < buffer.width(); x++) {
            text.append(buffer.get(x, y).symbol());
        }
        text.append('\n');
    }
    return text.toString();
}
```

Tests must assert:

```java
@Test
void wideFrame_placesTraversalAndResultTitlesOnTheSameRow() {
    DashboardState state = activeState();
    String rendered = content(render(new TambouiDashboardView(state, "2.1.0", List.of()), 120, 24));

    String titleRow = rendered.lines().filter(line -> line.contains("Traversal")).findFirst().orElseThrow();
    assertThat(titleRow).contains("Result");
}

@Test
void narrowFrame_stacksTraversalAboveResult() {
    String rendered = content(render(new TambouiDashboardView(activeState(), "2.1.0", List.of()), 70, 28));

    assertThat(rendered.indexOf("Traversal")).isLessThan(rendered.indexOf("Result"));
    assertThat(rendered.lines().filter(line -> line.contains("Traversal") && line.contains("Result")))
            .isEmpty();
}

@Test
void undersizedFrame_showsOnlySizeGuidance() {
    String rendered = content(render(new TambouiDashboardView(new DashboardState(), "2.1.0", List.of()), 30, 6));
    assertThat(rendered).contains("Terminal too small").doesNotContain("Traversal");
}
```

Add assertions that idle text, version/system status, traversal trace, command/duration/result,
and styled `ERROR` content appear. Add a long unbroken result and assert its tail becomes visible
after setting result focus and scrolling, demonstrating wrapping/scrolling rather than destructive
source truncation. Toggle `setBusy(true)` and assert the status bar displays `Running command…`.
Render the same view into a second, differently sized buffer and assert its input text and focused
panel are preserved while TamboUI clamps the requested scroll offset to visible content rather than
rendering a blank panel.

- [ ] **Step 2: Run the view tests and verify they fail**

Run:

```bash
mvn -Dtest=TambouiDashboardViewTest test
```

Expected: compilation fails because the view and action types do not exist.

- [ ] **Step 3: Add `DashboardAction`**

Create this package-private record:

```java
record DashboardAction(boolean redraw, String submittedCommand, boolean quit) {
    static DashboardAction none() { return new DashboardAction(false, null, false); }
    static DashboardAction redraw() { return new DashboardAction(true, null, false); }
    static DashboardAction submit(String command) { return new DashboardAction(true, command, false); }
    static DashboardAction quit() { return new DashboardAction(false, null, true); }
}
```

- [ ] **Step 4: Implement layout and widget rendering**

Implement `TambouiDashboardView.render(Frame)` with constants `MIN_WIDTH = 40`, `MIN_HEIGHT = 10`,
and `SIDE_BY_SIDE_WIDTH = 90`. Split the full area vertically using:

```java
List<Rect> rows = Layout.vertical()
        .constraints(Constraint.length(1), Constraint.fill(), Constraint.length(3), Constraint.length(1))
        .split(frame.area());
```

For the main row, use `Layout.horizontal()` with two `Constraint.percentage(50)` values at widths
of at least 90, otherwise `Layout.vertical()` with two `Constraint.percentage(50)` values. Render
both content areas with `Paragraph.builder()` using `Overflow.WRAP_CHARACTER`, a rounded bordered
`Block`, and the corresponding `DashboardUiState` scroll offset. Use cyan borders for the focused
panel and a neutral color otherwise.

Render the input with `TextInput.renderWithCursor(...)` only when input is focused; otherwise use
the normal stateful render path. Build result `Text` as styled `Line`/`Span` values so errors can be
red without embedding ANSI sequences. Keep command, duration, and result content as separate lines.

- [ ] **Step 5: Run the view tests**

Run:

```bash
mvn spotless:apply
mvn -Dtest=TambouiDashboardViewTest test
```

Expected: PASS for idle, success, error, wide, narrow, undersized, wrapping, and scrolling cases.

- [ ] **Step 6: Commit responsive rendering**

```bash
git add src/main/java/dev/dominikbreu/archlens/dashboard/DashboardAction.java \
  src/main/java/dev/dominikbreu/archlens/dashboard/TambouiDashboardView.java \
  src/test/java/dev/dominikbreu/archlens/dashboard/TambouiDashboardViewTest.java
git commit -m "feat: render responsive Tamboui dashboard"
```

---

### Task 4: Route keyboard input, completion, focus, and scrolling

**Files:**
- Modify: `src/main/java/dev/dominikbreu/archlens/dashboard/TambouiDashboardView.java`
- Modify: `src/test/java/dev/dominikbreu/archlens/dashboard/TambouiDashboardViewTest.java`

**Interfaces:**
- Consumes: TamboUI `Event`, `KeyEvent`, `PasteEvent`, `KeyCode`, and `DashboardUiState` methods from Task 2.
- Produces: complete `DashboardAction handle(Event)` behavior for `Dashboard` in Task 5.

- [ ] **Step 1: Add failing event-routing tests**

Use TamboUI event factories directly:

```java
@Test
void typingAndEnter_submitTheCommand() {
    TambouiDashboardView view = idleView(List.of("list_apps"));
    for (int codePoint : "list_apps".codePoints().toArray()) {
        assertThat(view.handle(KeyEvent.ofChar(codePoint)).redraw()).isTrue();
    }

    DashboardAction action = view.handle(KeyEvent.ofKey(KeyCode.ENTER));

    assertThat(action.submittedCommand()).isEqualTo("list_apps");
}

@Test
void plainQ_isInput_butCtrlCQuits() {
    TambouiDashboardView view = idleView(List.of());

    assertThat(view.handle(KeyEvent.ofChar('q')).quit()).isFalse();
    assertThat(view.handle(KeyEvent.ofChar('c', KeyModifiers.CTRL)).quit()).isTrue();
}

@Test
void f6MovesFocus_andPageDownScrollsFocusedPane() {
    TambouiDashboardView view = idleView(List.of());
    view.handle(KeyEvent.ofKey(KeyCode.F6));
    view.handle(KeyEvent.ofKey(KeyCode.PAGE_DOWN));

    assertThat(view.uiState().focus()).isEqualTo(DashboardUiState.Focus.TRAVERSAL);
    assertThat(view.uiState().scrollOffset(DashboardUiState.Focus.TRAVERSAL)).isPositive();
}
```

Also test Tab completion, Up/Down history, Left/Right/Home/End editing while input is focused,
character and bracketed-paste insertion, arrow/Page/Home/End scrolling while a content panel is
focused, unknown events returning `DashboardAction.none()`, and `commandCompleted()` restoring input
focus and resetting both scroll offsets.

- [ ] **Step 2: Run the routing tests and verify they fail**

Run:

```bash
mvn -Dtest=TambouiDashboardViewTest test
```

Expected: new routing assertions fail because `handle` is not fully implemented.

- [ ] **Step 3: Implement input-first event routing**

Implement the following precedence in `handle(Event)`:

1. `KeyEvent.isCtrlC()` returns `DashboardAction.quit()`; do not use `isQuit()` because its default
   bindings also treat plain `q` as quit.
2. `F6` cycles focus.
3. When input is focused: Enter submits, Tab completes, Up/Down navigate history, editing keys
   mutate `TextInputState`, and printable unmodified `CHAR` events insert `event.string()`.
4. When traversal/result is focused: arrows scroll by one, Page keys by the last rendered viewport
   height, and Home/End call the matching `DashboardUiState` methods.
5. A `PasteEvent` inserts its complete text only while input is focused.
6. Every non-Tab input edit calls `resetCompletion()`.

Track the last rendered traversal/result viewport heights during `render(Frame)` and use at least
one line when no frame has rendered yet. Return `DashboardAction.redraw()` only for state changes.

- [ ] **Step 4: Run the complete view suite**

Run:

```bash
mvn spotless:apply
mvn -Dtest=DashboardUiStateTest,TambouiDashboardViewTest test
```

Expected: PASS.

- [ ] **Step 5: Commit interaction handling**

```bash
git add src/main/java/dev/dominikbreu/archlens/dashboard/TambouiDashboardView.java \
  src/test/java/dev/dominikbreu/archlens/dashboard/TambouiDashboardViewTest.java
git commit -m "feat: add dashboard keyboard interaction"
```

---

### Task 5: Replace the JLine loop and remove handwritten rendering

**Files:**
- Create: `src/main/java/dev/dominikbreu/archlens/dashboard/DashboardSession.java`
- Create: `src/main/java/dev/dominikbreu/archlens/dashboard/TambouiDashboardSession.java`
- Modify: `src/main/java/dev/dominikbreu/archlens/dashboard/Dashboard.java`
- Delete: `src/main/java/dev/dominikbreu/archlens/dashboard/DashboardRenderer.java`
- Create: `src/test/java/dev/dominikbreu/archlens/dashboard/DashboardTest.java`
- Delete: `src/test/java/dev/dominikbreu/archlens/dashboard/DashboardRendererTest.java`

**Interfaces:**
- Consumes: `TambouiDashboardView.handle(Event)`, `TambouiDashboardView.render(Frame)`, `ReplEngine.dispatch(String)`, and `DashboardState`.
- Produces: `DashboardSession` with `run(Function<Event, Boolean>, Consumer<Frame>)`, `draw(Consumer<Frame>)`, `quit()`, and `close()`; `TambouiDashboardSession.open()`; unchanged public `Dashboard(List<SyncToolSpecification>, String)` and `run()` API.

- [ ] **Step 1: Write failing dashboard dispatch and lifecycle tests**

Create a fake package-private session implementation in `DashboardTest` that records `close()` and
can feed events to the installed handler. Add a package-private `Dashboard` constructor accepting
`DashboardSessionFactory` solely for this seam.

```java
@Test
void submittedCommand_dispatchesAndUpdatesState() throws IOException {
    List<Event> events = new ArrayList<>();
    ":help".codePoints().mapToObj(KeyEvent::ofChar).forEach(events::add);
    events.add(KeyEvent.ofKey(KeyCode.ENTER));
    FakeSession session = new FakeSession(events);
    Dashboard dashboard = dashboardWith(List.of(), () -> session);

    dashboard.run();

    assertThat(dashboard.state().currentEvent().resultText()).contains("Commands:");
    assertThat(session.closed).isTrue();
}

@Test
void exceptionalSession_isStillClosed() {
    FakeSession session = new FakeSession(new IllegalStateException("boom"));

    assertThatThrownBy(() -> dashboardWith(List.of(), () -> session).run())
            .isInstanceOf(IOException.class)
            .hasCauseInstanceOf(IllegalStateException.class);
    assertThat(session.closed).isTrue();
}
```

Also test that a `:quit` submission invokes `session.quit()`, successful `index_workspace` keeps the
existing first-line system-status update, and normal commands record their `DashboardEvent`.

- [ ] **Step 2: Run `DashboardTest` and verify it fails**

Run:

```bash
mvn -Dtest=DashboardTest test
```

Expected: compilation fails because the session seam and injectable constructor do not exist.

- [ ] **Step 3: Add the session abstraction and explicit Panama adapter**

Use package-private interfaces:

```java
interface DashboardSession extends AutoCloseable {
    void run(Function<Event, Boolean> eventHandler, Consumer<Frame> renderer) throws Exception;
    void draw(Consumer<Frame> renderer);
    void quit();
    @Override void close();
}

@FunctionalInterface
interface DashboardSessionFactory {
    DashboardSession open() throws Exception;
}
```

Implement `TambouiDashboardSession.open()` with:

```java
TuiConfig config = TuiConfig.builder()
        .backend(new PanamaBackend())
        .mouseCapture(false)
        .bracketedPaste(true)
        .noTick()
        .build();
return new TambouiDashboardSession(TuiRunner.create(config));
```

Adapt `run` using `runner.run((event, ignored) -> eventHandler.apply(event), renderer::accept)`,
adapt `draw` using `runner.draw(renderer)`, and delegate `quit()` and `close()` directly. Explicit
`new PanamaBackend()` is mandatory: do not use service-loader fallback.

- [ ] **Step 4: Rewrite `Dashboard` around the session and view**

Remove every JLine import, terminal operation, completer, redraw method, and handcrafted loop.
Build completion names from `engine.tools()` plus `:help`, `:tools`, and `:quit`. In the session
handler:

```java
DashboardAction action = view.handle(event);
if (action.quit()) {
    session.quit();
    return false;
}
if (action.submittedCommand() != null) {
    view.setBusy(true);
    session.draw(view::render);
    DispatchResult result;
    try {
        result = engine.dispatch(action.submittedCommand());
    } finally {
        view.setBusy(false);
    }
    if (result.quit()) {
        session.quit();
        return false;
    }
    record(result.event());
    view.commandCompleted();
    return true;
}
return action.redraw();
```

Retain `firstLine` and the successful `index_workspace` status update in a focused `record` method.
Keep public `run() throws IOException`; wrap non-`IOException` session failures in an `IOException`
with message `Unable to run standalone dashboard` while preserving the cause. The try-with-resources
around `DashboardSession` must cover the entire `run` call.

- [ ] **Step 5: Delete the obsolete renderer and its tests**

Delete `DashboardRenderer.java` and `DashboardRendererTest.java`. Confirm no production dashboard
code uses `StringBuilder`, `substring` clipping, `InfoCmp`, `TerminalBuilder`, `LineReader`, or ANSI
escape strings.

- [ ] **Step 6: Run focused dashboard tests**

Run:

```bash
mvn spotless:apply
mvn -Dtest='dev.dominikbreu.archlens.dashboard.*' test
rg -n "StringBuilder|substring|InfoCmp|TerminalBuilder|LineReader|clear_screen|\\\\u001b" \
  src/main/java/dev/dominikbreu/archlens/dashboard
```

Expected: all dashboard tests pass; the search finds no manual terminal rendering or JLine code.

- [ ] **Step 7: Commit the runtime migration**

```bash
git add src/main/java/dev/dominikbreu/archlens/dashboard \
  src/test/java/dev/dominikbreu/archlens/dashboard
git commit -m "feat: migrate dashboard runtime to Tamboui"
```

---

### Task 6: Document and verify the completed dashboard

**Files:**
- Modify: `README.md`
- Modify: `docs/ARCHITECTURE.md`

**Interfaces:**
- Consumes: completed dashboard behavior and key bindings.
- Produces: accurate user and architecture documentation; fully verified build artifact.

- [ ] **Step 0: Audit public API documentation before final verification**

Run the Javadoc build and inspect every touched public declaration:

```bash
mvn -DskipTests javadoc:javadoc
rg -n "public (final )?(class|record|interface)|public (static )?[A-Za-z].*\\(" \
  src/main/java/dev/dominikbreu/archlens/dashboard
```

Expected: Javadoc generation succeeds without new errors, `Dashboard`'s existing public API
documentation remains accurate, and any newly public declaration has a Javadoc block. Keep the new
session, action, UI-state, and view types package-private unless a public API is genuinely required;
package-private implementation types do not need public API documentation.

- [ ] **Step 1: Update user documentation without overwriting existing edits**

Edit only the terminal-dashboard paragraphs and command guidance in `README.md`. Describe:

- responsive side-by-side/stacked traversal and result panes;
- wrapping and independent scrolling;
- Enter, Up/Down history, Tab completion, F6 focus, arrows/Page/Home/End scrolling, Ctrl+C, and
  `:quit`;
- `java -jar target/archlens.jar` remaining the only required launch command.

Inspect `git diff -- README.md` before staging and preserve every unrelated pre-existing user hunk.

- [ ] **Step 2: Update architecture documentation**

Update the dashboard package section/class listing in `docs/ARCHITECTURE.md` so it shows
`TambouiDashboardView`, `DashboardUiState`, `DashboardSession`, and `TambouiDashboardSession`, and no
longer lists `DashboardRenderer`. Describe TamboUI as the presentation/terminal boundary and
`ReplEngine`/`DashboardState` as framework-independent.

- [ ] **Step 3: Run formatting and full verification**

Run:

```bash
mvn spotless:apply
mvn verify
mvn dependency:analyze
mvn dependency:tree -Dincludes=dev.tamboui:*,org.jline:*
git diff --check
```

Expected:

- `mvn verify` passes, including all tests, Spotless, and SpotBugs.
- Dependency analysis reports no new used/undeclared or unused/declared regression.
- TamboUI artifacts are all `0.5.0`; no JLine artifacts appear.
- `git diff --check` is clean.

- [ ] **Step 4: Package and perform an interactive pseudo-terminal smoke test**

Run:

```bash
mvn -DskipTests package
unzip -p target/archlens.jar META-INF/MANIFEST.MF | rg "Main-Class|Enable-Native-Access"
printf '\003' | script -qec 'java --illegal-native-access=deny -jar target/archlens.jar' /dev/null
```

Expected: the manifest contains the main class and native-access entry; the TamboUI dashboard opens
through a pseudo-terminal and Ctrl+C exits without `IllegalCallerException`, backend errors, or a
corrupted shell. If `script` is unavailable, run `java --illegal-native-access=deny -jar
target/archlens.jar` in a real terminal, confirm the layout, resize once, execute `:help`, focus and
scroll both panes, then exit with Ctrl+C.

- [ ] **Step 5: Review the final diff for scope and generated files**

Run:

```bash
git status --short
git diff --stat
git diff -- README.md docs/ARCHITECTURE.md pom.xml src/main/java/dev/dominikbreu/archlens/dashboard \
  src/test/java/dev/dominikbreu/archlens/dashboard
```

Expected: only planned source, tests, build configuration, and documentation are changed; no
`target/`, cache, or dependency-reduced POM is staged. Verify the user's original README changes
remain present.

- [ ] **Step 6: Commit documentation and final verification state**

```bash
git add README.md docs/ARCHITECTURE.md
git commit -m "docs: document Tamboui terminal dashboard"
```
