<script lang="ts">
  import Sigma from 'sigma';
  import { onMount, tick } from 'svelte';
  import { applyPreset, createInitialFilterState, visibleGraph } from './graph/filters';
  import { assignForceAtlasLayout, buildGraph } from './graph/layout';
  import { loadGraphFromFile, loadGraphFromUrl } from './graph/loadGraph';
  import { pipelineSummaries, selectedPipelineGraph } from './graph/pipelines';
  import { GRAPH_PRESETS } from './graph/presets';
  import type { PipelineSummary } from './graph/pipelines';
  import type { FilterState, GraphEdge, GraphNode, GraphPayload, Preset } from './graph/types';

  let container: HTMLElement;
  let renderer: Sigma | null = null;
  let payload: GraphPayload | null = null;
  let state: FilterState | null = null;
  let selected: GraphNode | GraphEdge | null = null;
  let status = 'Load an exported graph JSON file.';
  let exploreClickedNode = false;
  let edgeOpacity = 0.26;
  let renderLabels = true;
  let renderEdgeLabels = false;
  let layoutName = 'packed grid';
  let selectedPipelineId: string | null = null;
  let pipelineSearch = '';

  $: nodeLabels = payload ? Object.keys(payload.snapshot.metadata.labels).sort() : [];
  $: edgeLabels = payload ? Object.keys(payload.snapshot.metadata.edges).sort() : [];
  $: shown = payload && state ? visibleGraph(payload, state) : { nodes: [], edges: [] };
  $: pipelines = payload ? pipelineSummaries(payload) : [];
  $: filteredPipelines = pipelines.filter((pipeline) =>
    `${pipeline.title} ${pipeline.subtitle} ${pipeline.segments.map((segment) => segment.title).join(' ')}`
      .toLowerCase()
      .includes(pipelineSearch.toLowerCase())
  );

  onMount(() => {
    const graphUrl = new URLSearchParams(location.search).get('graph') ?? '/phoenix-graph.json';
    void loadFromUrl(graphUrl, false);
  });

  async function loadFromUrl(url: string, loud = true) {
    try {
      status = `Loading ${url}`;
      setPayload(await loadGraphFromUrl(url));
    } catch (error) {
      status = loud ? String(error) : 'Load an exported graph JSON file.';
    }
  }

  async function loadFromInput(event: Event) {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    try {
      status = `Loading ${file.name}`;
      setPayload(await loadGraphFromFile(file));
    } catch (error) {
      status = String(error);
    }
  }

  function setPayload(nextPayload: GraphPayload) {
    payload = nextPayload;
    state = createInitialFilterState(nextPayload);
    selectedPipelineId = pipelineSummaries(nextPayload)[0]?.id ?? null;
    selected = null;
    status = `Loaded ${nextPayload.snapshot.metadata.includedNodeCount} nodes, ${nextPayload.snapshot.metadata.includedEdgeCount} edges`;
    void renderGraph();
  }

  function choosePreset(preset: Preset) {
    if (!state) return;
    selectedPipelineId = null;
    state = applyPreset(state, preset);
    selected = null;
    void renderGraph(true);
  }

  function resetFilters() {
    if (!payload) return;
    state = createInitialFilterState(payload);
    selectedPipelineId = pipelineSummaries(payload)[0]?.id ?? null;
    selected = null;
    void renderGraph();
  }

  function choosePipeline(pipeline: PipelineSummary) {
    selectedPipelineId = pipeline.id;
    selected = pipeline.node;
    void renderGraph(true);
  }

  function toggleNodeLabel(label: string, checked: boolean) {
    if (!state) return;
    const next = new Set(state.nodeLabels);
    if (checked) next.add(label);
    else next.delete(label);
    state = { ...state, nodeLabels: next };
    void renderGraph();
  }

  function toggleEdgeLabel(label: string, checked: boolean) {
    if (!state) return;
    const next = new Set(state.edgeLabels);
    if (checked) next.add(label);
    else next.delete(label);
    state = { ...state, edgeLabels: next };
    void renderGraph();
  }

  function updateSearch(search: string) {
    if (!state) return;
    state = { ...state, search, mode: search.trim() ? 'search' : 'overview' };
    void renderGraph();
  }

  function updateLimit(visibleLimit: number) {
    if (!state) return;
    state = { ...state, visibleLimit };
    void renderGraph();
  }

  function updateRadius(radius: number) {
    if (!state) return;
    state = { ...state, radius };
    void renderGraph();
  }

  function updateHideIsolated(hideIsolated: boolean) {
    if (!state) return;
    state = { ...state, hideIsolated };
    void renderGraph();
  }

  async function renderGraph(forceLayout = false) {
    if (!payload || !state || !container) return;
    await tick();
    const graphSlice = selectedPipelineId ? selectedPipelineGraph(payload, selectedPipelineId) : visibleGraph(payload, state);
    const graph = buildGraph(graphSlice.nodes, graphSlice.edges, nodeLabels);
    layoutName = 'packed grid';
    if (forceLayout || graph.order <= 1200) {
      assignForceAtlasLayout(graph);
      layoutName = graph.order <= 1600 ? 'ForceAtlas2' : 'packed grid';
    }

    const focusedView =
      Boolean(selectedPipelineId) || forceLayout || state.mode === 'search' || state.mode === 'neighborhood' || graph.order <= 450;
    renderer?.kill();
    renderer = new Sigma(graph, container, {
      allowInvalidContainer: true,
      defaultEdgeColor: `rgba(116, 130, 143, ${edgeOpacity})`,
      defaultEdgeType: 'line',
      labelRenderedSizeThreshold: focusedView ? 0 : 4,
      renderEdgeLabels,
      renderLabels
    });
    renderer.on('clickNode', (event) => selectNode(String(event.node)));
    renderer.on('clickEdge', (event) => selectEdge(String(event.edge)));
    status = `${graphSlice.nodes.length} visible nodes, ${graphSlice.edges.length} visible edges. Layout: ${layoutName}`;
  }

  function selectNode(nodeId: string) {
    if (!payload || !state || !renderer) return;
    const raw = renderer.getGraph().getNodeAttribute(nodeId, 'raw') as GraphNode;
    selected = raw;
    if (exploreClickedNode) {
      state = { ...state, selectedNodeId: nodeId, mode: 'neighborhood' };
      selectedPipelineId = null;
      void renderGraph(true);
    }
  }

  function exploreSelectedNode() {
    if (!state || !selected || !('id' in selected)) return;
    state = { ...state, selectedNodeId: selected.id, mode: 'neighborhood' };
    selectedPipelineId = null;
    void renderGraph(true);
  }

  function selectEdge(edgeId: string) {
    if (!renderer) return;
    selected = renderer.getGraph().getEdgeAttribute(edgeId, 'raw') as GraphEdge;
  }

  function soloNodeLabel(label: string) {
    if (!state) return;
    selectedPipelineId = null;
    state = { ...state, nodeLabels: new Set([label]), mode: 'overview', selectedNodeId: null };
    selected = null;
    void renderGraph(true);
  }
</script>

<div class="app-shell">
  <aside class="sidebar">
    <h1>Spoon Graph Viewer</h1>
    <p class="meta">{payload ? `${payload.snapshot.metadata.includedNodeCount} nodes, ${payload.snapshot.metadata.includedEdgeCount} edges loaded` : status}</p>
    {#if payload}
      <p class="meta">{status}</p>
    {/if}

    <section>
      <h2>Load</h2>
      <input type="file" accept="application/json,.json" on:change={loadFromInput} />
    </section>

    <section>
      <h2>Pipeline Explorer</h2>
      <input
        type="search"
        placeholder="Filter pipelines..."
        bind:value={pipelineSearch}
      />
      <div class="pipeline-list">
        {#each filteredPipelines as pipeline}
          <button
            type="button"
            class:selected-row={selectedPipelineId === pipeline.id}
            on:click={() => choosePipeline(pipeline)}
          >
            <strong>{pipeline.title}</strong>
            <span>{pipeline.subtitle}</span>
          </button>
        {/each}
      </div>
    </section>

    <section>
      <h2>Raw Quick Views</h2>
      <div class="button-grid">
        {#each GRAPH_PRESETS as preset}
          <button type="button" on:click={() => choosePreset(preset)}>{preset.name}</button>
        {/each}
      </div>
      <button type="button" on:click={resetFilters}>Reset overview</button>
    </section>

    <section>
      <h2>Search</h2>
      <input
        type="search"
        placeholder="Node id, label, property..."
        value={state?.search ?? ''}
        on:input={(event) => updateSearch((event.target as HTMLInputElement).value)}
      />
    </section>

    <section>
      <h2>Explore</h2>
      <label><input type="checkbox" bind:checked={exploreClickedNode} /> Auto-explore clicked node</label>
      <label><input type="checkbox" checked={state?.hideIsolated ?? false} on:change={(event) => updateHideIsolated((event.target as HTMLInputElement).checked)} /> Hide isolated</label>
      <label><input type="checkbox" bind:checked={renderLabels} on:change={() => void renderGraph()} /> Node names</label>
      <label><input type="checkbox" bind:checked={renderEdgeLabels} on:change={() => void renderGraph()} /> Edge labels</label>
      <label>
        Visible nodes
        <input type="number" min="25" max="5000" step="25" value={state?.visibleLimit ?? 350} on:change={(event) => updateLimit(Number((event.target as HTMLInputElement).value))} />
      </label>
      <label>
        Neighborhood radius
        <select value={state?.radius ?? 1} on:change={(event) => updateRadius(Number((event.target as HTMLSelectElement).value))}>
          <option value="1">1 hop</option>
          <option value="2">2 hops</option>
          <option value="3">3 hops</option>
        </select>
      </label>
      <label>
        Edge opacity
        <input type="range" min="0.05" max="0.8" step="0.01" bind:value={edgeOpacity} on:change={() => void renderGraph()} />
      </label>
    </section>

    <section>
      <h2>Node Types</h2>
      {#each nodeLabels as label}
        <label class="filter-row">
          <input type="checkbox" checked={state?.nodeLabels.has(label)} on:change={(event) => toggleNodeLabel(label, (event.target as HTMLInputElement).checked)} />
          <span>{label}</span>
          <button type="button" class="solo" on:click={() => soloNodeLabel(label)}>solo</button>
        </label>
      {/each}
    </section>

    <section>
      <h2>Edge Types</h2>
      {#each edgeLabels as label}
        <label><input type="checkbox" checked={state?.edgeLabels.has(label)} on:change={(event) => toggleEdgeLabel(label, (event.target as HTMLInputElement).checked)} /> {label}</label>
      {/each}
    </section>
  </aside>

  <main bind:this={container} class="canvas" aria-label="Interactive graph canvas"></main>

  <aside class="inspector">
    <h2>Selection</h2>
    {#if selectedPipelineId}
      {@const pipeline = pipelines.find((item) => item.id === selectedPipelineId)}
      {#if pipeline}
        <div class="pipeline-detail">
          <h3>{pipeline.title}</h3>
          <p>{pipeline.subtitle}</p>
          <ol>
            {#each pipeline.segments as segment}
              <li>
                <strong>{segment.title}</strong>
                {#if segment.linkKind || segment.viaChannel}
                  <span>{[segment.linkKind, segment.viaChannel].filter(Boolean).join(' / ')}</span>
                {/if}
              </li>
            {/each}
          </ol>
        </div>
      {/if}
    {/if}
    {#if selected && 'id' in selected}
      <button type="button" on:click={exploreSelectedNode}>Show neighborhood</button>
    {/if}
    <pre>{selected ? JSON.stringify(selected, null, 2) : 'Click a node or edge.'}</pre>
  </aside>
</div>
