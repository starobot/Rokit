package bot.staro.rokit;

import static java.lang.Integer.MAX_VALUE;
import static java.lang.Integer.MIN_VALUE;

/**
 * Enum representing various priority levels for event listeners.
 * Determines the order in which listeners are executed.
 */
public enum Priority {
    MAX(MAX_VALUE),
    HIGH(3),
    MEDIUM(2),
    LOW(1),
    DEFAULT(0),
    UNDERMINING(-1),
    TERRIBLE(-2),
    HORRENDOUS(-3),
    MIN(MIN_VALUE);

    private final int val;

    Priority(int val) {
        this.val = val;
    }

    public int getVal() {
        return val;
    }

}
