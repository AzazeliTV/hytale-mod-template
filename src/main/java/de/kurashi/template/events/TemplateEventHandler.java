package de.kurashi.template.events;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.event.IEventRegistry;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;

public final class TemplateEventHandler {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private TemplateEventHandler() {}

    public static void registerAll(IEventRegistry registry) {
        registry.registerGlobal(PlayerReadyEvent.class, TemplateEventHandler::onPlayerReady);
        registry.register(PlayerDisconnectEvent.class, TemplateEventHandler::onPlayerDisconnect);
    }

    @SuppressWarnings("removal")
    private static void onPlayerReady(PlayerReadyEvent event) {
        Player player = event.getPlayer();
        PlayerRef playerRef = player.getPlayerRef();
        String name = playerRef.getUsername();
        LOGGER.atInfo().log("[TemplateMod] Spieler beigetreten: %s", name);
        playerRef.sendMessage(Message.raw("Willkommen, " + name + "!").color("#55FF55"));
    }

    private static void onPlayerDisconnect(PlayerDisconnectEvent event) {
        PlayerRef playerRef = event.getPlayerRef();
        LOGGER.atInfo().log("[TemplateMod] Spieler verlassen: %s", playerRef.getUsername());
    }
}
