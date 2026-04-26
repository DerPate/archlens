package dev.dominikbreu.spoonmcp.extractor;

import dev.dominikbreu.spoonmcp.model.Component;
import dev.dominikbreu.spoonmcp.model.ComponentType;

/**
 * Assigns a bounded evidence score to extracted dependencies.
 *
 * <p>The value is not a statistical probability. It represents how directly
 * the dependency was observed in source code.</p>
 */
class DependencyEvidenceScorer {

    double score(Component from, Component to, boolean annotationBacked) {
        if (annotationBacked) {
            return annotationScore(to);
        }

        double score = 0.55;
        if (from != null && to != null && sameModule(from, to)) {
            score += 0.05;
        }
        if (to != null && architecturalTarget(to.type)) {
            score += 0.05;
        }
        return round(score);
    }

    private double annotationScore(Component target) {
        double score = 0.9;
        if (target != null && target.type == ComponentType.REPOSITORY) {
            score += 0.03;
        }
        if (target != null && target.type == ComponentType.REMOTE_SERVICE) {
            score += 0.03;
        }
        return round(score);
    }

    private boolean sameModule(Component from, Component to) {
        return from.module != null && from.module.equals(to.module);
    }

    private boolean architecturalTarget(ComponentType type) {
        return switch (type) {
            case REST_RESOURCE, SERVICE, REPOSITORY, EJB_STATELESS, EJB_STATEFUL, EJB_SINGLETON,
                 MESSAGE_DRIVEN_BEAN, SCHEDULER, HTTP_CLIENT, CDI_EVENT_CONSUMER,
                 CDI_EVENT_PRODUCER, REMOTE_SERVICE -> true;
            case ENTITY, UTILITY, UNKNOWN -> false;
        };
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
