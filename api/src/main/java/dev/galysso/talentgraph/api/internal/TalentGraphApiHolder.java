package dev.galysso.talentgraph.api.internal;

import dev.galysso.talentgraph.api.TalentGraphApi;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Wiring between the API and its implementation.
 *
 * @apiNote Not part of the public API. It is only {@code public} because the
 *          implementation lives in another package. Third-party plugins must
 *          go through {@link TalentGraphApi#get()} and never call
 *          {@link #install(TalentGraphApi)}.
 */
public final class TalentGraphApiHolder {

    private static final AtomicReference<TalentGraphApi> INSTANCE = new AtomicReference<>();

    private TalentGraphApiHolder() {
    }

    /**
     * Publishes the implementation. Called once by TalentGraph itself.
     *
     * @param api the implementation
     * @throws IllegalStateException if an instance is already installed
     */
    public static void install(TalentGraphApi api) {
        if (!INSTANCE.compareAndSet(null, api)) {
            throw new IllegalStateException("TalentGraph API is already installed");
        }
    }

    /**
     * Withdraws the implementation, on plugin shutdown or reload.
     */
    public static void uninstall() {
        INSTANCE.set(null);
    }

    /**
     * {@return the installed implementation}
     *
     * @throws IllegalStateException if none is installed
     */
    public static TalentGraphApi require() {
        TalentGraphApi api = INSTANCE.get();
        if (api == null) {
            throw new IllegalStateException(
                    "TalentGraph is not loaded. Declare \"Galysso:talentgraph\" "
                            + "in your manifest Dependencies.");
        }
        return api;
    }

    /**
     * {@return the installed implementation, or empty if none is installed}
     */
    public static Optional<TalentGraphApi> find() {
        return Optional.ofNullable(INSTANCE.get());
    }
}
