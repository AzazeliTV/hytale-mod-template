package de.kurashi.glidespawn;

import com.google.gson.Gson;
import de.kurashi.lib.util.JsonUtil;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.ChangeVelocityType;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.player.ClientMovement;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.handlers.game.GamePacketHandler;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.RespawnSystems;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;

import com.google.gson.reflect.TypeToken;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class KurashisGlideSpawn extends JavaPlugin {

    private static final Gson GSON = JsonUtil.GSON;

    private GlideConfig config;
    private final ConcurrentHashMap<UUID, PlayerGlideState> states = new ConcurrentHashMap<>();

    private final Set<UUID> firstJoinedPlayers = ConcurrentHashMap.newKeySet();
    private Path firstJoinedFile;

    private com.hypixel.hytale.server.core.io.adapter.PacketFilter packetFilter;

    public KurashisGlideSpawn(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        loadConfig();
        firstJoinedFile = getDataDirectory().resolve("firstjoined.json");
        loadFirstJoined();

        getEventRegistry().registerGlobal(PlayerReadyEvent.class, this::onPlayerReady);
        getEventRegistry().register(PlayerDisconnectEvent.class, this::onPlayerDisconnect);
        getEventRegistry().registerGlobal(AddPlayerToWorldEvent.class, this::onAddToWorld);

        packetFilter = PacketAdapters.registerInbound((PacketHandler handler, Packet packet) -> {
            if (!(packet instanceof ClientMovement movPacket)) return;
            if (!(handler instanceof GamePacketHandler gpHandler)) return;
            handleMovement(gpHandler.getPlayerRef(), movPacket);
        });

        getEntityStoreRegistry().registerSystem(new GlideRespawnSystem());
    }

    @Override
    protected void start() {
        getLogger().at(Level.INFO).log(
            "KurashisGlideSpawn v3.1 aktiv - Gleitflug-Modus - Spawn: (%.0f, %.0f, %.0f) Radius: %.0f Welt: %s",
            config.spawnX, config.spawnY, config.spawnZ, config.activationRadius, config.survivalWorldName
        );
    }

    @Override
    public void shutdown() {
        if (packetFilter != null) {
            PacketAdapters.deregisterInbound(packetFilter);
        }
        for (PlayerGlideState state : states.values()) {
            stopDescentTask(state);
        }
        states.clear();
    }

    // ──────────────────────────────────────────────────────────────────
    // Events
    // ──────────────────────────────────────────────────────────────────

    private void onPlayerReady(PlayerReadyEvent event) {
        UUID uuid = event.getPlayer().getPlayerRef().getUuid();
        states.put(uuid, new PlayerGlideState());
    }

    private void onPlayerDisconnect(PlayerDisconnectEvent event) {
        PlayerRef playerRef = event.getPlayerRef();
        PlayerGlideState state = states.remove(playerRef.getUuid());

        if (state != null && state.gliding) {
            UUID worldUuid = playerRef.getWorldUuid();
            if (worldUuid == null) return;
            World world = Universe.get().getWorld(worldUuid);
            if (world == null) return;
            world.execute(() -> deactivateGlide(playerRef));
        }
    }

    private void onAddToWorld(AddPlayerToWorldEvent event) {
        if (!config.survivalWorldName.equals(event.getWorld().getName())) return;

        Holder<EntityStore> holder = event.getHolder();
        PlayerRef playerRef = holder.getComponent(PlayerRef.getComponentType());
        if (playerRef == null) return;

        UUID uuid = playerRef.getUuid();

        PlayerGlideState state = states.get(uuid);
        if (state != null) {
            state.stuckYTicks = 0;
            state.lastY = -1;
        }

        boolean shouldTeleport = config.teleportOnJoin
                || (config.teleportOnFirstJoin && firstJoinedPlayers.add(uuid));

        if (!shouldTeleport) return;

        if (config.teleportOnFirstJoin && !config.teleportOnJoin) {
            saveFirstJoinedAsync();
        }

        World world = event.getWorld();
        HytaleServer.SCHEDULED_EXECUTOR.schedule(() ->
            world.execute(() -> teleportToSpawn(playerRef, world)),
            500, TimeUnit.MILLISECONDS
        );
    }

    private void loadFirstJoined() {
        if (!Files.exists(firstJoinedFile)) return;
        try (Reader r = Files.newBufferedReader(firstJoinedFile, StandardCharsets.UTF_8)) {
            Type listType = new TypeToken<List<String>>() {}.getType();
            List<String> uuids = GSON.fromJson(r, listType);
            if (uuids != null) {
                for (String s : uuids) {
                    try { firstJoinedPlayers.add(UUID.fromString(s)); }
                    catch (IllegalArgumentException ignored) {}
                }
            }
            getLogger().at(Level.INFO).log("firstjoined.json geladen: %d Eintraege", firstJoinedPlayers.size());
        } catch (IOException e) {
            getLogger().at(Level.WARNING).log("firstjoined.json konnte nicht gelesen werden: " + e.getMessage());
        }
    }

    private void saveFirstJoinedAsync() {
        List<String> snapshot = new ArrayList<>();
        for (UUID uuid : firstJoinedPlayers) snapshot.add(uuid.toString());
        HytaleServer.SCHEDULED_EXECUTOR.execute(() -> {
            try {
                Files.createDirectories(firstJoinedFile.getParent());
                try (Writer w = Files.newBufferedWriter(firstJoinedFile, StandardCharsets.UTF_8)) {
                    GSON.toJson(snapshot, w);
                }
            } catch (IOException e) {
                getLogger().at(Level.WARNING).log("firstjoined.json konnte nicht gespeichert werden: " + e.getMessage());
            }
        });
    }

    // ──────────────────────────────────────────────────────────────────
    // Packet-Handling (IO-Thread)
    // ──────────────────────────────────────────────────────────────────

    private void handleMovement(PlayerRef playerRef, ClientMovement packet) {
        if (packet.movementStates == null || packet.absolutePosition == null) return;

        UUID uuid = playerRef.getUuid();
        PlayerGlideState state = states.get(uuid);
        if (state == null) return;

        // World-Check ZUERST
        UUID worldUuid = playerRef.getWorldUuid();
        if (worldUuid == null) return;
        World world = Universe.get().getWorld(worldUuid);
        if (world == null) return;
        if (!config.survivalWorldName.equals(world.getName())) return;

        double playerX = packet.absolutePosition.x;
        double playerY = packet.absolutePosition.y;
        double playerZ = packet.absolutePosition.z;

        // Y-Tracking fuer Landungserkennung
        if (state.lastY >= 0 && Math.abs(playerY - state.lastY) < config.yStuckThreshold) {
            state.stuckYTicks++;
        } else {
            state.stuckYTicks = 0;
        }
        state.lastY = playerY;

        // XZ-Distanz zum Spawn
        double dx = playerX - config.spawnX;
        double dz = playerZ - config.spawnZ;
        double distSq = dx * dx + dz * dz;
        boolean inRadius = distSq <= (config.activationRadius * config.activationRadius);

        // Glide-Zone = Y-Band unter der Spawn-Insel
        double zoneTop = config.spawnY - config.glideZoneTop;
        double zoneBottom = config.spawnY - config.glideZoneBottom;
        boolean inGlideZone = playerY < zoneTop && playerY > zoneBottom;

        // Cooldown
        long cooldownRemaining = config.reactivationCooldownMs - (System.currentTimeMillis() - state.lastGlideEndTime);
        boolean cooldownOk = cooldownRemaining <= 0;

        // Faellt der Spieler gerade? (Y sinkt)
        boolean isFalling = state.lastY >= 0 && (state.lastY - playerY) >= config.minFallSpeed;

        // DEBUG: Alle 100 Pakete loggen warum Aktivierung nicht klappt (nur wenn nicht gleitend)
        if (!state.gliding) {
            state.debugCounter++;
            if (state.debugCounter >= 100) {
                state.debugCounter = 0;
                getLogger().at(Level.INFO).log(
                    "[DEBUG] pos=(%.1f, %.1f, %.1f) dist=%.1f inRadius=%b inZone=%b falling=%b(dY=%.2f) cooldown=%dms world=%s",
                    playerX, playerY, playerZ, Math.sqrt(distSq),
                    inRadius, inGlideZone, isFalling,
                    state.lastY >= 0 ? (state.lastY - playerY) : 0.0,
                    cooldownRemaining, world.getName()
                );
            }
        }

        // Aktivierung: nur wenn im Radius, in der Zone, UND tatsaechlich im Freifall
        if (inRadius && !state.gliding && inGlideZone && cooldownOk && isFalling) {
            state.gliding = true;
            state.stuckYTicks = 0;
            state.boostUsed = false;
            state.glideStartTime = System.currentTimeMillis();
            getLogger().at(Level.INFO).log("FLY ON pos=(%.1f, %.1f, %.1f) falling dY=%.2f %s",
                playerX, playerY, playerZ, state.lastY - playerY, uuid);
            world.execute(() -> activateGlide(playerRef));
        }

        // Einmaliger Boost: Spacebar-Flanke waehrend des Flugs
        if (state.gliding && config.boostEnabled && !state.boostUsed
                && packet.movementStates.jumping && !state.wasJumping) {
            state.boostUsed = true;
            world.execute(() -> applyBoost(playerRef));
        }

        // Max-Timeout
        if (state.gliding && System.currentTimeMillis() - state.glideStartTime > config.maxGlideDurationMs) {
            getLogger().at(Level.INFO).log("FLY TIMEOUT %s", uuid);
            resetGlideState(playerRef);
            world.execute(() -> deactivateGlide(playerRef));
            state.wasJumping = packet.movementStates.jumping;
            return;
        }

        // Landung: Y hat sich N Pakete nicht geaendert
        if (state.gliding && state.stuckYTicks >= config.landingStuckTicks) {
            long glideDuration = System.currentTimeMillis() - state.glideStartTime;
            if (glideDuration >= config.minGlideDurationMs) {
                getLogger().at(Level.INFO).log("FLY OFF y=%.1f (landed, stuck %d, %.1fs) %s",
                    playerY, state.stuckYTicks, glideDuration / 1000.0, uuid);
                resetGlideState(playerRef);
                world.execute(() -> deactivateGlide(playerRef));
            }
        }

        state.wasJumping = packet.movementStates.jumping;
    }

    // ──────────────────────────────────────────────────────────────────
    // Spielmechaniken (World-Thread)
    // ──────────────────────────────────────────────────────────────────

    private void teleportToSpawn(PlayerRef playerRef, World world) {
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) return;
        Store<EntityStore> store = ref.getStore();

        Transform spawnTransform = new Transform(
            new Vector3d(config.spawnX, config.spawnY, config.spawnZ),
            new Vector3f(0, 0, 0)
        );
        Teleport teleport = Teleport.createForPlayer(world, spawnTransform);
        store.addComponent(ref, Teleport.getComponentType(), teleport);
    }

    private void activateGlide(PlayerRef playerRef) {
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) return;
        Store<EntityStore> store = ref.getStore();

        // canFly aktivieren + Fluggeschwindigkeiten setzen
        MovementManager mm = store.getComponent(ref, MovementManager.getComponentType());
        if (mm != null) {
            mm.getSettings().canFly = true;
            mm.getSettings().horizontalFlySpeed = config.horizontalFlySpeed;
            mm.getSettings().verticalFlySpeed = config.verticalFlySpeed;
            mm.update(playerRef.getPacketHandler());
        }

        // Freifall stoppen - Velocity auf sanften Gleitflug setzen
        // Ohne das faellt der Spieler mit voller Geschwindigkeit weiter trotz canFly
        Velocity vel = store.getComponent(ref, Velocity.getComponentType());
        if (vel != null) {
            vel.addInstruction(new Vector3d(0, 0, 0), null, ChangeVelocityType.Set);
        }

        // Unverwundbar waehrend des Flugs
        store.putComponent(ref, Invulnerable.getComponentType(), Invulnerable.INSTANCE);

        // Periodischer Abwaerts-Nudge fuer Gleitflug-Sinkrate
        startDescentTask(playerRef);

        // Benachrichtigung
        if (config.notificationTitle != null && !config.notificationTitle.isEmpty()) {
            Message subtitle = (config.notificationSubtitle != null && !config.notificationSubtitle.isEmpty())
                ? Message.raw(config.notificationSubtitle) : null;
            NotificationUtil.sendNotification(
                playerRef.getPacketHandler(),
                Message.raw(config.notificationTitle),
                subtitle,
                NotificationStyle.Default
            );
        }
    }

    private void startDescentTask(PlayerRef playerRef) {
        PlayerGlideState state = states.get(playerRef.getUuid());
        if (state == null) return;

        stopDescentTask(state);

        state.descentTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
            if (!state.gliding) {
                stopDescentTask(state);
                return;
            }
            UUID wUuid = playerRef.getWorldUuid();
            if (wUuid == null) return;
            World w = Universe.get().getWorld(wUuid);
            if (w == null) return;
            w.execute(() -> applyDescent(playerRef));
        }, config.descentIntervalMs, config.descentIntervalMs, TimeUnit.MILLISECONDS);
    }

    private void stopDescentTask(PlayerGlideState state) {
        ScheduledFuture<?> task = state.descentTask;
        if (task != null) {
            task.cancel(false);
            state.descentTask = null;
        }
    }

    private void applyDescent(PlayerRef playerRef) {
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) return;
        Store<EntityStore> store = ref.getStore();

        Velocity vel = store.getComponent(ref, Velocity.getComponentType());
        if (vel == null) return;

        vel.addInstruction(new Vector3d(0, -config.descentSpeed, 0), null, ChangeVelocityType.Add);
    }

    private void applyBoost(PlayerRef playerRef) {
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) return;
        Store<EntityStore> store = ref.getStore();

        HeadRotation headRot = store.getComponent(ref, HeadRotation.getComponentType());
        Velocity vel = store.getComponent(ref, Velocity.getComponentType());
        if (headRot == null || vel == null) return;

        Vector3d dir = headRot.getDirection();
        Vector3d boostVel = new Vector3d(
            dir.getX() * config.boostForce,
            dir.getY() * config.boostForce,
            dir.getZ() * config.boostForce
        );
        vel.addInstruction(boostVel, null, ChangeVelocityType.Add);
    }

    private void deactivateGlide(PlayerRef playerRef) {
        // Descent-Task stoppen
        PlayerGlideState state = states.get(playerRef.getUuid());
        if (state != null) {
            stopDescentTask(state);
        }

        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) return;
        Store<EntityStore> store = ref.getStore();

        // MovementSettings auf Defaults zuruecksetzen (canFly = false)
        MovementManager mm = store.getComponent(ref, MovementManager.getComponentType());
        if (mm != null) {
            mm.applyDefaultSettings();
            mm.update(playerRef.getPacketHandler());
        }

        // Invulnerable nach kurzer Verzoegerung entfernen
        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            PlayerGlideState s = states.get(playerRef.getUuid());
            if (s != null && s.gliding) return;
            UUID wUuid = playerRef.getWorldUuid();
            if (wUuid == null) return;
            World w = Universe.get().getWorld(wUuid);
            if (w == null) return;
            w.execute(() -> {
                Ref<EntityStore> r = playerRef.getReference();
                if (r == null || !r.isValid()) return;
                r.getStore().tryRemoveComponent(r, Invulnerable.getComponentType());
            });
        }, 500, TimeUnit.MILLISECONDS);
    }

    // ──────────────────────────────────────────────────────────────────
    // Glide State Management
    // ──────────────────────────────────────────────────────────────────

    private void resetGlideState(PlayerRef playerRef) {
        PlayerGlideState state = states.get(playerRef.getUuid());
        if (state == null) return;
        stopDescentTask(state);
        state.gliding = false;
        state.stuckYTicks = 0;
        state.lastY = -1;
        state.boostUsed = false;
        state.lastGlideEndTime = System.currentTimeMillis();
    }

    // ──────────────────────────────────────────────────────────────────
    // ECS System: Tod/Respawn-Handling
    // ──────────────────────────────────────────────────────────────────

    private class GlideRespawnSystem extends RespawnSystems.OnRespawnSystem {

        @Nonnull
        @Override
        public Query<EntityStore> getQuery() {
            return Player.getComponentType();
        }

        @Override
        public void onComponentAdded(@Nonnull Ref<EntityStore> ref, @Nonnull DeathComponent component,
                                      @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef == null) return;

            PlayerGlideState state = states.get(playerRef.getUuid());
            if (state == null || !state.gliding) return;

            getLogger().at(Level.INFO).log("FLY DEATH %s", playerRef.getUuid());
            resetGlideState(playerRef);
        }

        @Override
        public void onComponentRemoved(@Nonnull Ref<EntityStore> ref, @Nonnull DeathComponent component,
                                        @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef == null) return;

            resetGlideState(playerRef);

            // MovementSettings zuruecksetzen + Invulnerable entfernen
            MovementManager mm = store.getComponent(ref, MovementManager.getComponentType());
            if (mm != null) {
                mm.applyDefaultSettings();
                mm.update(playerRef.getPacketHandler());
            }
            commandBuffer.tryRemoveComponent(ref, Invulnerable.getComponentType());

            getLogger().at(Level.INFO).log("FLY RESPAWN %s - Settings zurueckgesetzt", playerRef.getUuid());
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // Config
    // ──────────────────────────────────────────────────────────────────

    private void loadConfig() {
        Path configFile = getDataDirectory().resolve("glidespawn.json");
        if (Files.exists(configFile)) {
            try (Reader r = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
                config = GSON.fromJson(r, GlideConfig.class);
                getLogger().at(Level.INFO).log("Config geladen");
            } catch (IOException e) {
                getLogger().at(Level.WARNING).log("Config-Fehler, nutze Defaults: " + e.getMessage());
                config = new GlideConfig();
            }
            saveConfig(configFile);
        } else {
            config = new GlideConfig();
            saveConfig(configFile);
        }
    }

    private void saveConfig(Path configFile) {
        try {
            Files.createDirectories(configFile.getParent());
            try (Writer w = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
                GSON.toJson(config, w);
            }
        } catch (IOException e) {
            getLogger().at(Level.WARNING).log("Config konnte nicht gespeichert werden: " + e.getMessage());
        }
    }
}
