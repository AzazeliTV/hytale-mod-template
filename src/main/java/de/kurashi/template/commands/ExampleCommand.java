package de.kurashi.template.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Beispiel-Command als Starting-Point.
 * Registriert in TemplateMod.start() via getCommandRegistry().registerCommand(new ExampleCommand());
 *
 * Spieler-spezifische Commands: AbstractPlayerCommand (braucht Player)
 * Konsolen-erlaubte Commands: AbstractCommand (funktioniert auch von Server-Konsole)
 *
 * SubCommands via addSubCommand(new SubCmd()) im Constructor.
 */
public class ExampleCommand extends AbstractPlayerCommand {

    public ExampleCommand() {
        super("example", "Beispiel-Command des Template-Mods");
        requirePermission("template.use");
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        ctx.sendMessage(Message.raw("Hallo " + playerRef.getUsername() + "!").color("#55FF55"));
    }
}
