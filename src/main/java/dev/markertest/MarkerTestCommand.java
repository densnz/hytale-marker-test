package dev.markertest;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.DefaultArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The /markertest command collection:
 * list, near, trigger, debug on|off [filter], flag, skiptime &lt;minutes&gt;.
 */
final class MarkerTestCommand extends AbstractCommandCollection {

    MarkerTestCommand() {
        super("markertest", "Spawn marker test bench");
        addSubCommand(new ListCommand("list", "List spawn markers within a radius", 64.0));
        addSubCommand(new ListCommand("near", "Show the nearest spawn marker in detail", 16.0));
        addSubCommand(new TriggerCommand());
        addSubCommand(new DebugCommand());
        addSubCommand(new FlagCommand());
        addSubCommand(new SkipTimeCommand());
    }

    private static Vector3d playerPosition(Store<EntityStore> store, Ref<EntityStore> playerEntity) {
        TransformComponent transform = store.getComponent(playerEntity, TransformComponent.getComponentType());
        return transform == null ? new Vector3d() : new Vector3d(transform.getPosition());
    }

    private static void say(CommandContext context, String text) {
        context.sendMessage(Message.raw(text));
    }

    private static final class ListCommand extends AbstractPlayerCommand {
        private final DefaultArg<Double> radiusArg;
        private final boolean nearestOnly;

        ListCommand(String name, String description, double defaultRadius) {
            super(name, description);
            this.nearestOnly = name.equals("near");
            this.radiusArg = withDefaultArg("radius", "Search radius in blocks", ArgTypes.DOUBLE,
                    defaultRadius, String.valueOf((int) defaultRadius));
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> playerEntity, @Nonnull PlayerRef player, @Nonnull World world) {
            double radius = radiusArg.get(context);
            List<MarkerInfo> markers = MarkerInfo.near(store, playerPosition(store, playerEntity), radius);
            say(context, "[MarkerTest] markers enabled in this world: "
                    + world.getWorldConfig().isSpawnMarkersEnabled() + ", found " + markers.size()
                    + " within " + (int) radius + " blocks");
            int shown = 0;
            for (MarkerInfo info : markers) {
                String line = info.describe(store);
                say(context, " - " + line);
                MarkerTestPlugin.log(line);
                if (nearestOnly || ++shown >= 15) {
                    break;
                }
            }
        }
    }

    private static final class TriggerCommand extends AbstractPlayerCommand {
        private final DefaultArg<Double> radiusArg;

        TriggerCommand() {
            super("trigger", "Manually trigger the nearest ManualTrigger marker");
            this.radiusArg = withDefaultArg("radius", "Search radius in blocks", ArgTypes.DOUBLE, 16.0, "16");
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> playerEntity, @Nonnull PlayerRef player, @Nonnull World world) {
            List<MarkerInfo> markers = MarkerInfo.near(store, playerPosition(store, playerEntity), radiusArg.get(context));
            for (MarkerInfo info : markers) {
                if (!info.marker.isManualTrigger()) {
                    continue;
                }
                List<Ref<EntityStore>> spawned = new ArrayList<>();
                boolean ok = info.marker.trigger(info.ref, spawned, store);
                String line = "trigger " + info.marker.getSpawnMarkerId() + " -> " + (ok ? "OK" : "FAILED")
                        + ", spawned " + spawned.size() + (ok ? "" : " (no room / bad role / player in exclusion"
                        + " radius / too high above ground / NPC still alive)");
                say(context, "[MarkerTest] " + line);
                MarkerTestPlugin.log(line + " | " + info.describe(store));
                return;
            }
            say(context, "[MarkerTest] no ManualTrigger marker nearby (" + markers.size() + " other markers)");
        }
    }

    private static final class DebugCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> stateArg;
        private final DefaultArg<String> filterArg;

        DebugCommand() {
            super("debug", "Log marker state changes: on or off");
            this.stateArg = withRequiredArg("state", "on or off", ArgTypes.STRING);
            this.filterArg = withDefaultArg("filter", "Marker id prefix to watch, or all", ArgTypes.STRING,
                    "MT_", "MT_");
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> playerEntity, @Nonnull PlayerRef player, @Nonnull World world) {
            boolean on = stateArg.get(context).equalsIgnoreCase("on");
            String filter = filterArg.get(context);
            MarkerWatchSystem.reset();
            MarkerWatchSystem.filter = filter.equalsIgnoreCase("all") ? "" : filter;
            MarkerWatchSystem.listener = on ? player : null;
            MarkerWatchSystem.debug = on;
            say(context, "[MarkerTest] debug " + (on ? "ON for markers starting with '"
                    + MarkerWatchSystem.filter + "': changes go to chat and the server log" : "OFF"));
            MarkerTestPlugin.log("debug " + (on ? "on, filter='" + MarkerWatchSystem.filter + "'" : "off"));
        }
    }

    private static final class FlagCommand extends AbstractPlayerCommand {
        FlagCommand() {
            super("flag", "Show the world switches that gate automatic marker spawning");
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> playerEntity, @Nonnull PlayerRef player, @Nonnull World world) {
            boolean markers = world.getWorldConfig().isSpawnMarkersEnabled();
            boolean npcs = world.getWorldConfig().isSpawningNPC();
            say(context, "[MarkerTest] IsSpawnMarkersEnabled = " + markers
                    + (markers ? "" : "  -> /world settings spawnmarkers set true"));
            say(context, "[MarkerTest] IsSpawningNPC = " + npcs
                    + (npcs ? "" : "  -> /world settings spawningnpc set true"));
            say(context, "[MarkerTest] automatic (non-manual) markers spawn only when BOTH are true");
            MarkerTestPlugin.log("flags: IsSpawnMarkersEnabled=" + markers + " IsSpawningNPC=" + npcs);
        }
    }

    private static final class SkipTimeCommand extends AbstractPlayerCommand {
        private final RequiredArg<Integer> minutesArg;

        SkipTimeCommand() {
            super("skiptime", "Move world game time forward by N game minutes");
            this.minutesArg = withRequiredArg("minutes", "Game minutes to skip", ArgTypes.INTEGER);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> playerEntity, @Nonnull PlayerRef player, @Nonnull World world) {
            int minutes = minutesArg.get(context);
            if (minutes <= 0 || minutes > 7 * 24 * 60) {
                say(context, "[MarkerTest] minutes must be between 1 and " + (7 * 24 * 60));
                return;
            }
            WorldTimeResource time = store.getResource(WorldTimeResource.getResourceType());
            Instant before = time.getGameTime();
            Instant after = before.plus(Duration.ofMinutes(minutes));
            time.setGameTime(after, world, store);
            String line = "game time " + before + " -> " + after + " (+" + minutes + " min)";
            say(context, "[MarkerTest] " + line);
            MarkerTestPlugin.log(line);
        }
    }
}
