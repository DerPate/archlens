package dev.dominikbreu.archlens.model;

import dev.dominikbreu.archlens.model.ids.AppId;
import dev.dominikbreu.archlens.model.ids.ComponentId;
import java.util.ArrayList;
import java.util.List;

/** Effective transaction policy governing one component method. */
public class TransactionPolicy {
    /** Stable graph identifier. */
    public String id;
    /** Owning application/module. */
    public AppId appId;
    /** Governed component. */
    public ComponentId componentId;
    /** Governed method name. */
    public String methodName;
    /** Governed method signature. */
    public String methodSignature;
    /** Framework family: spring, jakarta, javax, ejb, spring-xml, ejb-xml, or programmatic. */
    public String framework;
    /** Normalized policy such as REQUIRED or REQUIRES_NEW. */
    public String policy;
    /** Framework-native declaration value. */
    public String nativePolicy;
    /** Read-only hint when declared. */
    public Boolean readOnly;
    /** Isolation level when declared. */
    public String isolation;
    /** Declared rollback/no-rollback rules. */
    public List<String> rollbackRules = new ArrayList<>();
    /** method, type, inherited-type, xml, xml-unresolved, ejb-default, bean-managed, or programmatic-api. */
    public String declarationLevel;
    /** Whether the policy is a framework default rather than explicit source. */
    public boolean defaulted;
    /** Whether programmatic/bean-managed transaction handling prevents CMT inference. */
    public boolean programmatic;
    /** Visible inference limitations. */
    public List<String> limitations = new ArrayList<>();
    /** Declaration/default evidence. */
    public SourceInfo source;

    /** Creates an empty transaction policy fact. */
    public TransactionPolicy() {}
}
