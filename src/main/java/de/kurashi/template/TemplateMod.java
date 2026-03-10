package de.kurashi.template;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;

/**
 * Hytale Mod Template.
 *
 * Lifecycle:
 *   1. Constructor  - JAR wird geladen, super(init) aufrufen
 *   2. setup()      - Events, ECS-Systeme registrieren (Server noch nicht bereit)
 *   3. start()      - Server bereit, Commands registrieren, Scheduler starten
 *   4. shutdown()   - Server stoppt, Ressourcen freigeben (DB schliessen, Tasks canceln)
 */
public class TemplateMod extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static TemplateMod instance;

    public TemplateMod(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
    }

    public static TemplateMod getInstance() {
        return instance;
    }

    @Override
    protected void setup() {
        // Events registrieren (Server akzeptiert noch keine Spieler)
        // PlayerReadyEvent ist String-keyed -> registerGlobal()
        getEventRegistry().registerGlobal(PlayerReadyEvent.class, this::onPlayerReady);
        // PlayerDisconnectEvent ist Void-keyed -> register()
        getEventRegistry().register(PlayerDisconnectEvent.class, this::onPlayerDisconnect);

        // ECS-System registrieren:
        // getEntityStoreRegistry().registerSystem(new MeinSystem());

        LOGGER.atInfo().log("[TemplateMod] Setup abgeschlossen");
    }

    @Override
    protected void start() {
        // Server ist bereit - Commands, Integrationen, Scheduler hier starten
        // getCommandRegistry().registerCommand(new MeinCommand(this));

        LOGGER.atInfo().log("[TemplateMod] Gestartet");
    }

    @Override
    public void shutdown() {
        // Aufraumen: DB schliessen, Scheduler stoppen, Referenzen freigeben
        instance = null;
        LOGGER.atInfo().log("[TemplateMod] Heruntergefahren");
    }

    // --- Event Handlers ---

    @SuppressWarnings("removal") // Player.getPlayerRef() ist deprecated aber ohne Ersatz
    private void onPlayerReady(PlayerReadyEvent event) {
        Player player = event.getPlayer();
        PlayerRef playerRef = player.getPlayerRef();
        String name = playerRef.getUsername();

        LOGGER.atInfo().log("[TemplateMod] Spieler beigetreten: %s", name);
        playerRef.sendMessage(Message.raw("Willkommen, " + name + "!").color("#55FF55"));
    }

    private void onPlayerDisconnect(PlayerDisconnectEvent event) {
        PlayerRef playerRef = event.getPlayerRef();
        LOGGER.atInfo().log("[TemplateMod] Spieler verlassen: %s", playerRef.getUsername());
    }
}
