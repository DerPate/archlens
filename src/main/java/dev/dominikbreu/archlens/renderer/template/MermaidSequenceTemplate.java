package dev.dominikbreu.archlens.renderer.template;

import io.jstach.jstache.JStache;
import io.jstach.jstache.JStacheConfig;
import io.jstach.jstache.JStacheType;
import java.util.List;

/**
 * Typed presentation model for a Mermaid sequence diagram.
 *
 * @param empty whether to render the no-steps fallback
 * @param participantGroups ordered participant groups
 * @param messages ordered sequence messages
 * @param deactivations reverse-order participant deactivations
 */
@JStache(path = "templates/mermaid/sequence.mustache")
@JStacheConfig(type = JStacheType.STACHE)
public record MermaidSequenceTemplate(
        boolean empty,
        List<ParticipantGroup> participantGroups,
        List<Message> messages,
        List<Deactivation> deactivations) {

    /** Makes collection components immutable. */
    public MermaidSequenceTemplate {
        participantGroups = List.copyOf(participantGroups);
        messages = List.copyOf(messages);
        deactivations = List.copyOf(deactivations);
    }

    /**
     * Participants optionally wrapped in an application box.
     *
     * @param boxed whether to emit an application box
     * @param appName escaped application name
     * @param indent participant indentation
     * @param participants ordered participants
     */
    public record ParticipantGroup(boolean boxed, String appName, String indent, List<Participant> participants) {
        /** Makes the participant list immutable. */
        public ParticipantGroup {
            participants = List.copyOf(participants);
        }
    }

    /**
     * One sequence participant.
     *
     * @param pid Mermaid-safe participant id
     * @param display escaped display name
     * @param stereotype component stereotype
     */
    public record Participant(String pid, String display, String stereotype) {}

    /**
     * One synchronous or asynchronous message.
     *
     * @param from source participant id
     * @param to target participant id
     * @param label escaped message label
     * @param async whether to use the asynchronous arrow
     * @param activate whether to activate the target
     */
    public record Message(String from, String to, String label, boolean async, boolean activate) {}

    /**
     * One participant deactivation.
     *
     * @param participant participant id
     */
    public record Deactivation(String participant) {}
}
