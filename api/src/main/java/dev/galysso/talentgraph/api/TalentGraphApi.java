package dev.galysso.talentgraph.api;

import dev.galysso.talentgraph.api.internal.TalentGraphApiHolder;

import java.util.Optional;
import java.util.UUID;

/**
 * Entry point of the TalentGraph public API.
 *
 * <p>Third-party plugins obtain the singleton through {@link #get()} once
 * TalentGraph has finished loading. Declare a manifest dependency so ordering
 * is guaranteed:</p>
 *
 * <pre>{@code
 * "Dependencies": { "Galysso:talentgraph": ">=0.1.0" }
 * }</pre>
 *
 * <p>For an optional integration, declare it under
 * {@code OptionalDependencies} and use {@link #find()} instead.</p>
 */
public interface TalentGraphApi {

    /**
     * {@return the registry used to contribute talent graphs}
     */
    TalentRegistry registry();

    /**
     * Returns a live view of a player's progression.
     *
     * @param playerId the player identifier
     * @return the player's progression view, created on first access
     */
    PlayerTalents talentsOf(UUID playerId);

    /**
     * Subscribes to progression events. Listeners are invoked on the thread
     * that performed the change; keep them short and non-blocking.
     *
     * @param listener the listener to add
     */
    void addListener(TalentListener listener);

    /**
     * Removes a previously added listener.
     *
     * @param listener the listener to remove
     * @return {@code true} if it was registered
     */
    boolean removeListener(TalentListener listener);

    /**
     * {@return the running API instance}
     *
     * @throws IllegalStateException if TalentGraph is not loaded, which means a
     *                               missing or misordered manifest dependency
     */
    static TalentGraphApi get() {
        return TalentGraphApiHolder.require();
    }

    /**
     * {@return the running API instance, or empty if TalentGraph is absent}
     */
    static Optional<TalentGraphApi> find() {
        return TalentGraphApiHolder.find();
    }
}
