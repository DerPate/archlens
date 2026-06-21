package dev.dominikbreu.archlens.model;

import dev.dominikbreu.archlens.model.ids.AppId;
import java.util.ArrayList;
import java.util.List;

/**
 * Deployment element discovered from infrastructure descriptors such as Docker Compose or Ansible.
 */
public class DeploymentEntry {
    /** Stable deployment element identifier. */
    public String id;
    /** Deployment service, host, or group name. */
    public String name;
    /** Deployment source type, for example docker-compose, ansible-host, or ansible-group. */
    public String type;
    /** File path that was parsed. */
    public String source;
    /** Application identifiers associated with this deployment element. */
    public List<AppId> appIds = new ArrayList<>();
    /** Published or exposed ports. */
    public List<String> ports = new ArrayList<>();
    /** Deployment-level service dependencies. */
    public List<String> dependsOn = new ArrayList<>();
    /** Ansible roles or equivalent deployment roles. */
    public List<String> roles = new ArrayList<>();
    /** Hosts associated with this deployment entry. */
    public List<String> hosts = new ArrayList<>();

    /** Creates an empty deployment entry for JSON deserialization. */
    public DeploymentEntry() {}
}
