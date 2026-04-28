package de.kurashi.template;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import de.kurashi.template.commands.ExampleCommand;
import de.kurashi.template.events.TemplateEventHandler;

import javax.annotation.Nonnull;

/**
 * Hytale Mod Template.
 *
 * Lifecycle:
 *   1. Constructor  - JAR wird geladen, super(init) aufrufen
 *   2. setup()      - Events, ECS-Systeme registrieren (Server noch nicht bereit)
 *   3. start()      - Server bereit, Commands registrieren, Scheduler starten
 *   4. shutdown()   - Server stoppt, Ressourcen freigeben (DB schliessen, Tasks canceln)
 *
 * Package-Layout:
 *   de.kurashi.template          — Plugin Entry
 *   de.kurashi.template.events   — Event-Handler
 *   de.kurashi.template.commands — Command-Klassen
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
        TemplateEventHandler.registerAll(getEventRegistry());

        // ECS-System registrieren:
        // getEntityStoreRegistry().registerSystem(new MeinSystem());

        LOGGER.atInfo().log("[TemplateMod] Setup abgeschlossen");
    }

    @Override
    protected void start() {
        getCommandRegistry().registerCommand(new ExampleCommand());

        LOGGER.atInfo().log("[TemplateMod] Gestartet");
    }

    @Override
    public void shutdown() {
        instance = null;
        LOGGER.atInfo().log("[TemplateMod] Heruntergefahren");
    }
}
