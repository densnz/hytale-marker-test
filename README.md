# MarkerTest — a test bench for Hytale spawn markers

MarkerTest is a small Hytale server plugin for learning how **NPC spawn markers** work in practice.
It ships five test markers that each exercise one marker setting, and a `/markertest` command that
shows what every marker is doing: whether its NPC is alive, how long until it respawns, whether it
is deactivated, and why a manual trigger failed.

It was written to help mod authors who want to move their NPC spawning from world/environment spawn
rules to spawn markers, which are only partly covered by the official documentation.

- Hytale server: **0.7.5 – 0.7.x** (built against `0.7.5`, also compiles against `0.7.6`)
- Java: **25**
- License: MIT

> This is an unofficial community project and is not affiliated with Hypixel Studios.

---

## Contents

1. [What is in the mod](#what-is-in-the-mod)
2. [Installing and using it](#installing-and-using-it)
3. [Commands](#commands)
4. [Test markers](#test-markers)
5. [Test walkthrough and results](#test-walkthrough-and-results)
6. [How spawn markers work](#how-spawn-markers-work)
7. [Building from source](#building-from-source)
8. [Project layout](#project-layout)
9. [Known gaps](#known-gaps)
10. [Credits](#credits)

---

## What is in the mod

| Part | File | Purpose |
|---|---|---|
| Plugin entry point | `MarkerTestPlugin.java` | Registers the command and the watcher system |
| Commands | `MarkerTestCommand.java` | `/markertest list, near, trigger, debug, flag, skiptime` |
| Marker inspection | `MarkerInfo.java` | Finds markers around the player and formats their state |
| Watcher | `MarkerWatchSystem.java` | Once per second reports every change of a marker's state |
| Test markers | `src/main/resources/Server/NPC/Spawn/Markers/MT_*.json` | Five markers, one feature each |

The mod also works as an asset pack (`IncludesAssetPack: true`), so the test markers are loaded
together with the plugin.

---

## Installing and using it

1. Download `MarkerTest-<version>.jar` from the
   [latest release](https://github.com/densnz/hytale-marker-test/releases/latest)
   (or [build it yourself](#building-from-source)) and put it into `%APPDATA%\Hytale\UserData\Mods`.
2. Create a **new test world** and enable *MarkerTest* in that world's mod list.
   Do not test in a world you care about.
3. Check the world switches:
   ```
   /markertest flag
   ```
   Both `IsSpawnMarkersEnabled` and `IsSpawningNPC` must be `true` (see
   [World switches](#world-switches)). If they are not:
   ```
   /world settings spawnmarkers set true
   /world settings spawningnpc set true
   ```
4. Turn on the watcher:
   ```
   /markertest debug on
   ```
5. Place the test markers a few blocks apart with the built-in command:
   ```
   /spawning markers add MT_Fast_Respawn
   /spawning markers add MT_Weighted_Nothing
   /spawning markers add MT_Exclusion
   /spawning markers add MT_Manual
   /spawning markers add MT_Skeleton_GameTime
   ```
   Markers are visible in creative mode and hidden from players in adventure mode.
6. Watch the chat (and the server log, lines start with `[MarkerTest]`).

If markers are placed but nothing spawns at all, even after checking the switches, restart the
world once. In our first session no marker spawned, not even the ones generated with the world;
after a restart everything worked. We did not find the exact reason.

---

## Commands

All commands are player commands under `/markertest`.

| Command | What it does |
|---|---|
| `/markertest flag` | Shows `IsSpawnMarkersEnabled` and `IsSpawningNPC` for the current world and the command to fix each |
| `/markertest list [--radius=64]` | Lists up to 15 markers around you, nearest first, with their state |
| `/markertest near [--radius=16]` | Shows only the nearest marker |
| `/markertest trigger [--radius=16]` | Triggers the nearest `ManualTrigger` marker and reports OK / FAILED and how many NPCs spawned |
| `/markertest debug on [filter]` | Reports every state change of markers whose ID starts with `filter` (default `MT_`; use `all` for every marker) |
| `/markertest debug off` | Stops the reports |
| `/markertest skiptime <minutes>` | Moves the world's game time forward, to test game-time respawns without waiting |

### Reading the state line

```
MT_Fast_Respawn @ -1029.5 123.0 361.6 (6.7m) spawnCount=1 alive=1/1 manual=false deactivateIn=0.0
despawnStarted=false storedFlock=true suppressedBy=0 spawnAfter=null exclusion=0.0 deactDist=40.0 realtime=true
```

| Field | Meaning |
|---|---|
| `spawnCount` | NPCs the marker considers spawned and not yet dead |
| `alive=a/b` | `b` NPC references held by the marker, `a` of them resolve to a live entity |
| `manual` | Marker only spawns when triggered |
| `deactivateIn` | Seconds left before the marker deactivates (no players in range) |
| `despawnStarted` | The marker is storing its NPC away because no player is near |
| `storedFlock` | The marker has a stored-flock object (it is present on all markers, so `true` alone means little) |
| `suppressedBy` | Number of spawn suppressors currently blocking this marker |
| `spawnAfter` | Pending game-time respawn: `in 0h37m(game)`, `due(...)`, or `null` |
| `exclusion`, `deactDist`, `realtime` | Values from the marker asset |

---

## Test markers

All test markers use NPC roles that exist in the base game: `Archaeopteryx` (a small passive
creature) and `Skeleton_Burnt_Soldier_Wander`.

| Marker | Settings | What to observe |
|---|---|---|
| `MT_Fast_Respawn` | `RealtimeRespawn: true`, `RealtimeRespawnTime: 10` | Kill the NPC, a new one appears 10 real seconds later |
| `MT_Weighted_Nothing` | 25 % `Archaeopteryx`, 75 % entry without `Name` | Most of the time nothing spawns |
| `MT_Exclusion` | `ExclusionRadius: 8` | Does not spawn while a player is closer than 8 blocks |
| `MT_Manual` | `ManualTrigger: true` | Spawns only on `/markertest trigger` |
| `MT_Skeleton_GameTime` | `SpawnAfterGameTime: "PT1H"` | After death, respawns one in-game hour later (`/markertest skiptime 61` to skip) |

Example (`MT_Fast_Respawn.json`):

```json
{
  "Model": "NPC_Spawn_Marker",
  "NPCs": [
    { "Name": "Archaeopteryx", "Weight": 100, "RealtimeRespawnTime": 10 }
  ],
  "RealtimeRespawn": true,
  "ExclusionRadius": 0,
  "DeactivationDistance": 40,
  "DeactivationTime": 5
}
```

---

## Test walkthrough and results

Tested on Hytale 0.7.5 in a creative test world, together with a large mod pack.

| Check | Result |
|---|---|
| `MT_Manual` + `/markertest trigger` | ✅ NPC spawns. Triggering again while that NPC is alive fails. Works even when automatic markers do nothing |
| `MT_Fast_Respawn` | ✅ After a kill the next NPC appeared exactly 10 s later (14:24:38 → 14:24:48 in the log) |
| `MT_Weighted_Nothing` | ✅ Spawned only sometimes |
| `MT_Exclusion` | ✅ Waited until the player moved away |
| Deactivation (walk > 40 blocks away and back) | ✅ Markers set `despawnStarted=true`, stored their NPCs, and restored them when the player returned |
| `MT_Skeleton_GameTime` | ✅ On death the marker recorded a game-time `spawnAfter`. The actual respawn after one game hour was not checked |
| `/spawning markers add` | ✅ Places a marker at the player's position and rotation |

---

## How spawn markers work

This section combines the official documentation
([Spawn markers](https://docs.hytale.com/assets/npc/spawn/markers/),
[SpawnMarkerEntity API](https://docs.hytale.com/api/com/hypixel/hytale/server/spawning/spawnmarkers/SpawnMarkerEntity))
with what we found by reading the server's public API and by testing. Items marked **(code)** are
not in the documentation; they come from the server classes of 0.7.5/0.7.6 and may change.

### The marker asset

Markers are JSON assets in `Server/NPC/Spawn/Markers/<Id>.json`.

| Field | Default | Meaning |
|---|---|---|
| `Model` | server default | Model shown for the marker in creative mode (`NPC_Spawn_Marker`) |
| `NPCs` | — | Weighted list of spawn entries |
| `NPCs[].Name` | — | NPC role to spawn. An entry **without `Name` spawns nothing**, which gives a chance of "empty" spawns |
| `NPCs[].Weight` | 0 | Relative weight in the list |
| `NPCs[].RealtimeRespawnTime` | 0 | Respawn delay in real seconds (used with `RealtimeRespawn: true`) |
| `NPCs[].SpawnAfterGameTime` | — | Respawn delay in game time, ISO-8601 duration such as `PT1H` or `P1D` (used with `RealtimeRespawn: false`) |
| `NPCs[].Flock` | — | Optional flock spawned around the NPC |
| `RealtimeRespawn` | false | Which of the two respawn delays is used |
| `ManualTrigger` | false | Spawn only when triggered |
| `ExclusionRadius` | 0 | Do not spawn while a player in adventure mode is within this radius |
| `DeactivationDistance` | 40 | With no player inside this distance the marker deactivates |
| `DeactivationTime` | 5 | Seconds before deactivation happens |
| `MaxDropHeight` | 2 | Max height above ground for spawning ground NPCs |
| `ValidatePosition`, `ValidateBelowPosition` | true | Reject marker positions inside blocks / above empty space |
| `RequireFitAtMarkerHeight` | false | NPC must fit at the marker's own height |
| `MaxHorizontalSpawnOffset` | 0 | How far along the marker's facing to search for a spawn spot |
| `SpawnPositionSearch` | `Line` | `Line` or `Range` |
| `BlockOverlapHeightCompensation` | 1 | How far up a marker stuck in a block may be corrected |

The weighted entry is re-rolled on every spawn **(code)**: `spawnNPC` picks from the weighted list
with a random number each time.

### World switches

**(code)** Automatic spawning of a marker happens only when **all** of these are true:

- the world setting `IsSpawningNPC` is `true` (`/world settings spawningnpc set true`);
- the world setting `IsSpawnMarkersEnabled` is `true` (`/world settings spawnmarkers set true`,
  or `/spawning markers enable`);
- the marker is not `ManualTrigger`;
- no spawn suppressor currently suppresses the marker.

Manual triggering ignores both world settings. That is why `MT_Manual` worked in our first session
while nothing else did.

### Lifecycle of one marker

**(code)**, simplified from `SpawnMarkerSystems.Ticking`, which runs every tick for every marker:

1. **Players in range?** The marker looks for players within `DeactivationDistance`.
   - Players present and NPCs are stored → the stored NPCs are put back into the world.
   - No players → after `DeactivationTime` seconds the marker stores its NPCs and removes them from
     the world (`despawnStarted`). If the NPC's model has a `Despawn` animation, it is played first.
     A dead NPC or one in a busy state postpones the deactivation.
2. **Lost NPC.** If the marker cannot find the NPC it spawned for a while, it gives up on it
   ("Marker lost spawned NPC") and becomes free to spawn again. NPCs that lose their marker
   despawn ("NPCEntity despawning due to lost marker" in the server log).
3. **Respawn.** If the world switches allow it and the marker is not manual:
   - `RealtimeRespawn: true` → a real-time countdown (`RealtimeRespawnTime`) runs, then a new NPC spawns;
   - `RealtimeRespawn: false` → the marker waits until the world's game time passes `spawnAfter`,
     which is set when the NPC dies, using `SpawnAfterGameTime` as the delay.
4. **Spawn.** A weighted entry is chosen, a position is searched near the marker, and the NPC (and
   its flock) is created. A failed spawn has a reason **(code)**: `INVALID_ROLE`,
   `NONEXISTENT_ROLE`, `FAILED_ROLE_VALIDATION`, `NO_ROOM`, `NO_VALID_SPAWN_POSITION`,
   `NO_MOVEMENT_MODES`, `MOVEMENT_MODE_REJECTED`, `TOO_HIGH`, `INVALID_SPAWN_INPUT`.

One marker keeps one live NPC (plus its flock). Triggering a manual marker whose NPC is alive fails.

### Ways to place a marker

| Way | How | Notes |
|---|---|---|
| Command **(code)** | `/spawning markers add <MarkerId> [--flip]` (alias `/sp`) | Creates a marker entity at the player's position and rotation. `--flip` flips its rotation around the vertical axis. Other subcommands: `enable`, `disable`, `autoremoveinvalid`, `debuginvalid` |
| Block **(code)** | A block type with the `SpawnMarkerBlock` component | The block creates and keeps a marker entity, see below. This is the way to put markers into builds and prefabs |
| World generation / prefabs | Marker entities saved in generated structures | The server has systems for markers "added from prefabs or world generation"; vanilla worlds contain many such markers (goblins, skeletons) |
| Companion block spawner | `Server/NPC/Spawn/CompanionBlockSpawners/*.json` | A spawner block plus required nearby blocks keeps a marker's NPC alive; breaking the setup despawns it |

**Marker block (code).** Block components are declared in a block type under
`BlockEntity.Components`. The component registered as `SpawnMarkerBlock` has this shape:

```json
"BlockEntity": {
  "Components": {
    "SpawnMarkerBlock": {
      "Config": {
        "SpawnMarker": "MT_Fast_Respawn",
        "MarkerOffset": { "X": 0, "Y": 1, "Z": 0 }
      }
    }
  }
}
```

`SpawnMarker` is the marker asset to use; `MarkerOffset` is where, relative to the block, the marker
entity is created. This shape is taken from the server's codec. It has not been tested in game yet.

**Trigger interaction (code).** The interaction type `TriggerSpawnMarkers` triggers nearby
`ManualTrigger` markers:

| Field | Meaning |
|---|---|
| `MarkerType` | Which manual marker type to trigger (all manual markers if omitted) |
| `Range` | Search range |
| `Count` | Max number of markers to trigger |

Attached to a block or item interaction (for example opening a container), this is a natural fit
for "the chest turns into a mimic when opened". Not tested in game yet.

### Pitfalls

- If nothing spawns, check `/markertest flag` first. If both switches are on, restart the world.
- A marker more than `MaxDropHeight` above the ground does not spawn ground NPCs.
- Every entry needs the delay that matches `RealtimeRespawn`.
- A role name that does not exist makes the spawn fail with `NONEXISTENT_ROLE`.
- NPCs spawned by markers get their model scaled like any other NPC. A model with a scale other than
  `1` combined with a non-zero particle `PositionOffset` was the cause of the
  `PositionOffset ... is not finite` disconnects fixed in MimicTale 1.6.9; markers do not change that.

---

## Building from source

Requirements: JDK 25. Gradle is included as a wrapper. The Hytale server API is downloaded
automatically by the Gradle plugin; no game files need to be copied.

```bash
./gradlew build          # Windows: gradlew.bat build
```

The jar is written to `build/libs/MarkerTest-<version>.jar`.

The target game version is set in `gradle.properties` (`hytale_version`, `manifestServerVersion`).
The template's `./gradlew runServer` task can start a local server with the plugin (it may ask you
to authorize the server on first start).

### Publishing a release

Releases are built by GitHub Actions (`.github/workflows/release.yml`). Set `version` in
`gradle.properties`, commit, then push a tag with the same version:

```bash
git tag v0.2.0
git push origin v0.2.0
```

The workflow builds the project and creates a GitHub release for the tag with
`MarkerTest-<version>.jar` attached. It stops with an error if the tag does not match `version`.

---

## Project layout

```
.
├── build.gradle.kts            Gradle build (AzureDoom hytale-tools plugin)
├── settings.gradle.kts
├── gradle.properties           Mod id, version, target Hytale version
├── src/main/java/dev/markertest/
│   ├── MarkerTestPlugin.java
│   ├── MarkerTestCommand.java
│   ├── MarkerInfo.java
│   └── MarkerWatchSystem.java
├── src/main/resources/
│   ├── manifest.json           Generated/updated by the build from gradle.properties
│   └── Server/NPC/Spawn/Markers/MT_*.json
├── .github/workflows/gradle.yml  CI: checks that the project compiles
├── .github/workflows/release.yml Builds the jar and publishes a release for v* tags
└── LICENSE
```

---

## Known gaps

- Why no marker spawned in the very first session is unknown; a world restart fixed it.
- The game-time respawn of `MT_Skeleton_GameTime` was not waited out.
- The `SpawnMarkerBlock` component and the `TriggerSpawnMarkers` interaction are described from the
  server code only. A test block and a test item for them are the obvious next step.
- How marker entities are stored inside prefab files was not examined.
- Marker internals marked **(code)** are not official documentation and may change between game versions.

---

## Credits

- Project template: [HytaleModding plugin template](https://hytalemodding.dev/) with
  [AzureDoom's Hytale Gradle plugin](https://github.com/AzureDoom/Hytale-Gradle-Plugin).
- Official docs: [docs.hytale.com](https://docs.hytale.com/).
- Made by densnz. MIT License, see [LICENSE](LICENSE).
