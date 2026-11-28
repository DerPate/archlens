# Changelog

All notable changes to this project are documented in this file.

## [3.0.2](https://github.com/DerPate/archlens/compare/archlens-3.0.1...archlens-3.0.2) (2026-11-28)


### Bug Fixes

* **mcp:** speed up path search, understand "affected", report cut evidence ([42a8fea](https://github.com/DerPate/archlens/commit/42a8fea8961b28bb2db94463eb6414b0b7f19af0))
* **mcp:** speed up path search, understand "affected", report cut evidence ([3556a0d](https://github.com/DerPate/archlens/commit/3556a0d0826827bf75df836319bc5ba22f599f6d))


### Documentation

* **wiki:** record the render_pipeline review ([c9c0fd6](https://github.com/DerPate/archlens/commit/c9c0fd6806089d2ed429e71c59511a383d924228))
* **wiki:** record the render_pipeline review ([887f1db](https://github.com/DerPate/archlens/commit/887f1db349670b26481ff0960e38346dea2b042f))
* **wiki:** record the review and correct render_pipeline shapes ([c511e0a](https://github.com/DerPate/archlens/commit/c511e0ac6be56acfa4e6fa5cef89d5e45f739c1b))
* **wiki:** record the review and correct render_pipeline shapes ([fcd2945](https://github.com/DerPate/archlens/commit/fcd2945ac828af903c5195eea47a137ec25a9ccb))

## [3.0.1](https://github.com/DerPate/archlens/compare/archlens-3.0.0...archlens-3.0.1) (2026-11-22)


### Bug Fixes

* **index:** keep indexing inside the indexed projects ([c4827e7](https://github.com/DerPate/archlens/commit/c4827e715105bed61570e0ca4531d3a0f721867b))
* **index:** keep indexing inside the indexed projects ([7c6bf75](https://github.com/DerPate/archlens/commit/7c6bf754f18a84194c32b657f1bf7c4860d62dd1))
* **mcp:** stop truncating results silently and honor documented arguments ([5057447](https://github.com/DerPate/archlens/commit/50574473150a174d6d63c850bad1eb5b6233e133))
* **mcp:** stop truncating results silently and honor documented arguments ([4a078e2](https://github.com/DerPate/archlens/commit/4a078e212ddbcd3eaa3705a4047395b28b7b00c6))
* **merger:** read Ansible inventory directories ([d3e9112](https://github.com/DerPate/archlens/commit/d3e91123659a86324c153dea17e76bd2b078d564))
* **merger:** read Ansible inventory directories ([626ca25](https://github.com/DerPate/archlens/commit/626ca25cc2c90543eeb411fc35d5532a4cf4ed08))
* **site:** correct landing page claims ([4756c64](https://github.com/DerPate/archlens/commit/4756c64ce09972428ceee96a3660e2fc90e9b72c))
* **site:** correct tool behavior, install steps and narrow layouts ([0eab3ac](https://github.com/DerPate/archlens/commit/0eab3acf5a3447f718d2a24268d0023c74f4d493))
* **site:** document the 3.0 tool arguments and results ([be22003](https://github.com/DerPate/archlens/commit/be220033809bee6a95386eb28ef163b77c66ecc5))
* **site:** never deploy without a fresh API reference ([aa2e09e](https://github.com/DerPate/archlens/commit/aa2e09e01e7549d060f5d80e21668512cd9ce3f8))
* **site:** update the website for 3.0 and make deploys safe ([469a677](https://github.com/DerPate/archlens/commit/469a6778f624dcee4cff468e43091b780b366a9e))


### Documentation

* **api:** describe the current package boundaries ([23b4d1e](https://github.com/DerPate/archlens/commit/23b4d1e7392a06cf77985c87d1cdbbf52cac01d5))
* **site:** link releases and the 3.0 migration guide ([54cfb60](https://github.com/DerPate/archlens/commit/54cfb60f27dcdc12e5609da927e7054ad373ad62))
* **wiki:** record the review of the MCP server concept ([69d495f](https://github.com/DerPate/archlens/commit/69d495f85c7018ce5bf07ad231ddc6b71a20e63e))
* **wiki:** record the review of the MCP server concept ([e72e5be](https://github.com/DerPate/archlens/commit/e72e5bee79d4f34c3769403cbadf087c09b67a69))

## [3.0.0](https://github.com/DerPate/archlens/compare/archlens-2.3.0...archlens-3.0.0) (2026-11-22)


### ⚠ BREAKING CHANGES

* **mcp:** ARCHLENS_MCP_EXPERIMENTAL_DRAFT is ignored; collection results always use named-object roots such as {"entrypoints": [...]}. See docs/MIGRATION_3.0.md.
* **okf:** generated OKF concepts use x-archlens-* keys instead of archlens_*. ArchLens reads both, but 2.x cannot refresh concepts written by 3.x, and external readers of the old keys must be updated.
* **cache:** .archlens-cache/ in the working directory is no longer read or written; run index_workspace again after upgrading. Set ARCHLENS_CACHE_DIR to an absolute path to choose another location.

### Features

* **mcp:** remove the experimental draft output mode ([3cc8a51](https://github.com/DerPate/archlens/commit/3cc8a51da381e2d6ac9af5761c5ba03b27b2de6f))
* **mcp:** select a workspace by explicit id ([836bb05](https://github.com/DerPate/archlens/commit/836bb0531088ded3d5c53e7b36b80de62897d087))
* **okf:** migrate to OKF v0.3 ([e8374d8](https://github.com/DerPate/archlens/commit/e8374d8ea6179684950beff8a6c667f367936510))
* **okf:** write x-archlens frontmatter keys ([4a7faae](https://github.com/DerPate/archlens/commit/4a7faae4170a6df97815a04a333e81675ce5adff))


### Bug Fixes

* **cache:** move the cache to the per-user cache directory ([a8f2b53](https://github.com/DerPate/archlens/commit/a8f2b5362ec6fa2f02fcb1da67b1ad16959979e2))
* **deps:** patch advisories and remove Jackson 2 ([3f25198](https://github.com/DerPate/archlens/commit/3f251980e7f60ff2df1c02b8771871fc1ea01b10))
* **deps:** patch advisories and remove Jackson 2 ([d3c5ad5](https://github.com/DerPate/archlens/commit/d3c5ad54d42fbe3ea11aa274739aeef2987a07ee))
* **export:** keep generated files inside the project ([83a84c1](https://github.com/DerPate/archlens/commit/83a84c1628b4e2cc3a676db88d31bf3a222286e7))
* **export:** keep generated files inside the project ([8421002](https://github.com/DerPate/archlens/commit/842100260a0eedf4011ba76bcf387c6171547867))
* **extractor:** bind project calls when a library type is unresolved ([c86d1e6](https://github.com/DerPate/archlens/commit/c86d1e64421b9f25731f63c1f9841b9474f55d8b))
* **mcp:** emit tool schemas in deterministic order ([57bb0eb](https://github.com/DerPate/archlens/commit/57bb0ebd1d8d80ae591b20a544502ef89653c48e))
* **mcp:** emit tool schemas in the same order in every JVM ([ebff3b8](https://github.com/DerPate/archlens/commit/ebff3b86aef32e00ef149b1fdc597fbd5198adbe))
* **mcp:** run tool calls in arrival order ([b37391d](https://github.com/DerPate/archlens/commit/b37391d17156d64a3347c031c602ef2a749dd923))
* **mcp:** run tool calls in arrival order ([4e01302](https://github.com/DerPate/archlens/commit/4e01302a83c9f0d218c912502e0e82bd8d926996))
* **okf:** preserve human review on refresh ([845c797](https://github.com/DerPate/archlens/commit/845c7976760b15778d5059b387c6086173bf9c17))
* **okf:** preserve human review on refresh and repair log and link writing ([cd27aa7](https://github.com/DerPate/archlens/commit/cd27aa74b9e588c4728d2a4d2ca80391e5c7f306))
* **test:** repair broken checks on main ([e589e80](https://github.com/DerPate/archlens/commit/e589e80dc8be56af0e03e332bba5aa8011415393))
* **test:** repair broken self-tests, smoke tests and test isolation ([f263527](https://github.com/DerPate/archlens/commit/f263527ae56ed23254ec534f4fa8f725da7d9724))


### Documentation

* **mcp:** propose dual-era protocol support ([c414394](https://github.com/DerPate/archlens/commit/c414394e91e10d8301bc6115a2ced9831675aba9))
* **mcp:** propose dual-era protocol support ([e14d095](https://github.com/DerPate/archlens/commit/e14d09514a27280f2c9fc991811d7d33378254ff))

## [2.3.0](https://github.com/DerPate/archlens/compare/archlens-2.2.0...archlens-2.3.0) (2026-09-20)


### Features

* add cyclonedx-maven-plugin and GitHub dependency graph submission ([2d560f2](https://github.com/DerPate/archlens/commit/2d560f22d513f5c7de479e014fcaa0aaf009ed4d))
* add cyclonedx-maven-plugin and GitHub dependency graph submission ([cd1fc1a](https://github.com/DerPate/archlens/commit/cd1fc1a1a880f8208b2a68e39f7712c727ac9c50))


### Bug Fixes

* **ci:** set explicit build-mode for CodeQL Java analysis ([ff3961d](https://github.com/DerPate/archlens/commit/ff3961d58b9ccbbd295395648ba4e3dbe706180a))
* **ci:** set explicit build-mode for CodeQL Java analysis ([90ce5b8](https://github.com/DerPate/archlens/commit/90ce5b889f6c9e30439f2dd3ae7421a6c6d343f4))
* **ci:** stop OWASP scan from re-downloading the NVD DB on every push ([004abdf](https://github.com/DerPate/archlens/commit/004abdf5e80062d8b61ebdec2f55f628449b4f59))
* **ci:** stop OWASP scan from re-downloading the NVD DB on every push ([5e58e1c](https://github.com/DerPate/archlens/commit/5e58e1cad5e79727633226c94b459f69f10dc908))
* **deps:** bump devalue to patch DoS vulnerability ([bdbbad7](https://github.com/DerPate/archlens/commit/bdbbad785d6ffde5eccf1f4fe8b81ac4e12543a6))
* **deps:** bump devalue to patch DoS vulnerability ([43d4ba4](https://github.com/DerPate/archlens/commit/43d4ba48bc8cfb185ba0ec23816754c6c0af2e06))
* **test:** shut down OTel SDK in otlpModeProducesValidSpans ([fcfc032](https://github.com/DerPate/archlens/commit/fcfc032c026cfd50f396901f1dc40acf54a88b71))

## [2.2.0](https://github.com/DerPate/archlens/compare/archlens-2.1.0...archlens-2.2.0) (2026-09-17)


### Features

* check changed public API javadoc ([5b11cde](https://github.com/DerPate/archlens/commit/5b11cde68c73c337b68cb1ff412d981ba6b8ffe6))
* **site:** self-host fonts, add workflow-pack section, fix nav hash links ([a7d8be1](https://github.com/DerPate/archlens/commit/a7d8be146acc9211599ab1f8625431cbd9e20e3f))


### Bug Fixes

* address final review findings in public javadoc checker ([b74eb76](https://github.com/DerPate/archlens/commit/b74eb76bfb3fbdb965b92c72bd908f852c68f69e))
* refresh npm lockfiles to resolve dependabot alerts ([202ac5b](https://github.com/DerPate/archlens/commit/202ac5b13c33704753bf4ff41ebd6fda000da999))


### Documentation

* add javadoc to ArchitectureExtractor ([205b542](https://github.com/DerPate/archlens/commit/205b542f71a9f8061d665e9dd372ea19893e5067))
* add javadoc to ArchitectureRelevanceScorer ([40dc3bf](https://github.com/DerPate/archlens/commit/40dc3bfe718384ed218176c1eca9cccc450ea2ea))
* add javadoc to build detectors, tracing, and DataFlowSink ([16717e4](https://github.com/DerPate/archlens/commit/16717e481e9e265b1a7742611c1d9e776be2ad20))
* add javadoc to DataFlowTracer ([1d1fb72](https://github.com/DerPate/archlens/commit/1d1fb72a8ea893802ff43dcaeaf95091d7e4f8ee))
* add javadoc to ExportGraphArchitecturePocTool ([6be438c](https://github.com/DerPate/archlens/commit/6be438c7fc2b17c21283d2a89f2e836c937f67a8))
* add javadoc to GraphDataProjection ([42ac7c4](https://github.com/DerPate/archlens/commit/42ac7c4c8244ec7bf132a002197a52a503c8b678))
* add javadoc to GraphQuery public API ([9dc9611](https://github.com/DerPate/archlens/commit/9dc96117d2197f32ecaf3e15e1d456f03c3d82eb))
* add javadoc to JavaEEExtractor ([8fdf259](https://github.com/DerPate/archlens/commit/8fdf2590e8aa8f24e7552c06792a463e19f37543))
* add javadoc to LikeC4WorkspaceProjector ([9a0b03d](https://github.com/DerPate/archlens/commit/9a0b03da7a758acd2c32dd05cc13481f0c232996))
* add javadoc to McpServer ([70cc234](https://github.com/DerPate/archlens/commit/70cc234722b6133c1ec965763ae16013474ecc49))
* add javadoc to MermaidDocument ([73f638a](https://github.com/DerPate/archlens/commit/73f638adb1665ad905e13a17581995c1139284b1))
* add javadoc to MermaidFlowchartRenderer ([65d3e07](https://github.com/DerPate/archlens/commit/65d3e078a9772ec5a07668249db2136af19d0013))
* add javadoc to MermaidPipelineRenderer ([3348bf1](https://github.com/DerPate/archlens/commit/3348bf15d2dc89c614b33f137c3d7ccca8db8999))
* add javadoc to MessagingCallSiteResolver ([740c71d](https://github.com/DerPate/archlens/commit/740c71de52102baed9feb2b90d3d325b93c9dce0))
* add javadoc to ObjectFlowIndexBuilder ([15bc3f2](https://github.com/DerPate/archlens/commit/15bc3f21af1e7285a63df1eb57ac43fee1806fe9))
* add javadoc to OkfBundleWriter ([4b8c5cd](https://github.com/DerPate/archlens/commit/4b8c5cdcad646485046ce065592d7470c7e32101))
* add javadoc to QuarkusExtractor ([cae5bae](https://github.com/DerPate/archlens/commit/cae5baedd6d8f03e56538c64a9fda8fc6d0fb6fb))
* add javadoc to QuestionOkfRenderer ([2b87cf1](https://github.com/DerPate/archlens/commit/2b87cf122fc2ae09300f0f120d75fcfcd8a70f9a))
* add javadoc to remaining public API gaps ([acd9208](https://github.com/DerPate/archlens/commit/acd920860cb9e600bb21e656fe4cdbd27f4b8b07))
* add javadoc to SourceFactIndexBuilder ([3eda658](https://github.com/DerPate/archlens/commit/3eda65840aaac4dc993644c62b77d2482e59036d))
* add javadoc to ToolArgs ([f48f638](https://github.com/DerPate/archlens/commit/f48f638b5428df0ab89b2496220d881dd718a932))
* add javadoc to TraceDataFlowTool ([bb64712](https://github.com/DerPate/archlens/commit/bb6471260ae73a00e36799565f3e8f953e076cdd))
* add javadoc to TransactionPolicyExtractor ([8ff0b9d](https://github.com/DerPate/archlens/commit/8ff0b9d776cb4c4a21ad20ce35e5f5fb3d2ac291))
* add javadoc to TransactionXmlPolicyResolver ([c6ddb7c](https://github.com/DerPate/archlens/commit/c6ddb7cf68b10b8c35b7ac4e957a2e34f5f6a883))
* add javadoc to undocumented members in largest cache/extractor classes ([3f419b7](https://github.com/DerPate/archlens/commit/3f419b7a64b2f069df8fbc41a3c77d8d0335f3d7))
* add javadoc to WorkflowTraversalPolicy and ToolArgs ([0ccf9ce](https://github.com/DerPate/archlens/commit/0ccf9cefd8dba07e04438e4fd78c6325484579e9))
* add OKF architecture-knowledge bundle for ArchLens itself ([578ba09](https://github.com/DerPate/archlens/commit/578ba0943c3522372bd8f6a957095a6ae54d0b47))
* add OKF architecture-knowledge bundle for ArchLens itself ([5c9d0aa](https://github.com/DerPate/archlens/commit/5c9d0aa52cce44950e665468155bdedb37faa731))
* block-comment trivial constant groups, document SpringExtractor gaps missed in batch 1 ([f773877](https://github.com/DerPate/archlens/commit/f773877e514e1609b26120cfa4785a2c04dbf6e5))
* design Tamboui dashboard migration ([8d968de](https://github.com/DerPate/archlens/commit/8d968de7c62f493e48bbe61e06fd48543c808e22))
* fix boundaryRole javadoc accuracy ([eec51a0](https://github.com/DerPate/archlens/commit/eec51a04c182709e1c75c210e5c449c5fd03e094))
* fix CONFIGURATION/LOGGING/MAPPER javadoc accuracy ([2e00281](https://github.com/DerPate/archlens/commit/2e0028179638690507b9085be874013fce83eaad))
* fix entrypointLabel javadoc accuracy ([d64a189](https://github.com/DerPate/archlens/commit/d64a189fc7c0c5c0db3e4fc63b6e7c6b2c5a4bea))
* fix pipelineProjection javadoc accuracy ([ba6952c](https://github.com/DerPate/archlens/commit/ba6952cc820f97326caf2f54fcf21bc53d07eb7f))
* fix receiverFieldName javadoc accuracy ([8dbd10a](https://github.com/DerPate/archlens/commit/8dbd10af3d6d0faf360e4272f3330429b81e8bd6))
* fix remaining constant javadoc gaps found in final review ([bac80c1](https://github.com/DerPate/archlens/commit/bac80c1d2f03fe9e740fb5d4584504382d380970))
* make dashboard javadocs explicit in plan ([743b8c6](https://github.com/DerPate/archlens/commit/743b8c647c2c472124b8f5a02edbdb936aba7139))
* pin released Tamboui version ([9f97794](https://github.com/DerPate/archlens/commit/9f97794c31e90ace0eebbc7f6e07b6a5b5a101dc))
* plan Tamboui dashboard migration ([a6dbd33](https://github.com/DerPate/archlens/commit/a6dbd3349df0764439dcabb0f553da745c80d7d3))
* require public javadoc regression check ([8e03484](https://github.com/DerPate/archlens/commit/8e03484f920980c0e2306e022f953dae8fa17ec5))
* sweep multi-line-signature methods missed in the first documentation pass ([905218c](https://github.com/DerPate/archlens/commit/905218c83fdcd6486fbd415d781daca940fe0168))

## [2.1.0](https://github.com/DerPate/archlens/compare/archlens-2.0.0...archlens-2.1.0) (2026-09-15)


### Features

* **okf:** backfill okf_version declaration on index.md ([82244a0](https://github.com/DerPate/archlens/commit/82244a08e2b50ce2d4cd6ff303f2da621713279a))
* **okf:** upgrade generated concepts to OKF v0.2 frontmatter ([6708b4f](https://github.com/DerPate/archlens/commit/6708b4fd84bfacb6d50bb6a1c7cd9937f78103df))


### Bug Fixes

* emit valid Mermaid subgraph syntax ([628c1e5](https://github.com/DerPate/archlens/commit/628c1e547fda660416a421df2df9541d590845f1))
* rebuild renderer before generating architecture docs ([3d8525a](https://github.com/DerPate/archlens/commit/3d8525a203962f125afa9eecaab979a668e91bad))
* scope Mermaid C4-keyword escaping to avoid id/label collisions ([be6ba24](https://github.com/DerPate/archlens/commit/be6ba24d67771d6be3c720e4786db3ebef88fb81))


### Documentation

* confirm renderer comparison holds after C4-keyword scoping fix ([64152da](https://github.com/DerPate/archlens/commit/64152daf76d21c28964b169de781290268f5fa2e))
* partition oversized generated flowcharts in self-doc output ([b6c4a47](https://github.com/DerPate/archlens/commit/b6c4a4744b95be619806d5dc29e920a65aa5a2fd))

## [2.0.0](https://github.com/DerPate/archlens/compare/archlens-1.5.0...archlens-2.0.0) (2026-09-14)


### ⚠ BREAKING CHANGES

* **renderer:** render_use_case_timeline now returns a Mermaid flowchart, not a gantt chart.
* **tracing:** -Dspoon.traces and -Dspoon.otlp.endpoint are no longer read; use -Darchlens.traces and -Darchlens.otlp.endpoint.
* **skill:** the skill is now invoked as archlens-understand; any saved prompt or config referencing spoon-understand must be updated.
* **renderer:** drop legend subgraph from diagrams

### Features

* attach direct EntityManager invocation sinks to persistence data flows ([b7307e9](https://github.com/DerPate/archlens/commit/b7307e9f962e39a4116ea76238bcc1eb2d6397b1))
* **extractor:** widen plain-Java service-name classification ([b061b28](https://github.com/DerPate/archlens/commit/b061b28c9bb2c1962796c8ee5d360af36dd529e9))
* **mcp:** ARCHLENS_MCP_EXPERIMENTAL_C4 opt-in and renderer docs ([722fddc](https://github.com/DerPate/archlens/commit/722fddcd166758f9021b654abbe91a23821cca48))
* **renderer:** add shared MermaidStyle theme, role palette, and legend ([23c96a7](https://github.com/DerPate/archlens/commit/23c96a78c266e003703d2ff1d799de799697fa3f))
* **renderer:** drop legend subgraph from diagrams ([aac05b7](https://github.com/DerPate/archlens/commit/aac05b7c78269d8a399e260fe023cc4061587562))
* **renderer:** opt-in C4 dialect for system and container levels ([c306101](https://github.com/DerPate/archlens/commit/c30610136ac6f99b9fa7292cc14138d2e3e2ede3))
* **renderer:** pipeline diagrams use shared role palette ([69fe932](https://github.com/DerPate/archlens/commit/69fe9320b0d94270b40be1c0e03f47f69160be61))
* **renderer:** render call flows as sequence diagrams ([5aa22e1](https://github.com/DerPate/archlens/commit/5aa22e18bef67315611d84a077839bdc0bfdc4cb))
* **renderer:** render the use-case timeline as a flowchart chain ([0a1f87a](https://github.com/DerPate/archlens/commit/0a1f87aaaadef0483afbb7660dd84d5a6f7110c5))
* **renderer:** shared role styling for dependency map and slice diagrams ([aa45531](https://github.com/DerPate/archlens/commit/aa45531dac5d896487a64396126d12680a56c035))
* **renderer:** shared styling for source overview, architecture view, timeline ([49289f5](https://github.com/DerPate/archlens/commit/49289f5de04de2a46c2d40215f80687ec6d1a1dd))
* **renderer:** show entities only where they cross a boundary ([791eb16](https://github.com/DerPate/archlens/commit/791eb166268678edc8e1b22319aef2a1d74fd174))
* **renderer:** themed roles, dashed async edges, and legends in flowchart levels ([a655c8e](https://github.com/DerPate/archlens/commit/a655c8e4625be1130818478ea6368ece7c84b46a))


### Bug Fixes

* **extractor:** detect entities structurally and stop attributing container calls to elements ([94f217a](https://github.com/DerPate/archlens/commit/94f217a7704997e74411bf1b6dea703e8f77ac61))
* **extractor:** give every entrypoint its own runtime flow ([f353638](https://github.com/DerPate/archlens/commit/f35363860652a6c9bd1945318ecade1d96752527))
* **extractor:** give every entrypoint its own runtime flow ([f26c7ac](https://github.com/DerPate/archlens/commit/f26c7accd9b9ba23e810f6af0a95cac52336e2ac))
* **extractor:** guard source positions that have no file ([806448e](https://github.com/DerPate/archlens/commit/806448e7fa2f9c9d410b0c0cd882ff43db605ac6))
* **extractor:** stop swallowing named path/value fallback on mapping annotations ([b49d7e1](https://github.com/DerPate/archlens/commit/b49d7e1c6f2fa6dee30bf3d531b6f0b5b6141bcb))
* **extractor:** stop swallowing named path/value fallback on mapping annotations ([e78cfa0](https://github.com/DerPate/archlens/commit/e78cfa07f0a78319c5ecb8c4a252ad23e1f1be91))
* **mcp:** make unfiltered use-case timeline readable and deterministic ([2cf25a0](https://github.com/DerPate/archlens/commit/2cf25a0d1cda973f7c4c0c519683b70c2028ed4a))
* **mcp:** unbreak detect_use_cases and suppress remaining Optional/Stream calls ([a78ff7f](https://github.com/DerPate/archlens/commit/a78ff7f3ad451a96c041d57b04e5c63acb58cb85))
* **renderer:** deterministic call-edge order for duplicate component pairs ([63463f2](https://github.com/DerPate/archlens/commit/63463f22fb37a9a93557ad4d29676bbf17895efa))
* **renderer:** emit valid gantt spans and raise condition cap to 255 ([1a87e8c](https://github.com/DerPate/archlens/commit/1a87e8cafff002684ee5468997c7a64ad952046c))
* **renderer:** guard null kind in architecture view label; correct pipeline shape doc ([ed5622c](https://github.com/DerPate/archlens/commit/ed5622cdfedcc3945c9651e8b35241ff9c296467))
* **renderer:** un-hollow pipeline dedup assertion, refresh shape javadoc ([79b688f](https://github.com/DerPate/archlens/commit/79b688f2e86f2d8e28e5f0193dc24aa705c4ede9))


### Documentation

* **skill:** restore the tool map and record the diagram conventions ([7bb7f16](https://github.com/DerPate/archlens/commit/7bb7f16b100d2965e563f4c57b32b32205bdc787))
* **tools:** correct the use-case timeline sample and its defaults ([0966a1c](https://github.com/DerPate/archlens/commit/0966a1cb572fd54209f9274d2b2cd78c4a5c9015))
* **workflow-graphs:** regenerate against the current tree ([73a5e2c](https://github.com/DerPate/archlens/commit/73a5e2c45ab464f588ffb06a72a976390436ffdd))


### Code Refactoring

* **skill:** rename spoon-understand to archlens-understand ([d44ac89](https://github.com/DerPate/archlens/commit/d44ac8946502b0cae12bb4413a6e1deda7d38e60))
* **tracing:** rename spoon.traces to archlens.traces ([ad29589](https://github.com/DerPate/archlens/commit/ad295899cf2da35225e383e6549e5721fdc0d654))

## [1.5.0](https://github.com/DerPate/archlens/compare/archlens-1.4.2...archlens-1.5.0) (2026-07-20)


### Features

* add canonical architecture question requests ([545a280](https://github.com/DerPate/archlens/commit/545a2806c85c462fbb328c56724fd2daa2a1724a))
* confine OKF output paths to indexed projects ([1718d56](https://github.com/DerPate/archlens/commit/1718d56078f7bc45971b2e93e580954b1a7f6943))
* expose architecture question OKF compiler ([2400840](https://github.com/DerPate/archlens/commit/2400840c69ab90f397cbc602105aae1a7dcacb07))
* render and validate question OKF concepts ([9c53348](https://github.com/DerPate/archlens/commit/9c533487fdccd1b828bf293f0a4854a998c65a7f))
* safely create and refresh OKF bundles ([03e7e4b](https://github.com/DerPate/archlens/commit/03e7e4be3df10b9879f28475bd598592feb89bf4))
* validate and identify OKF investigations ([9ca1e7c](https://github.com/DerPate/archlens/commit/9ca1e7c3143269a139f68123844d76691e2314c7))


### Bug Fixes

* preserve null values in question OKF compilation ([6bb1689](https://github.com/DerPate/archlens/commit/6bb168999402b470ce91d7a2025e5c237b27410b))


### Documentation

* document architecture question OKF compilation ([3912ced](https://github.com/DerPate/archlens/commit/3912ceda17c38773936cea69f48d500843257adc))

## [archlens-1.4.2]

## Changelog

### Features
- expose architecture question OKF compiler
- safely create and refresh OKF bundles
- render and validate question OKF concepts
- confine OKF output paths to indexed projects
- validate and identify OKF investigations
- add canonical architecture question requests
- expose the 6 new intents in the MCP tool schema
- wire messaging/state/scheduled/integration/config/relationship intents into the dispatcher and planner
- add relationship intent
- add configuration_context intent
- add external_integration_context intent
- add scheduled_workflow intent
- add state_lifecycle intent
- add messaging_flow intent
- capture scheduler trigger metadata (cron/fixedRate/fixedDelay/every)
- project ConfigProperty nodes and CONFIGURED_BY edges
- add ConfigPropertyResolver and shared PropertyFileReader
- implement M0-M4 evidence contract, persistence/transaction extraction, and benchmark harness
- expose question input and endpoint_context in MCP tool schema
- add endpoint_context intent, forward mode
- route natural-language question input through QuestionPlanner
- add deterministic keyword-scoring question planner
- trace Kafka sends from non-entrypoint producers
- wire MessagingTopicResolver into ArchitectureExtractor pass1Scan
- MessagingTopicResolver driver — expand unresolved OutboundSinkSites to one site per literal
- StringExpressionResolver MESSAGE_OBJECT — scan setHeader(KafkaHeaders.TOPIC, ...) in callers
- StringExpressionResolver METHOD_CALL — walk interface implementations
- StringExpressionResolver PARAM_REF — walk callers to find passed literals
- StringExpressionResolver base cases — literal, static field, local var
- classify Kafka topic arg kind in SpringExtractor instead of toString()
- add TopicArgKind enum and OutboundSinkSite.withTopic() clone helper
- add stable and draft structured output modes
- add structuredContent to tool responses (MCP SDK 2.0)
- add human-readable title to all tool registrations

### Fixes
- preserve null values in question OKF compilation
- defensively copy Interpretation's list/map fields
- scope persistence_destination's PersistenceOperation loop to component/query
- guard against duplicate self-invocation limitation notes
- match ejb-jar.xml ejb-name against declared @Stateless/@Singleton name
- match CONFIGURED_BY against exact REST-client config-key conventions
- add word boundaries to QuestionPlanner's single-token triggers
- unify secret-key filtering into one shared 8-marker list
- restore config-file precedence, fix YAML null handling, read once
- keep dead wrapper-overload sink sites unresolved instead of falling back to cross-overload caller walk
- resolve Kafka wrapper topics per caller call site instead of unioning across callers
- suppress duplicate DataFlowSink records from call-graph diamond revisits
- raise findEdges limit cap from 100 to 50,000 to match findNodes
- add limitation comments and remove redundant import (final review feedback)
- set topicPropertyKey in CtLocalVariable literal branch of classifyTopicArg
- validate graph structured output by action

### Documentation
- document architecture question OKF compilation
- tighten OKF compiler design details
- design architecture question OKF compiler
- document messaging/state/scheduler/integration/config/relationship intents
- M0-M4 website and installation documentation updates
- document question input and endpoint_context contract
- record findEdges limit cap fix under Unreleased
- refresh CONTRIBUTING.md and add 'document as you go' convention
- comprehensive Javadoc across public API + housekeeping
- explain graph structured output shapes
- design graph query structured output fix
- design MCP draft structured output compatibility

### Maintenance
- release 1.4.2
- update SpotBugs and harden OKF records
- consolidate 5 duplicated data-flow-path-by-entrypoint lookups
- add final M5 Phoenix acceptance script covering all six new intents
- add benchmark scenarios for M5.3-M5.5's six new intents
- add Phoenix acceptance script for endpoint_context
- add M5.0 endpoint_context benchmark scenario
- verify NL/typed equivalence for endpoint_context, forward and reverse
- cover endpoint_context reverse mode
- cover QueryPlanRecorder ordering and immutability
- extract answer_architecture_question intents into mcp/tools/question package
- exclude EI_EXPOSE_REP for OutboundSinkIndex.all (List.copyOf, immutable)
- exclude PA_PUBLIC_PRIMITIVE_ATTRIBUTE for OutboundSinkSite
- apply spotless formatting to topic-resolution changes
- add cross-project workflow-link probe script
- bump the routine-dependencies group across 1 directory with 8 updates
- add reproducer for METHOD_CALL implementation walk
- add reproducer for PARAM_REF caller walk (resolvesParamRefByWalkingCallers)
- assert all OutboundSinkSite fields are cloned by withTopic()
- add kafka-topic-resolver-sample test project covering all 3 unresolved-topic patterns
- reproduce graph structured output validation
- bump version to 1.4.1


## Contributors
We'd like to thank the following people for their contributions:
Dominik Breu, GitHub, dependabot[bot]


## [Unreleased]

### Fixes

- **`find_edges` no longer silently capped at 100 results:** `GraphQuery.normalizeLimit` clamped
  every edge query to 100, so MCP `query_architecture_graph` `find_edges` (default limit 256),
  `dependencyEdges` (100,000), `get_component_dependencies` and `infer_containers` (10,000) all
  returned at most 100 edges with no truncation indicator. `findEdges` now uses a dedicated
  `normalizeFindEdgesLimit` with the same 50,000 cap as `findNodes`, keeping the historical
  default of 25 for non-positive limits. Traversal queries (`neighborhood`, `paths`,
  `impacted_by`, `reachable`) intentionally keep the small cap and are unchanged.

## [spoon-mcp-server-1.3.0]

## Changelog

### Features
- add API docs landing page via USE_MDFILE_AS_MAINPAGE
- add doxygen-awesome extensions per official customization guide
- set nav-logo-dark.svg as Doxygen project logo
- add Doxygen dark theme config and API doc generation (tasks 9-10)
- add four tool deep-dive pages (tasks 5-8)
- add tool card grid and API teaser section
- add quick-start section
- add CSS utility classes for docs sections
- add Tools and API nav links
- switch to standalone dashboard when launched in an interactive terminal
- support light and dark nav logos
- add JLine-based standalone dashboard terminal
- support light and dark favicons
- add DashboardState and pure-function two-pane renderer
- add ReplEngine dispatching REPL commands against the MCP tool registry
- add REPL command line parser
- capture real Gremlin traversal text in GraphQuery's traversal-building methods
- add TraversalRecorder for capturing real Gremlin traversal text
- add ArchLens project website
- add ArchLens site layout
- use condition expression text as branch edge label instead of 'then'/'else'
- render conditional topology edges as dashed Mermaid arrows
- complete graph rework — zero cache.load() in tools
- route tool resolution and traversal through graph
- register graph viewer export tool
- add graph viewer export tool
- render self-contained graph viewer html
- expose architecture graph snapshot
- dynamic views for message broker flows
- resolve interface-typed injection fields and force injection targets into LikeC4 view
- typed-ID graph API and LikeC4 workspace projector rewrite
- introduce AppId across the module/app namespace
- introduce GraphNodeId; type ArchitectureGraph vertex/edge ids
- introduce SourceFactId; type source-fact id/typeId/methodId/ownerId
- introduce FieldAccessId; migrate FieldAccess.id from String
- introduce UseCaseId; migrate UseCase.id from String
- introduce DependencyId record; add EntrypointId.of() factory
- migrate Dependency.fromId/toId and FlowEdge.fromId/toId to ComponentId
- introduce typed ComponentId, MethodRef, EntrypointId, FieldRef, FieldBinding records
- generate likec4 starter workspace

### Fixes
- add default case to nodeKind switch to satisfy SpotBugs SW_SWITCH_NO_DEFAULT
- replace DataFlowNodeNode::id method reference with explicit lambda to resolve IDE inspection
- replace unchecked String casts with instanceof pattern matching in MermaidPipelineRenderer
- let doxygen-awesome handle layout, only override brand colors
- update Doxyfile and header template for Doxygen 1.17.0
- force dark theme over doxygen hardcoded backgrounds
- fix doxygen-awesome-css layout to match showcase
- correct color tokens and Doxyfile Java optimization setting
- resolve spotless/javadoc/spotbugs gate failures on plan-touched files
- restore favicon media mapping order
- swap favicon color-scheme mapping
- connect ArchLens hero graph preview
- parenthesize negated condition in else-branch labels
- quote edge labels in Mermaid pipe syntax to handle == ! () in conditions
- omit empty label pipes on unlabeled topology edges
- serialize typed IDs to string when storing as graph vertex properties
- drop ReDoS-flagged trim regex in LikeC4 identifier (S5852)
- resolve sonar bugs, add jacoco coverage, modernize tests
- serialize() ComponentId in call/outbound edge ids instead of toString()
- normalize legacy-prefixed node ids in query_architecture_graph
- normalize prefixes in ComponentId.of; drop comp: from objectflow targets
- accept legacy ep-prefixed entrypoint refs in RuntimeFlowInferrer
- accept legacy comp-prefixed component refs in resolver tools
- raise render_architecture_view default maxNodes from 18 to 500
- suppress shadowed cross-component store writes in pipeline linker
- compare EntrypointId by value in DetectUseCasesTool filter
- exclude AvoidInlineConditionals from Cleanthat
- stitch scheduler→Emitter.send()→consumer leg when sink broker is null (#16)
- emit STORE sink when put() value is a method invocation or param is the store key (#15)
- stitch consumer→store→scheduler pipelines when write is via helper method (#14)

### Documentation
- rename project website spec to ArchLens
- add Spoon MCP website design spec
- remove [Unreleased] section from CHANGELOG — JReleaser owns it
- align all documents with v2/v3 graph rework
- record JDK 25 + SonarQube cleanup under Unreleased
- sync ARCHITECTURE dependency ids; add Identity model to README
- sync tool id examples to unprefixed typed-id form
- add typed ID model implementation plan

### Maintenance
- add site assets, dev scripts, and gitignore rules for generated/tool artifacts
- remove nav logo frame
- replace hero eyebrow with wordmark
- add icons to footer links
- remove hero compass backdrop
- remove terminal preview watermark
- add compass watermark to terminal preview
- make ArchLens marks swappable
- lean into ArchLens compass imagery
- tighten ArchLens hero spacing
- replace ArchLens nav logo
- use ArchLens monogram favicon
- simplify ArchLens favicon
- soften ArchLens orange accent
- apply ArchLens brand system
- add ArchLens site smoke tests
- scaffold ArchLens static site
- add failing test for conditional topology edge rendering
- replace reachableFromEntrypoints BFS with Gremlin aggregate
- push filters into Gremlin predicates, lowercase enum storage
- extract sort constants, remove incidental ordering, Gremlin iteration
- replace manual BFS with Gremlin traversal DSL
- replace Map<String,Object> GraphNode with sealed typed records
- format graph viewer changes
- extract Spans.traced() helper; flatten nested span try-blocks
- use var for unnamed try-resource variables (S7466)
- unnamed variables for unused try-resources and catch bindings
- JDK 25 + unnamed variables for ignored exceptions
- method reference StringUtils::isNotBlank in maven packaging filter (S1612)
- adopt Java 21 sequenced-collection + emptiness idioms
- centralize Mermaid label escaping into one helper
- adopt commons-lang3 StringUtils for null-safe string checks
- adopt Math.clamp and record patterns (Java 21, S6885/S6878)
- type DependencyCondenser adjacency by ComponentId
- type component/entrypoint/dependency maps in extractor + tool layer
- type entrypoint maps by EntrypointId in workflow layer
- type component maps by ComponentId in renderers
- single continue in dependency-walk loop (S135)
- clear new-code Sonar violations from the complexity work
- apply spotless formatting to refactored files
- split three over-threshold extracted helpers (S3776 cleanup)
- reduce complexity in merger subsystem (S3776)
- reduce complexity in rendering/reporting subsystem (S3776)
- reduce complexity in extraction subsystem (S3776)
- reduce complexity in graph projection subsystem (S3776)
- simplify PipelineGraphBuilder.incomingSink via kind mapping (S3776)
- reduce complexity in workflow/pipeline subsystem (S3776)
- reduce complexity in call-graph subsystem (S3776)
- finish dataflow subsystem complexity (S3776)
- reduce complexity in dataflow subsystem (S3776)
- extract remaining duplicated literals (S1192 follow-up)
- extract duplicated string literals into constants (S1192)
- cover MCP tool layer and McpServer wiring
- standardize on Stream.toList() and drop redundant @SuppressWarnings
- drop legacy comp:/ep:/dep: prefix back-compat
- verify ModelCache store/load preserves typed ids
- lock typed-id serialization and legacy round-trip contract
- add Cleanthat SafeButNotConsensual step to Spotless (v2.25)
- typed DataFlowTracer visited set + Dependency.idFor factory
- satisfy spotless and spotbugs gates


## Contributors
We'd like to thank the following people for their contributions:
Dominik Breu


## [spoon-mcp-server-1.2.0]

## Changelog

### Features
- add httpMethod and path filters to find_entrypoints
- HTTP-method disambiguation via 'METHOD /path' ref syntax
- seed call graph extraction from source facts
- seed object flow from source facts
- build source facts during extraction
- trace source fact indexing phases
- index source invocation assignment return and injection facts
- index source inheritance implementations
- build source type member and annotation facts
- add immutable source fact index
- add source fact model skeleton
- add ModelIndex and typed adjacency/index types
- add ComponentIndex and ExtractionContext for typed extraction state
- add resolvedLiteralArgs to CallEdge, totalLinks to WorkflowGraph, and expand cache/objectflow tests
- add OTel spans to callgraph dataflow spring and objectflow extractors
- instrument PipelineGraphBuilder with OTel spans
- instrument ArchitectureExtractor with OTel spans
- add TracingConfig and wire OTel into server startup
- add StdoutSpanExporter for console tracing output
- explain missing pipeline links
- render and expose persistence workflow links
- link persistence workflow handoffs
- link messaging data-flow paths by broker topic
- trace outbound sinks through nested calls
- extract spring kafka outbound sink sites
- add pipeline handoff metadata to data-flow sinks
- retain spring config placeholder provenance
- consume build metadata and spring
- extract outbound interfaces
- extract inbound triggers
- extract components and rest endpoints
- resolve bounded application config
- scan normalized build modules
- add metadata service and unknown fallback detector
- detect gradle project metadata
- detect maven project metadata
- add build metadata model
- add workflow and object-flow analysis
- make architecture view edges visible for all codebases
- export architecture projections as likec4
- expose architecture view rendering tool
- render architecture projections as mermaid
- project architecture graph into component views
- add architecture view projection model
- migrate to official MCP Java SDK and add ToolArgs helper
- add render_pipeline tool and PipelineChain graph projection (#10)
- add ownedEntrypointCount and architecturalWeight to component nodes; fix jar naming
- emit cross-component FieldAccess READ for getter-style shared-state returns
- vert.x eventbus / websocket / sse / grpc + file & object-storage sinks
- tier 2 — nested-arg paramMapping, return-flow, killed locals
- tier 1 — producer field-seed, sourceFieldName, logger denylist
- infer in-memory channels, resolve topics, link cross-entrypoint stores
- detect plain-Java main(String[]) methods as MAIN_METHOD entrypoints
- use-case timeline renderer and doc cleanup
- call graph extraction, use-case detection, and data-flow tracing

### Fixes
- prevent false call edges from generic Java API method names; add entrypoint filters and disambiguation
- prefer exact path match in findEntrypoint; block prefix match for parameterised refs
- skip capped polymorphic expansion edges in workflow traversal
- path-prefix matching in trace_data_flow, timeline, and pipeline tools
- delegate findStoredFlow to inferrer.findEntrypoint in flow tools
- use path-prefix matching in RuntimeFlowInferrer.findEntrypoint
- resolve constructor injected service calls
- collapse tech detection to one annotation pass, run only matching extractor for unknown tech
- stabilize model cache and graph semantics
- load detect-use-cases config once instead of twice
- stabilize mcp tool layer
- eliminate lifecycle chains and same-entrypoint STORE loops
- exclude lifecycle CDI observer entrypoints from pipeline chains
- stop collectReachableRead* from crossing messaging/event-bus edges
- skip steps[0] in segment loop to eliminate duplicate header node
- guard null CallEdge fields in propagateStateHandoffThroughCallers
- propagate STATE_HANDOFF through callers when writer and reader share a component
- block same-entrypoint store-sink self-stitching in DataFlowTracer
- include zero-sink MESSAGING_CONSUMER paths in DataFlowTracer result
- deduplicate pipeline chains with identical entrypoint-ID sequences
- suppress early-terminating prefix chains from PipelineGraphBuilder
- selectDiverse caps at one chain per root, keeping the longest
- filter CDI/main/RMI lifecycle chains from render_pipeline by default
- improve architecture_view prompt — add index step and warnings guidance
- diversity-first chain selection in render_pipeline (#11)
- propagate calleeQualifiedName from OutboundSinkSite to DataFlowSink
- remove DataFlowPath.paramType — field was never assigned
- surface Emitter/EventBus send calls as messaging sinks and link downstream consumers (#7)
- emit store sinks at depth 0 when param is destructured before storing
- wire jreleaser.dry.run property through Maven plugin configuration
- set GitHub remote in release checkout before JReleaser runs
- render branching call flows and trace zero-param entrypoints to sinks
- improve container-level flowchart and dependency map diagrams

### Documentation
- describe source fact extractor foundation
- plan source fact index foundation
- design source fact index foundation
- close whole-repo stabilization audit
- otel tracing implementation plan
- otel tracing design spec
- renderer docs and test audit — no findings
- workflow and pipeline audit — no findings
- extractor audit — no findings
- correct F-001 status — false finding
- record stabilization baseline
- start whole-repo stabilization audit
- design whole-repo stabilization pass
- document pipeline workflow links
- document spring and gradle extraction
- design spring gradle extraction
- teach agents architecture projection workflow
- fix all 44 missing Javadoc warnings
- add Known Limitations section for G7 and G9 to ARCHITECTURE.md
- document render_pipeline empty-linkedPathIds behaviour (D3)
- fix DataFlowPath/Sink property tables — remove paramType, add calleeQualifiedName/calleeMethod
- write 1.0.2 changelog and fix jreleaser tag wiring
- update tool reference, architecture guide, and llms.txt for call graph, use cases, and data-flow tracing

### Maintenance
- cover multi-segment parameterised paths in pathPrefixMatches and findEntrypoint
- expose object-flow tracing details
- reuse CtModel across extraction passes
- add CtModel build tracing spans
- extraction pipeline redesign complete, all tests pass
- two-phase extraction pipeline with single CtModel in memory
- RuntimeFlowInferrer accepts ModelIndex, eliminates per-entrypoint map rebuilds
- redesign DataFlowTracer DFS with ModelIndex
- wire ExtractionContext into CallGraphExtractor, true one-pass MethodScan, remove byComponentId
- add error recording to child spans in ArchitectureExtractor
- remove unreachable default branch in TracingConfig switch
- add opentelemetry-sdk and otlp exporter dependencies
- reduce AST traversals per method from ~8 to 1 in CallGraphExtractor
- cover spring pipeline rendering end to end
- add spring pipeline fixture
- cover runtime flow chain
- guard against vacuous pass in storeSinkDoesNotLink test
- apply spotless import ordering
- pre-compute path IDs in removePrefixChains, document algorithm
- remove unused mockito-core dependency
- fix stale docs, gitignore generated files, commit pending graph improvements
- add architecture view self-test
- apply Spotless formatting across all sources
- bump version to 1.1.0
- assert calleeMethod value on FILE_OUTBOUND sink (G6)
- add Form 2 handler-chain fixture and test for Vert.x consumer detection
- add direct EventBusExtractor unit test for Vert.x consumer detection (G4)
- tighten WebSocket entrypoint assertion — add path check, use endsWith
- add WebSocket endpoint detection test and ChatResource fixture (G5)
- add log read site so denylist test is a genuine regression guard
- verify Logger fields are excluded from shared-state seeding (G10)
- replace DataFlowSink.kind String with Kind enum
- add OWASP dependency-check, fix release pipeline, and update Java version docs
- replace sequence diagrams with flowchart-based call flow renderer


## Contributors
We'd like to thank the following people for their contributions:
Dominik Breu


## [spoon-mcp-server-1.2.0]

## Changelog

### Features
- add httpMethod and path filters to find_entrypoints
- HTTP-method disambiguation via 'METHOD /path' ref syntax
- seed call graph extraction from source facts
- seed object flow from source facts
- build source facts during extraction
- trace source fact indexing phases
- index source invocation assignment return and injection facts
- index source inheritance implementations
- build source type member and annotation facts
- add immutable source fact index
- add source fact model skeleton
- add ModelIndex and typed adjacency/index types
- add ComponentIndex and ExtractionContext for typed extraction state
- add resolvedLiteralArgs to CallEdge, totalLinks to WorkflowGraph, and expand cache/objectflow tests
- add OTel spans to callgraph dataflow spring and objectflow extractors
- instrument PipelineGraphBuilder with OTel spans
- instrument ArchitectureExtractor with OTel spans
- add TracingConfig and wire OTel into server startup
- add StdoutSpanExporter for console tracing output
- explain missing pipeline links
- render and expose persistence workflow links
- link persistence workflow handoffs
- link messaging data-flow paths by broker topic
- trace outbound sinks through nested calls
- extract spring kafka outbound sink sites
- add pipeline handoff metadata to data-flow sinks
- retain spring config placeholder provenance
- consume build metadata and spring
- extract outbound interfaces
- extract inbound triggers
- extract components and rest endpoints
- resolve bounded application config
- scan normalized build modules
- add metadata service and unknown fallback detector
- detect gradle project metadata
- detect maven project metadata
- add build metadata model
- add workflow and object-flow analysis
- make architecture view edges visible for all codebases
- export architecture projections as likec4
- expose architecture view rendering tool
- render architecture projections as mermaid
- project architecture graph into component views
- add architecture view projection model
- migrate to official MCP Java SDK and add ToolArgs helper
- add render_pipeline tool and PipelineChain graph projection (#10)
- add ownedEntrypointCount and architecturalWeight to component nodes; fix jar naming
- emit cross-component FieldAccess READ for getter-style shared-state returns
- vert.x eventbus / websocket / sse / grpc + file & object-storage sinks
- tier 2 — nested-arg paramMapping, return-flow, killed locals
- tier 1 — producer field-seed, sourceFieldName, logger denylist
- infer in-memory channels, resolve topics, link cross-entrypoint stores
- detect plain-Java main(String[]) methods as MAIN_METHOD entrypoints
- use-case timeline renderer and doc cleanup
- call graph extraction, use-case detection, and data-flow tracing

### Fixes
- prevent false call edges from generic Java API method names; add entrypoint filters and disambiguation
- prefer exact path match in findEntrypoint; block prefix match for parameterised refs
- skip capped polymorphic expansion edges in workflow traversal
- path-prefix matching in trace_data_flow, timeline, and pipeline tools
- delegate findStoredFlow to inferrer.findEntrypoint in flow tools
- use path-prefix matching in RuntimeFlowInferrer.findEntrypoint
- resolve constructor injected service calls
- collapse tech detection to one annotation pass, run only matching extractor for unknown tech
- stabilize model cache and graph semantics
- load detect-use-cases config once instead of twice
- stabilize mcp tool layer
- eliminate lifecycle chains and same-entrypoint STORE loops
- exclude lifecycle CDI observer entrypoints from pipeline chains
- stop collectReachableRead* from crossing messaging/event-bus edges
- skip steps[0] in segment loop to eliminate duplicate header node
- guard null CallEdge fields in propagateStateHandoffThroughCallers
- propagate STATE_HANDOFF through callers when writer and reader share a component
- block same-entrypoint store-sink self-stitching in DataFlowTracer
- include zero-sink MESSAGING_CONSUMER paths in DataFlowTracer result
- deduplicate pipeline chains with identical entrypoint-ID sequences
- suppress early-terminating prefix chains from PipelineGraphBuilder
- selectDiverse caps at one chain per root, keeping the longest
- filter CDI/main/RMI lifecycle chains from render_pipeline by default
- improve architecture_view prompt — add index step and warnings guidance
- diversity-first chain selection in render_pipeline (#11)
- propagate calleeQualifiedName from OutboundSinkSite to DataFlowSink
- remove DataFlowPath.paramType — field was never assigned
- surface Emitter/EventBus send calls as messaging sinks and link downstream consumers (#7)
- emit store sinks at depth 0 when param is destructured before storing
- wire jreleaser.dry.run property through Maven plugin configuration
- set GitHub remote in release checkout before JReleaser runs
- render branching call flows and trace zero-param entrypoints to sinks
- improve container-level flowchart and dependency map diagrams

### Documentation
- describe source fact extractor foundation
- plan source fact index foundation
- design source fact index foundation
- close whole-repo stabilization audit
- otel tracing implementation plan
- otel tracing design spec
- renderer docs and test audit — no findings
- workflow and pipeline audit — no findings
- extractor audit — no findings
- correct F-001 status — false finding
- record stabilization baseline
- start whole-repo stabilization audit
- design whole-repo stabilization pass
- document pipeline workflow links
- document spring and gradle extraction
- design spring gradle extraction
- teach agents architecture projection workflow
- fix all 44 missing Javadoc warnings
- add Known Limitations section for G7 and G9 to ARCHITECTURE.md
- document render_pipeline empty-linkedPathIds behaviour (D3)
- fix DataFlowPath/Sink property tables — remove paramType, add calleeQualifiedName/calleeMethod
- write 1.0.2 changelog and fix jreleaser tag wiring
- update tool reference, architecture guide, and llms.txt for call graph, use cases, and data-flow tracing

### Maintenance
- cover multi-segment parameterised paths in pathPrefixMatches and findEntrypoint
- expose object-flow tracing details
- reuse CtModel across extraction passes
- add CtModel build tracing spans
- extraction pipeline redesign complete, all tests pass
- two-phase extraction pipeline with single CtModel in memory
- RuntimeFlowInferrer accepts ModelIndex, eliminates per-entrypoint map rebuilds
- redesign DataFlowTracer DFS with ModelIndex
- wire ExtractionContext into CallGraphExtractor, true one-pass MethodScan, remove byComponentId
- add error recording to child spans in ArchitectureExtractor
- remove unreachable default branch in TracingConfig switch
- add opentelemetry-sdk and otlp exporter dependencies
- cover spring pipeline rendering end to end
- add spring pipeline fixture
- cover runtime flow chain
- guard against vacuous pass in storeSinkDoesNotLink test
- pre-compute path IDs in removePrefixChains, document algorithm
- remove unused mockito-core dependency
- fix stale docs, gitignore generated files, commit pending graph improvements
- add architecture view self-test
- apply Spotless formatting across all sources
- bump version to 1.1.0
- assert calleeMethod value on FILE_OUTBOUND sink (G6)
- add Form 2 handler-chain fixture and test for Vert.x consumer detection
- add direct EventBusExtractor unit test for Vert.x consumer detection (G4)
- tighten WebSocket entrypoint assertion — add path check, use endsWith
- add WebSocket endpoint detection test and ChatResource fixture (G5)
- add log read site so denylist test is a genuine regression guard
- verify Logger fields are excluded from shared-state seeding (G10)
- replace DataFlowSink.kind String with Kind enum
- add OWASP dependency-check, fix release pipeline, and update Java version docs
- replace sequence diagrams with flowchart-based call flow renderer
- reduce AST traversals per method from ~8 to 1 in CallGraphExtractor
- apply spotless import ordering

## Contributors
We'd like to thank the following people for their contributions:
Dominik Breu

## [Unreleased]

### 🚀 Features

- **`find_entrypoints` — REST discovery filters:** new `httpMethod` and `path` parameters.
  `httpMethod` filters by HTTP verb (case-insensitive: `GET`, `POST`, `PUT`, `DELETE`, …).
  `path` filters by path prefix in discovery mode — `/customer` returns `/customer`,
  `/customer/{id}`, and all sub-resources; `/customer/{id}` returns sub-resources but not
  the bare collection endpoint. Both filters are combinable with each other and with `appId`
  and `type`.
- **HTTP-verb disambiguation syntax for all flow tools:** every `entrypointName` parameter
  in `get_runtime_flow`, `render_call_flow`, `render_use_case_timeline`, `trace_data_flow`,
  and `render_pipeline` now accepts a `"METHOD /path"` prefix (e.g. `"GET /account"`,
  `"POST /account"`). When the verb is present, only entrypoints whose `httpMethod` matches
  are considered, resolving GET/POST conflicts at the same path.
- **Exact-path priority in entrypoint lookup:** `findEntrypoint` now performs a two-pass
  scan — exact `ep.path` match is returned immediately; a prefix-match candidate is kept as
  fallback only when no exact match exists. Eliminates wrong-endpoint selection when a
  longer path such as `/customer/{id}/contactPerson` happened to prefix-match before the
  exact `/customer` endpoint in iteration order.

### 🐞 Fixes

- **Path-prefix over-matching guard:** `pathPrefixMatches` now returns exact-only when the
  ref contains `{`. Prevents `/absence/{id}` from matching `/absence/{id}/cancel` and
  similar parameterised sub-path collisions.
- Two semantically distinct matchers are now explicit: `pathPrefixMatches` (strict,
  single-lookup) and `pathPrefixMatchesForDiscovery` (permissive, list/filter) so that
  discovery can expand parameterised prefixes while single-lookup stays safe.

### 🐞 Fixes (continued)

- **Spurious call edges via generic Java API method names (Lombok-blindness false positives):**
  `resolveAccessorTarget` in `ObjectFlowIndexBuilder` was matching accessor names like `get`,
  `stream`, `findFirst`, and other common `java.util.Optional` / `Stream` / `Collection`
  method names against any project component that happened to have a same-named method —
  producing incorrect `ACCESSOR_RETURN` edges. Added `GENERIC_JAVA_API_METHODS` denylist;
  these names are now skipped so common Java API call chains no longer pollute the call graph.

- **Garbled paths when annotation array value starts with a path variable:** `stripArray`
  in `SpringExtractor` was stripping the outer `{}` from any string that started with `{`
  and ended with `}` — including path values like `{id}/contactPerson/{personId}` (where `{`
  is a path variable, not a Java array literal). The fix checks whether the inner content
  starts with `"` before stripping; path templates are left unchanged.
  Affected: `CustomerController.getContactPerson` and `deleteContactPerson` in phoenix_backend,
  both using `@GetMapping(value = {"{id}/..."})` without a leading `/`.

### 📚 Docs / Tooling

- Updated `docs/TOOLS.md`: `find_entrypoints` section extended with `httpMethod` and `path`
  parameters and six new examples; all `entrypointName` parameter descriptions across the
  five flow tools updated to document the `METHOD /path` disambiguation syntax.
- Added mandatory Python driver pattern to `AGENTS.md` (three rules: `stderr=sys.stderr`,
  send `notifications/initialized`, one request/response at a time).

### ♻️ Refactoring / Code quality

- **JDK 25 toolchain:** build now targets `maven.compiler.release=25` (enforcer
  `requireJavaVersion [25,)`); verified against Spoon 11.3.0.
- **`Spans.traced()` tracing helper:** new `dev.dominikbreu.spoonmcp.tracing.Spans` encapsulates
  the `startSpan → makeCurrent → recordException → end` OpenTelemetry boilerplate behind a
  `Supplier`/`Runnable` pair. `ArchitectureExtractor.extract()` and `PipelineGraphBuilder.build()`
  were migrated onto it, flattening their stacked span `try`-with-resources blocks (clears S1141
  nested-`try` smells project-wide, 7 → 0). Extraction phases in `extract()` were lifted into named
  private methods, dropping the method's cognitive complexity back under threshold (S3776).
- **Java 21 / 25 idioms:** unnamed variables (`_`) for ignored try-with-resources and catch
  bindings; sequenced-collection accessors (`getFirst()`); `Math.clamp`; method references
  (`StringUtils::isNotBlank`, S1612). Removed redundant `(long)` casts on `Span.setAttribute`
  calls — `int` widens to the `long` overload (S1905).
- **commons-lang3 `StringUtils`** adopted for null-safe blank/empty string checks in place of
  hand-rolled helpers; Mermaid label escaping centralized into a single helper.
- **SonarQube quality gate green:** new_violations 0, new_coverage 83.5%,
  new_duplicated_lines_density 0.38%. 556 tests pass; SpotBugs and spotless (palantir-java-format)
  clean.


## [spoon-mcp-server-1.0.2]

### 🚀 Features

- **Tier 1 (dataflow):** producer field-seed (G11), `FieldAccess.sourceFieldName` (G3), and a logger/tracer/audit-type denylist for shared-state heuristics (G10). `MESSAGING_PRODUCER` and `SCHEDULER` entrypoints now seed tracking from transitively read shared-state fields even when they declare parameters. Writes whose RHS is another field read keep the STORE sink attached to paths tracking the upstream field.
- **Tier 2 (dataflow):** nested-argument `paramMapping` recovery (G2) — `CallGraphExtractor` descends into `CtConstructorCall`, nested `CtInvocation`, and `CtConditional`; synthesised mappings are listed in `CallEdge.syntheticParamMappings`. Return-value flow (G1) — `CallEdge.assignedToVar` + `returnsTracked` derived at the entrypoint extends tracking to the assigned local. Killed-locals pruning (G8) — `CallEdge.killedTrackedNames` lists caller locals reassigned before the call site; the tracer drops their tracking.
- **Tier 3 (entrypoints):** Vert.x EventBus consumers (G4) detected via `eventBus.consumer(addr, lambda)` and `eventBus.consumer(addr).handler(lambda)` patterns — emit `EVENT_BUS_CONSUMER`. WebSocket / SSE / gRPC entrypoints (G5) detected: `@ServerEndpoint` + `@OnMessage` → `WEBSOCKET_ENDPOINT`; REST methods producing `text/event-stream` or with an `SseEventSink`/`Sse` param/return → `SSE_ENDPOINT`; classes annotated `@GrpcService`, implementing `BindableService`, or extending `*Grpc.*ImplBase` → `GRPC_METHOD` per public method.
- **Tier 4 (sinks):** new sink kinds `file-outbound` and `object-storage` (G6). Direct invocations against `java.nio.file.Files`, `software.amazon.awssdk.services.s3.*`, and `com.azure.storage.*` are captured in the new `ArchitectureModel.outboundSinkSites` list and emitted as data-flow sinks at depth 0 inside the entrypoint body, even when the callee is not a project component.

### 🐞 Fixes

- Emit STORE sinks at depth 0 when an entrypoint parameter is destructured into a local before being stored.
- Replace `DataFlowSink.kind` String with a typed `Kind` enum (wire format unchanged).
- Wire `jreleaser.dry.run` property through the Maven plugin configuration so `mvn release:perform … -Djreleaser.dry.run=true` correctly activates dry-run mode.
- Set the GitHub remote in the release checkout before JReleaser runs.
- Configure JReleaser to use the maven-release-plugin's `spoon-mcp-server-{{projectVersion}}` tag pattern so future releases generate non-empty changelogs from the previous release anchor.

### 📚 Docs / Tooling

- Document new sink kinds, entrypoint families, and edge metadata in `docs/TOOLS.md` and `llms.txt`.
- Add OWASP `dependency-check` plugin and refresh release-pipeline notes.
- Resolve issue #4.
