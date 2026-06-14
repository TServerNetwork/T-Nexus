package network.tserver.tnexus.manager;

import java.time.Instant;
import org.jetbrains.annotations.Nullable;

/**
 * Carries contextual metadata for one activity event.
 *
 * @param timestamp activity timestamp
 * @param content optional textual content such as chat or command text
 * @param payload optional typed payload for future policy extensions
 */
public record ActivityContext(
        Instant timestamp,
        @Nullable String content,
        @Nullable Object payload) {

    /**
     * Creates a context with no additional payload.
     *
     * @param timestamp activity timestamp
     * @return context instance
     */
    public static ActivityContext at(Instant timestamp) {
        return new ActivityContext(timestamp, null, null);
    }

    /**
     * Creates a context carrying textual content.
     *
     * @param timestamp activity timestamp
     * @param content textual content
     * @return context instance
     */
    public static ActivityContext withContent(Instant timestamp, String content) {
        return new ActivityContext(timestamp, content, null);
    }

    /**
     * Creates a context carrying an arbitrary payload.
     *
     * @param timestamp activity timestamp
     * @param payload arbitrary payload
     * @return context instance
     */
    public static ActivityContext withPayload(Instant timestamp, Object payload) {
        return new ActivityContext(timestamp, null, payload);
    }
}
