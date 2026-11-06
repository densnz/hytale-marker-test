package dev.markertest;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import javax.annotation.Nonnull;

/**
 * Entry point of the MarkerTest plugin: a small test bench for Hytale spawn markers.
 * Registers the /markertest command and a watcher system that reports marker state changes.
 */
public class MarkerTestPlugin extends JavaPlugin {

    private static HytaleLogger logger;

    public MarkerTestPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        logger = getLogger();
        getCommandRegistry().registerCommand(new MarkerTestCommand());
        getEntityStoreRegistry().registerSystem(new MarkerWatchSystem());
        logger.atInfo().log("[MarkerTest] ready: /markertest list | near | trigger | debug on|off [filter] | flag | skiptime <minutes>");
    }

    static void log(String message) {
        if (logger != null) {
            logger.atInfo().log("[MarkerTest] " + message);
        }
    }
}
