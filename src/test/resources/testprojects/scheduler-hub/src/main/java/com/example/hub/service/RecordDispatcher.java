package com.example.hub.service;

import com.example.hub.client.BrokerClient;
import com.example.hub.client.TopicResolver;
import com.example.hub.rule.AssignmentService;
import com.example.hub.rule.RuleService;
import com.example.hub.rule.model.Assignment;
import com.example.hub.rule.model.Rule;
import com.example.hub.util.ConcurrencyGuard;
import com.example.hub.util.ChannelDepthTracker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.reactive.messaging.annotations.Broadcast;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.OnOverflow;

/**
 * Dispatches aggregated records to downstream processors and external brokers.
 * <p>
 * Reads latest records from {@link RecordStore}, resolves routing rules per item,
 * and fans out to either the external broker (direct) or one of two internal
 * Kafka channels for further pipeline processing.
 */
@ApplicationScoped
public class RecordDispatcher {

    private volatile boolean isShuttingDown = false;

    public static final String ITEM_ID_KEY = "itemId";
    public static final String TIMESTAMP_KEY = "recordTimestamp";
    public static final String PAYLOAD_KEY = "payload";

    private static final Logger LOGGER = Logger.getLogger(RecordDispatcher.class.getName());

    @Inject
    BrokerClient brokerClient;

    @Inject
    TopicResolver topicResolver;

    @Inject
    RecordStore recordStore;

    @Inject
    AssignmentService assignmentService;

    @Inject
    RuleService ruleService;

    @Inject
    ManagedExecutor executor;

    @Inject
    ConcurrencyGuard concurrencyGuard;

    @Inject
    MeterRegistry meterRegistry;

    @Inject
    ChannelDepthTracker channelDepthTracker;

    @Inject
    @Channel("recordsInternal")
    @Broadcast
    @OnOverflow(value = OnOverflow.Strategy.UNBOUNDED_BUFFER, bufferSize = 10_000)
    Emitter<Map.Entry<String, JsonNode>> primaryEmitter;

    @Inject
    @Channel("recordsInternalAlt")
    @Broadcast
    @OnOverflow(value = OnOverflow.Strategy.UNBOUNDED_BUFFER, bufferSize = 10_000)
    Emitter<Map.Entry<String, JsonNode>> altEmitter;

    @ConfigProperty(name = "active.tenant.ids", defaultValue = "+")
    List<String> tenantIds;

    @ConfigProperty(name = "dispatch.debug.enabled", defaultValue = "false")
    boolean debugLoggingEnabled;

    @ConfigProperty(name = "dispatch.debug.tenant-ids", defaultValue = "+")
    List<String> debugTenantIds;

    @ConfigProperty(name = "dispatch.debug.item-ids", defaultValue = "+")
    List<String> debugItemIds;

    @ConfigProperty(name = "dispatch.assignment.cache.max-age-seconds", defaultValue = "30")
    long assignmentCacheMaxAgeSeconds;

    ObjectMapper mapper = new ObjectMapper();

    private Map<String, Assignment> allAssignments = new ConcurrentHashMap<>();
    private volatile long allAssignmentsLastRefreshEpochSecond = 0L;
    private final Object assignmentRefreshLock = new Object();
    private final Map<String, TenantCounters> tenantCounters = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        brokerClient.setClientName("RecordDispatcher");
        brokerClient.connect();
        refreshAssignments();
        registerMetrics();
        preinitializeTenantCounters();
    }

    private void registerMetrics() {
        Gauge.builder(
                        "hub.dispatcher.assignment_cache.entries",
                        this,
                        RecordDispatcher::assignmentCacheSize)
                .description("Number of assignments in RecordDispatcher cache")
                .register(meterRegistry);
        Gauge.builder(
                        "hub.dispatcher.tenant_counter_registry.entries",
                        this,
                        RecordDispatcher::tenantCounterRegistrySize)
                .description("Number of tenant counter bundles in RecordDispatcher")
                .register(meterRegistry);
    }

    private void preinitializeTenantCounters() {
        if (allAssignments != null && !allAssignments.isEmpty()) {
            allAssignments.values().stream()
                    .map(a -> a.tenantId)
                    .distinct()
                    .forEach(this::getOrCreateTenantCounters);
        }
    }

    void ensureTenantCountersInitialized(String tenantId) {
        getOrCreateTenantCounters(tenantId);
    }

    private TenantCounters getOrCreateTenantCounters(String tenantId) {
        String key = tenantId != null ? tenantId : "unknown";
        return tenantCounters.computeIfAbsent(key, this::createTenantCounters);
    }

    private TenantCounters createTenantCounters(String tenantId) {
        return new TenantCounters(
                Counter.builder("hub.dispatcher.processed")
                        .description("Records processed by RecordDispatcher")
                        .tag("tenant_id", tenantId)
                        .register(meterRegistry),
                Counter.builder("hub.dispatcher.assignment.cache_hit")
                        .description("Assignments resolved from cache")
                        .tag("tenant_id", tenantId)
                        .register(meterRegistry),
                Counter.builder("hub.dispatcher.assignment.db_fallback")
                        .description("Assignments resolved via DB fallback")
                        .tag("tenant_id", tenantId)
                        .register(meterRegistry),
                Counter.builder("hub.dispatcher.assignment.default_fallback")
                        .description("Assignments resolved via default-rule fallback")
                        .tag("tenant_id", tenantId)
                        .register(meterRegistry));
    }

    public void shutdown(@Observes ShutdownEvent event) {
        isShuttingDown = true;
        brokerClient.disconnect();
    }

    @Scheduled(every = "5M", delay = 5, delayUnit = TimeUnit.SECONDS)
    public void refreshAssignments() {
        if (isShuttingDown) return;
        allAssignments = assignmentService.getAllAssignments().stream()
                .collect(Collectors.toMap(
                        a -> a.itemId,
                        a -> a,
                        (l, r) -> l,
                        ConcurrentHashMap::new));
        allAssignmentsLastRefreshEpochSecond = Instant.now().getEpochSecond();
    }

    @Scheduled(every = "2s", delay = 10, delayUnit = TimeUnit.SECONDS)
    public void dispatchAll() {
        if (isShuttingDown) return;

        Map<String, JsonNode> records = recordStore.getLatestRecords();
        Set<String> activeItems = recordStore.activeItems();

        if (records.isEmpty()) {
            LOGGER.fine("No records to dispatch");
            return;
        }

        Instant now = Instant.now();
        ensureAssignmentCacheFresh(now);

        records.entrySet().stream()
                .filter(e -> e.getKey() != null)
                .filter(e -> activeItems.contains(e.getKey()))
                .collect(Collectors.groupingBy(e -> resolveItemTenant(e.getKey()), Collectors.toList()))
                .forEach((tenantId, items) -> {
                    final Rule defaultRule = ruleService.getDefault(tenantId);
                    final TenantCounters counters = getOrCreateTenantCounters(tenantId);
                    executor.runAsync(() -> items.forEach(item -> {
                        try {
                            String itemId = item.getKey();
                            counters.incrementProcessed();

                            Assignment cachedAssignment = allAssignments.get(itemId);
                            boolean cacheHit = cachedAssignment != null;
                            Assignment assignment;
                            if (cacheHit) {
                                assignment = cachedAssignment;
                            } else {
                                assignment = resolveAssignmentFromMiss(itemId, defaultRule);
                            }

                            recordAssignmentSourceMetric(cacheHit, assignment, counters);

                            if (assignment.rule == null) {
                                LOGGER.fine("Assignment for " + itemId + " has no rule, using default");
                                assignment = new Assignment(itemId, defaultRule, tenantId);
                            }

                            boolean debugItem = isDebugItem(itemId, tenantId);
                            String routing = null;

                            if (assignment.rule.workflowId != null && assignment.rule.isDynamic) {
                                String topic = topicResolver.buildDispatchTopic(
                                        tenantId, itemId, assignment.rule.workflowId);
                                buildAndSend(now, item, itemId, topic);
                                routing = "dynamic";
                            } else if (assignment.rule.id.equals(defaultRule.id)
                                    && assignment.rule.isBase
                                    && assignment.rule.isDynamic) {
                                String topic = topicResolver.buildDispatchTopic(
                                        tenantId, itemId, assignment.rule.workflowId);
                                buildAndSend(now, item, itemId, topic);
                                routing = "dynamic-default";
                            } else if (Objects.equals(assignment.rule.id, "DEFAULT_RULE_ID")
                                    && tenantIsEnabled(tenantId)) {
                                if (!primaryEmitter.isCancelled()) {
                                    channelDepthTracker.incrementPrimary();
                                    primaryEmitter.send(item);
                                }
                                routing = "primary-internal";
                            } else if (Objects.equals(assignment.rule.name, "ALT_DEFAULT_RULE")
                                    && tenantIsEnabled(tenantId)) {
                                if (!altEmitter.isCancelled()) {
                                    channelDepthTracker.incrementAlt();
                                    altEmitter.send(item);
                                }
                                routing = "alt-internal";
                            }

                            if (debugItem) {
                                LOGGER.info(String.format(
                                        "Dispatch debug tenant=%s item=%s routing=%s cacheHit=%s ruleId=%s",
                                        tenantId, itemId, routing, cacheHit, assignment.rule.id));
                            }
                        } catch (Exception e) {
                            LOGGER.log(Level.WARNING, e, () -> "Dispatch failed for item");
                        }
                    }));
                });
    }

    @Scheduled(every = "10M", delay = 2)
    public void heartBeat() {
        LOGGER.info("Dispatcher heartbeat activeItems=" + recordStore.activeItems().size()
                + " cacheSize=" + allAssignments.size()
                + " primaryPending=" + channelDepthTracker.primaryPending.get()
                + " altPending=" + channelDepthTracker.altPending.get());
    }

    private boolean tenantIsEnabled(String tenantId) {
        return tenantIds != null && ("+".equals(tenantIds.get(0)) || tenantIds.contains(tenantId));
    }

    private void ensureAssignmentCacheFresh(Instant now) {
        if (assignmentCacheMaxAgeSeconds <= 0) return;
        long nowEpoch = now.getEpochSecond();
        if (nowEpoch - allAssignmentsLastRefreshEpochSecond < assignmentCacheMaxAgeSeconds) return;
        synchronized (assignmentRefreshLock) {
            if (nowEpoch - allAssignmentsLastRefreshEpochSecond < assignmentCacheMaxAgeSeconds) return;
            refreshAssignments();
        }
    }

    private Assignment resolveAssignmentFromMiss(String itemId, Rule defaultRule) {
        Assignment assignment = concurrencyGuard.runWithPermit(
                () -> assignmentService.getAssignmentWithDefaultUncached(itemId));
        if (assignment != null) {
            allAssignments.put(itemId, assignment);
            return assignment;
        }
        LOGGER.fine("Assignment for " + itemId + " not found, creating default");
        return new Assignment(itemId, defaultRule, "unknown");
    }

    private boolean isDebugItem(String itemId, String tenantId) {
        if (!debugLoggingEnabled) return false;
        if (itemId == null || tenantId == null) return false;
        boolean tenantMatches = debugTenantIds != null
                && (debugTenantIds.contains("+") || debugTenantIds.contains(tenantId));
        boolean itemMatches = debugItemIds != null
                && (debugItemIds.contains("+") || debugItemIds.contains(itemId));
        return tenantMatches && itemMatches;
    }

    private void buildAndSend(Instant now, Map.Entry<String, JsonNode> entry, String itemId, String topic) {
        ObjectNode record = mapper.createObjectNode();
        record.put(ITEM_ID_KEY, itemId);
        record.put(TIMESTAMP_KEY, now.toString());
        record.set(PAYLOAD_KEY, entry.getValue());
        brokerClient.publish(topic, record.toString());
    }

    private void recordAssignmentSourceMetric(boolean cacheHit, Assignment assignment, TenantCounters counters) {
        if (cacheHit) {
            counters.incrementCacheHit();
            return;
        }
        if (assignment != null && assignment.id != null) {
            counters.incrementDbFallback();
            return;
        }
        counters.incrementDefaultFallback();
    }

    private String resolveItemTenant(String itemId) {
        Assignment a = allAssignments.get(itemId);
        return a != null ? a.tenantId : "unknown";
    }

    int assignmentCacheSize() {
        return allAssignments.size();
    }

    int tenantCounterRegistrySize() {
        return tenantCounters.size();
    }

    record TenantCounters(Counter processed, Counter cacheHit, Counter dbFallback, Counter defaultFallback) {
        void incrementProcessed() { processed.increment(); }
        void incrementCacheHit() { cacheHit.increment(); }
        void incrementDbFallback() { dbFallback.increment(); }
        void incrementDefaultFallback() { defaultFallback.increment(); }
    }
}
