package friendfinder;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Logging for the client half.
 *
 * Worth having because the two paths that open the screen both swallow failures:
 * Minecraft's scheduled-task queue captures an exception into a Future nobody reads,
 * and Forge's command handler reports some failures only to chat. Either can look
 * exactly like "the command did nothing".
 */
final class Log {
    private Log() {}

    private static final Logger LOG = LogManager.getLogger("friendfinder");

    static void info(String message) { LOG.info(message); }

    static void warn(String message, Throwable t) { LOG.warn(message, t); }
}
