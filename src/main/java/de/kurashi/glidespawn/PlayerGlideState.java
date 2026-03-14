package de.kurashi.glidespawn;

import java.util.concurrent.ScheduledFuture;

public class PlayerGlideState {

    // Gleitet der Spieler gerade?
    public volatile boolean gliding = false;

    // War die Sprung-Taste im letzten Paket gedrueckt? (fuer Flanken-Erkennung)
    public volatile boolean wasJumping = false;

    // Timestamp des letzten Boosts (fuer Cooldown)
    public volatile long lastBoostTime = 0;

    // Y-Position beim letzten Bodenkontakt (fuer Fallhoehenberechnung)
    public volatile double jumpStartY = Double.NaN;

    // War der letzte Bodenkontakt im Spawn-Radius? (Gleiten nur nach Absprung von der Insel)
    public volatile boolean cameFromSpawnRadius = false;

    // Laufender Glide-Tick-Task (periodischer Vorwaertsimpuls)
    public volatile ScheduledFuture<?> glideTask = null;
}
