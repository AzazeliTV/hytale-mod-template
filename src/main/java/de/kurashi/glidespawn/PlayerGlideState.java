package de.kurashi.glidespawn;

public class PlayerGlideState {

    // Gleitet der Spieler gerade?
    public volatile boolean gliding = false;

    // War die Sprung-Taste im letzten Paket gedrueckt? (fuer Flanken-Erkennung)
    public volatile boolean wasJumping = false;

    // Timestamp des letzten Boosts (fuer Cooldown)
    public volatile long lastBoostTime = 0;
}
