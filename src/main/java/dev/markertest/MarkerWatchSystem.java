package dev.markertest;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.spawning.spawnmarkers.SpawnMarkerEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Once per second looks at every loaded spawn marker and, while debug is on,
 * reports any change of its state to the server log and to the player who enabled debug.
 */
final class MarkerWatchSystem extends DelayedEntitySystem<EntityStore> {

    static volatile boolean debug = false;
    static volatile PlayerRef listener = null;
    static volatile String filter = "MT_";

    private static final Map<UUID, String> LAST_STATE = new ConcurrentHashMap<>();

    MarkerWatchSystem() {
        super(1.0f);
    }

    @Override
    public Query<EntityStore> getQuery() {
        return SpawnMarkerEntity.getComponentType();
    }

    @Override
    public boolean isParallel(int archetypeChunkSize, int taskCount) {
        return false;
    }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                     CommandBuffer<EntityStore> buffer) {
        if (!debug) {
            return;
        }
        SpawnMarkerEntity marker = chunk.getComponent(index, SpawnMarkerEntity.getComponentType());
        UUIDComponent uuidComponent = chunk.getComponent(index, UUIDComponent.getComponentType());
        if (marker == null || uuidComponent == null) {
            return;
        }
        String id = marker.getSpawnMarkerId();
        if (id == null || !id.startsWith(filter)) {
            return;
        }
        UUID uuid = uuidComponent.getUuid();
        // deactivateIn ticks every frame; leave it out of the change detection so the log stays readable.
        String state = MarkerInfo.state(marker, store).replaceAll(" deactivateIn=[^ ]+", "");
        String previous = LAST_STATE.put(uuid, state);
        if (state.equals(previous)) {
            return;
        }
        String line = (previous == null ? "seen " : "changed ") + marker.getSpawnMarkerId()
                + " [" + uuid.toString().substring(0, 8) + "] " + state;
        MarkerTestPlugin.log(line);
        PlayerRef player = listener;
        if (player != null) {
            player.sendMessage(Message.raw("[MarkerTest] " + line));
        }
    }

    static void reset() {
        LAST_STATE.clear();
    }
}
