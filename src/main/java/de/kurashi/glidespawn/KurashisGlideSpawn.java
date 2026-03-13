package de.kurashi.glidespawn;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.ChangeVelocityType;
import com.hypixel.hytale.protocol.MovementSettings;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.player.ClientMovement;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.handlers.game.GamePacketHandler;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class KurashisGlideSpawn extends JavaPlugin {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private GlideConfig config;
    private final ConcurrentHashMap<UUID, PlayerGlideState> states = new ConcurrentHashMap<>();

    // PacketFilter-Referenz fuer sauberes Deregistrieren beim Shutdown
    private com.hypixel.hytale.server.core.io.adapter.PacketFilter packetFilter;

    public KurashisGlideSpawn(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        loadConfig();

        getEventRegistry().registerGlobal(PlayerReadyEvent.class, this::onPlayerReady);
        getEventRegistry().register(PlayerDisconnectEvent.class, this::onPlayerDisconnect);
        getEventRegistry().registerGlobal(AddPlayerToWorldEvent.class, this::onAddToWorld);

        packetFilter = PacketAdapters.registerInbound((PacketHandler handler, Packet packet) -> {
            if (!(packet instanceof ClientMovement movPacket)) return;
            if (!(handler instanceof GamePacketHandler gpHandler)) return;
            handleMovement(gpHandler.getPlayerRef(), movPacket);
        });
    }

    @Override
    protected void start() {
        getLogger().at(Level.INFO).log(
            "KurashisGlideSpawn aktiv - Spawn: (%.0f, %.0f, %.0f) Radius: %.0f Welt: %s",
            config.spawnX, config.spawnY, config.spawnZ, config.activationRadius, config.survivalWorldName
        );
    }

    @Override
    public void shutdown() {
        if (packetFilter != null) {
            PacketAdapters.deregisterInbound(packetFilter);
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

        // Falls der Spieler beim Disconnect noch gleitet, MovementSettings aufraumen
        if (state != null && state.gliding) {
            UUID worldUuid = playerRef.getWorldUuid();
            if (worldUuid == null) return;
            World world = Universe.get().getWorld(worldUuid);
            if (world == null) return;
            world.execute(() -> deactivateGlide(playerRef));
        }
    }

    private void onAddToWorld(AddPlayerToWorldEvent event) {
        if (!config.teleportOnJoin) return;
        if (!config.survivalWorldName.equals(event.getWorld().getName())) return;

        Holder<EntityStore> holder = event.getHolder();
        PlayerRef playerRef = holder.getComponent(PlayerRef.getComponentType());
        if (playerRef == null) return;

        World world = event.getWorld();
        // Kurzes Delay (500ms) damit der Spieler vollstaendig in der Welt registriert ist
        HytaleServer.SCHEDULED_EXECUTOR.schedule(() ->
            world.execute(() -> teleportToSpawn(playerRef, world)),
            500, TimeUnit.MILLISECONDS
        );
    }

    // ──────────────────────────────────────────────────────────────────
    // Packet-Handling (laeuft auf IO-Thread via PacketAdapter)
    // ──────────────────────────────────────────────────────────────────

    private void handleMovement(PlayerRef playerRef, ClientMovement packet) {
        if (packet.movementStates == null || packet.absolutePosition == null) return;

        UUID uuid = playerRef.getUuid();
        PlayerGlideState state = states.get(uuid);
        if (state == null) return;

        boolean onGround = packet.movementStates.onGround
                || packet.movementStates.inFluid
                || packet.movementStates.climbing;

        // XZ-Distanz zum Spawn-Punkt pruefen
        double dx = packet.absolutePosition.x - config.spawnX;
        double dz = packet.absolutePosition.z - config.spawnZ;
        boolean inRadius = (dx * dx + dz * dz) <= (config.activationRadius * config.activationRadius);

        UUID worldUuid = playerRef.getWorldUuid();
        if (worldUuid == null) return;
        World world = Universe.get().getWorld(worldUuid);
        if (world == null) return;
        if (!config.survivalWorldName.equals(world.getName())) return;

        // Gleiten aktivieren: im Spawn-Radius, in der Luft, noch nicht am Gleiten
        if (inRadius && !onGround && !state.gliding) {
            state.gliding = true;
            world.execute(() -> activateGlide(playerRef));
        }

        // Boost: Spacebar gedrueckt (Flanke: war false, jetzt true) waehrend des Gleitens + Cooldown
        if (state.gliding && packet.movementStates.jumping && !state.wasJumping) {
            long now = System.currentTimeMillis();
            if (now - state.lastBoostTime >= config.boostCooldownMs) {
                state.lastBoostTime = now;
                world.execute(() -> applyBoost(playerRef));
            }
        }

        // Gleiten beenden: Spieler hat Boden beruehrt
        if (state.gliding && onGround) {
            state.gliding = false;
            world.execute(() -> deactivateGlide(playerRef));
        }

        state.wasJumping = packet.movementStates.jumping;
    }

    // ──────────────────────────────────────────────────────────────────
    // Spielmechaniken (laufen auf World-Thread via world.execute())
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

        MovementManager mm = store.getComponent(ref, MovementManager.getComponentType());
        if (mm == null) return;

        MovementSettings s = mm.getSettings();
        s.airDragMax = config.glideAirDragMax;
        s.airFrictionMax = config.glideAirFrictionMax;
        s.airControlMaxMultiplier = 6.0f;
        mm.update(playerRef.getPacketHandler());

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

    private void applyBoost(PlayerRef playerRef) {
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) return;
        Store<EntityStore> store = ref.getStore();

        HeadRotation headRot = store.getComponent(ref, HeadRotation.getComponentType());
        Velocity vel = store.getComponent(ref, Velocity.getComponentType());
        if (headRot == null || vel == null) return;

        // Vorwaertsrichtung aus Blickrichtung (Yaw) berechnen
        float yaw = headRot.getRotation().y;
        Vector3d forward = new Vector3d(0, 0, 1);
        forward.rotateY(yaw);
        forward.scale(config.boostForce);
        vel.addInstruction(forward, null, ChangeVelocityType.Add);
    }

    private void deactivateGlide(PlayerRef playerRef) {
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) return;
        Store<EntityStore> store = ref.getStore();

        MovementManager mm = store.getComponent(ref, MovementManager.getComponentType());
        if (mm == null) return;

        mm.applyDefaultSettings();
        mm.update(playerRef.getPacketHandler());
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
            getLogger().at(Level.INFO).log("Default-Config erstellt");
        } catch (IOException e) {
            getLogger().at(Level.WARNING).log("Config konnte nicht gespeichert werden: " + e.getMessage());
        }
    }
}
