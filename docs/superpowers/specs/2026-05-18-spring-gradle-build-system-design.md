# Spring Boot And Gradle Build System Design

## Context

Spoon MCP Server currently discovers project shape through Maven-specific logic in `SpoonScanner` and `ArchitectureExtractor`. `SpoonScanner` reads `pom.xml` modules and Maven packaging, while `ArchitectureExtractor` recursively registers only Maven module trees. Technology detection checks Maven text plus Quarkus and Java EE annotations. Spring Boot projects, especially Gradle-based Phoenix projects, therefore often appear as `technology: unknown` and expose only generic Java entrypoints such as `main`.

The goal is to make build-system metadata a first-class input to extraction and add a comprehensive Spring Boot extractor. This should improve Gradle Spring Boot projects without weakening existing Maven, Quarkus, Java EE, WAR-module, workflow, and object-flow behavior.

## Goals

- Introduce a real build-system layer with separate Maven and Gradle implementations.
- Move module discovery, packaging/artifact classification, plugin detection, source roots, and resource roots out of ad hoc scanner/extractor logic.
- Treat Gradle as a first-class build system boundary from the beginning.
- Add broad Spring Boot technology and architecture extraction.
- Resolve common Spring configuration values from resource files with the same bounded discipline as the current messaging config resolver.
- Preserve existing MCP model concepts unless a new enum or graph label is clearly needed.

## Non-Goals

- Do not execute Gradle or Maven builds during extraction.
- Do not implement full Spring environment simulation, profile activation, property placeholder expansion across arbitrary sources, or runtime bean graph construction.
- Do not add GitHub Actions, hooks, release automation, or generated output.
- Do not introduce new graph vertex or edge labels for the first Spring pass unless implementation proves the existing model cannot represent the evidence.

## Build System Architecture

Add a package such as `dev.dominikbreu.spoonmcp.build` with a common build metadata boundary:

- `BuildSystemDetector`: identifies Maven, Gradle Groovy, Gradle Kotlin, mixed, or unknown projects.
- `BuildProject`: root-level view containing build system, root path, modules, evidence, and confidence.
- `BuildModule`: module name, module path, parent/child relation, packaging or runtime artifact type, declared plugins, source roots, and resource roots.
- `MavenBuildProjectDetector`: owns current `pom.xml` parsing, Maven modules, Maven packaging, and Maven plugin/dependency evidence.
- `GradleBuildProjectDetector`: owns `settings.gradle`, `settings.gradle.kts`, `build.gradle`, and `build.gradle.kts` parsing.
- `BuildMetadataService`: orchestrates detectors and provides `ArchitectureExtractor` a normalized project/module tree.

For v1, Gradle detection is structurally first-class but semantically conservative. It parses literal module includes and literal plugin declarations, including common forms such as:

```gradle
include "api", "service"
include(":worker")
plugins {
    id "org.springframework.boot"
    id "java"
}
```

and Kotlin DSL equivalents:

```kotlin
include("api", "service")
include(":worker")
plugins {
    id("org.springframework.boot")
    java
}
```

The detector should classify common artifact types:

- `war` when Maven packaging or Gradle `war` plugin is present.
- `boot-jar` when Spring Boot plugin is present and no WAR signal dominates.
- `jar` for Java library/application modules without stronger evidence.
- `unknown` when no reliable evidence exists.

`SpoonScanner` should stop owning module traversal. It should accept source roots from `BuildModule` and add those roots to Spoon, with fallback behavior for unknown/plain Java roots. `ArchitectureExtractor.resolveAndRegisterModules` should consume `BuildProject` and `BuildModule` instead of recursively reading Maven modules.

Follow-up TODO: deepen Gradle support beyond literal parsing. Future Gradle work should cover richer source sets, artifact/task semantics such as `bootJar`, dependency metadata, convention plugins, version catalogs, included builds, and possibly Gradle Tooling API resolution if build execution becomes acceptable.

## Technology Detection

Technology detection should combine build metadata and source annotations:

- Detect `spring-boot` from Spring Boot Maven plugins/dependencies, Gradle `org.springframework.boot`, or `@SpringBootApplication`.
- Detect `spring` from Spring stereotypes and framework annotations when Spring Boot evidence is absent.
- Continue detecting `quarkus` and `javaee` as today.
- Preserve `unknown` only when no build or annotation evidence identifies a framework.

When a module is detected as Spring Boot, `ArchitectureExtractor` should dispatch to `SpringExtractor`. For unknown modules, the fallback can run Quarkus, Java EE, Spring, event bus, and generic Java extraction conservatively, using duplicate-id checks to avoid repeated components.

## Spring Boot Extraction

Add `SpringExtractor` beside `QuarkusExtractor`, `JavaEEExtractor`, and `GenericJavaExtractor`.

Component detection should use annotations and stronger source evidence before naming conventions:

- `@SpringBootApplication` as an application/main component.
- `@RestController` and `@Controller` as `REST_RESOURCE`.
- `@Service` as `SERVICE`.
- `@Repository` as `REPOSITORY`.
- `@Component` as `SERVICE` unless stronger evidence classifies it as scheduler, listener, client, or another existing model type.
- `@Configuration` as a service-like configuration component with stereotype `configuration`.
- `@Entity` from `javax.persistence` or `jakarta.persistence` as `ENTITY`.
- Feign clients via `@FeignClient` as `HTTP_CLIENT`.

REST entrypoints should be extracted from:

- `@RequestMapping`
- `@GetMapping`
- `@PostMapping`
- `@PutMapping`
- `@DeleteMapping`
- `@PatchMapping`

Class-level and method-level paths should be combined and normalized. HTTP methods from composed mapping annotations should be set directly. HTTP methods from `@RequestMapping(method = ...)` should be resolved when the value is literal.

Other inbound entrypoints:

- `@Scheduled` methods become `SCHEDULER`.
- `@KafkaListener` methods become `MESSAGING_CONSUMER`.
- `@RabbitListener` methods become `MESSAGING_CONSUMER`.
- `@JmsListener` methods become `JMS_CONSUMER`.
- `CommandLineRunner` and `ApplicationRunner` should be represented with existing entrypoint types where possible, preferably without adding a new enum in v1.

Outbound interfaces:

- Feign client type and method mappings become `rest_client` and `rest_client_operation` interfaces.
- `RestTemplate` and `WebClient` usages should be detected where fields, beans, or method call sites expose literal or property-backed URLs.
- Kafka, Rabbit, and JMS template usages should be detected as producer interfaces when literal or property-backed topic, queue, routing key, or destination names are visible.

Spring extraction should reuse existing model types:

- `REST_RESOURCE`, `SERVICE`, `REPOSITORY`, `ENTITY`, `SCHEDULER`, `HTTP_CLIENT`, and `MESSAGE_DRIVEN_BEAN` where appropriate.
- `REST_ENDPOINT`, `SCHEDULER`, `MESSAGING_CONSUMER`, `MESSAGING_PRODUCER`, `JMS_CONSUMER`, and `MAIN_METHOD` where appropriate.
- Existing interface type strings such as `rest_endpoint`, `rest_client`, `rest_client_operation`, `messaging_consumer`, and `messaging_producer`.

## Spring Configuration Resolution

Add a Spring configuration resolver with similar boundaries to `MessagingConfigResolver`.

It should read only these files under each module resource root:

- `application.properties`
- `application.yml`
- `application.yaml`

It should resolve only keys used as architecture evidence, such as:

- `spring.application.name`
- `server.servlet.context-path`
- `spring.kafka.consumer.*` and `spring.kafka.producer.*` values relevant to listener/template topics.
- `spring.rabbitmq.*` values relevant to listener/template destinations.
- Spring Cloud OpenFeign and common client base URL properties when referenced by annotation values or code literals.

The resolver should support literal property and YAML values. It should not emulate Spring profiles, environment variables, command-line arguments, or arbitrary placeholder expansion. Simple `${key}` references can be resolved only when both sides are present in the same parsed resource set and the implementation remains small and deterministic.

## Testing Strategy

Build layer tests:

- Maven single-module and multi-module detection.
- Maven packaging and plugin/dependency evidence.
- Gradle Groovy single-module and multi-module detection.
- Gradle Kotlin single-module and multi-module detection.
- Literal Gradle include parsing.
- Gradle plugin and artifact-type classification.
- Source and resource root discovery.

Scanner and extractor integration tests:

- `SpoonScanner` scans source roots supplied by build metadata.
- `ArchitectureExtractor` registers apps/modules from `BuildProject`/`BuildModule`.
- Existing WAR parent/child role behavior is preserved for Maven.
- Gradle Spring Boot modules become applications with useful packaging/artifact type and technology.

Spring extractor tests:

- REST controller paths and HTTP methods.
- Service, repository, entity, component, configuration, and application component classification.
- Scheduler entrypoints.
- Kafka, Rabbit, and JMS listener entrypoints.
- Feign client interfaces and operations.
- `RestTemplate`, `WebClient`, Kafka template, Rabbit template, and JMS template outbound interfaces where literal or property-backed evidence exists.
- Runtime flow smoke coverage for a Spring REST controller to service/repository chain.

Config resolver tests:

- Properties and YAML parsing.
- Topic/queue/base URL resolution.
- Bounded same-resource-set placeholder resolution for simple `${key}` references.
- Missing or malformed files result in empty evidence rather than extraction failure.

Regression tests:

- Existing Maven, Quarkus, Java EE, WAR-module, event-bus, object-flow, data-flow, graph, and runtime-flow tests continue to pass.

## Documentation

Update `docs/TOOLS.md` to include:

- `spring-boot` and `spring` technology examples.
- Spring REST, scheduler, messaging listener, and outbound client behavior.
- Any newly documented interface type usage.

Update `docs/ARCHITECTURE.md` to describe:

- Build-system metadata ownership.
- `SpoonScanner` as a source-root scanner rather than a module detector.
- `ArchitectureExtractor` consuming normalized build metadata.
- Spring extraction responsibilities.

Update `llms.txt` with:

- Notes for Gradle and Spring Boot projects.
- The explicit follow-up TODO that Gradle should become richer than literal parsing.

## Acceptance Criteria

- Gradle Spring Boot projects no longer fall back to mostly `technology: unknown`.
- Gradle single-module and multi-module projects are indexed through the build metadata layer.
- Spring Boot projects expose REST, scheduler, messaging, main/startup, and outbound integration evidence where source/config literals support it.
- Runtime flows become useful for Spring REST/scheduler/messaging entrypoints.
- Existing Maven and framework behavior remains compatible.
- No generated output from `target/`, `.spoon-mcp-cache/`, or `dependency-reduced-pom.xml` is committed.
