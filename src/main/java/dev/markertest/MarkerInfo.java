package dev.markertest;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.reference.InvalidatablePersistentRef;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.spawning.assets.spawnmarker.config.SpawnMarker;
import com.hypixel.hytale.server.spawning.spawnmarkers.SpawnMarkerEntity;
import org.joml.Vector3d;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Reads the state of spawn marker entities so commands and the watcher can print it. */
final class MarkerInfo {

    final Ref<EntityStore> ref;
    final SpawnMarkerEntity marker;
    final UUID uuid;
    final Vector3d position;
    final double distance;

    private MarkerInfo(Ref<EntityStore> ref, SpawnMarkerEntity marker, UUID uuid, Vector3d position, double distance) {
        this.ref = ref;
        this.marker = marker;
        this.uuid = uuid;
        this.position = position;
        this.distance = distance;
    }

    /** All markers in the store within radius of center, nearest first. */
    static List<MarkerInfo> near(Store<EntityStore> store, Vector3d center, double radius) {
        List<MarkerInfo> out = new ArrayList<>();
        store.forEachChunk(SpawnMarkerEntity.getComponentType(), (chunk, buffer) -> {
            for (int i = 0; i < chunk.size(); i++) {
                SpawnMarkerEntity marker = chunk.getComponent(i, SpawnMarkerEntity.getComponentType());
                TransformComponent transform = chunk.getComponent(i, TransformComponent.getComponentType());
                if (marker == null || transform == null) {
                    continue;
                }
                Vector3d pos = new Vector3d(transform.getPosition());
                double dist = pos.distance(center);
                if (dist > radius) {
                    continue;
                }
                UUIDComponent uuidComponent = chunk.getComponent(i, UUIDComponent.getComponentType());
                UUID uuid = uuidComponent != null ? uuidComponent.getUuid() : null;
                out.add(new MarkerInfo(chunk.getReferenceTo(i), marker, uuid, pos, dist));
            }
        });
        out.sort(Comparator.comparingDouble(m -> m.distance));
        return out;
    }

    /** Number of spawned NPCs this marker still references that resolve to a live entity. */
    static int aliveNpcs(SpawnMarkerEntity marker, Store<EntityStore> store) {
        InvalidatablePersistentRef[] refs = marker.getNpcReferences();
        if (refs == null) {
            return 0;
        }
        int alive = 0;
        for (InvalidatablePersistentRef npcRef : refs) {
            if (npcRef == null) {
                continue;
            }
            Ref<EntityStore> entity = npcRef.getEntity(store);
            if (entity != null && entity.isValid()) {
                alive++;
            }
        }
        return alive;
    }

    static String state(SpawnMarkerEntity marker, Store<EntityStore> store) {
        SpawnMarker asset = marker.getCachedMarker();
        int refs = marker.getNpcReferences() == null ? 0 : marker.getNpcReferences().length;
        return "spawnCount=" + marker.getSpawnCount()
                + " alive=" + aliveNpcs(marker, store) + "/" + refs
                + " manual=" + marker.isManualTrigger()
                + " deactivateIn=" + String.format("%.1f", marker.getTimeToDeactivation())
                + " despawnStarted=" + marker.isDespawnStarted()
                + " storedFlock=" + (marker.getStoredFlock() != null)
                + " suppressedBy=" + (marker.getSuppressedBy() == null ? 0 : marker.getSuppressedBy().size())
                + " spawnAfter=" + respawnIn(marker, store)
                + (asset == null ? " asset=?" : " exclusion=" + asset.getExclusionRadius()
                        + " deactDist=" + asset.getDeactivationDistance()
                        + " realtime=" + asset.isRealtimeRespawn());
    }

    /** Game-time respawn countdown such as "in 0h37m(game)", or "null" when no game-time respawn is pending. */
    static String respawnIn(SpawnMarkerEntity marker, Store<EntityStore> store) {
        Instant after = marker.getSpawnAfter();
        if (after == null) {
            return "null";
        }
        Instant now = gameTime(store);
        if (now == null) {
            return after.toString();
        }
        Duration left = Duration.between(now, after);
        if (left.isNegative()) {
            return "due(waiting for a player in range)";
        }
        return "in " + left.toHours() + "h" + String.format("%02d", left.toMinutesPart()) + "m(game)";
    }

    static Instant gameTime(Store<EntityStore> store) {
        WorldTimeResource time = store.getResource(WorldTimeResource.getResourceType());
        return time == null ? null : time.getGameTime();
    }

    String describe(Store<EntityStore> store) {
        return marker.getSpawnMarkerId()
                + String.format(" @ %.1f %.1f %.1f (%.1fm) ", position.x, position.y, position.z, distance)
                + state(marker, store);
    }
}
