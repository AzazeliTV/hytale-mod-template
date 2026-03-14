package de.kurashi.glidespawn;

public class GlideConfig {

    // Koordinaten der Spawn-Insel
    public double spawnX = 0;
    public double spawnY = 200;
    public double spawnZ = 0;

    // XZ-Radius um den Spawn in dem Gleiten aktiviert wird
    public double activationRadius = 50;

    // Name der Survival-Welt
    public String survivalWorldName = "survival";

    // Spieler beim ERSTEN Betreten der Survival-Welt auf die Insel teleportieren?
    public boolean teleportOnFirstJoin = true;

    // Spieler bei JEDEM Betreten der Survival-Welt auf die Insel teleportieren?
    public boolean teleportOnJoin = false;

    // MovementSettings fuer den Glide-Effekt
    // Normal: airDragMax=0.995, airFrictionMax=0.045
    public float glideAirDragMax = 0.9998f;
    public float glideAirFrictionMax = 0.003f;

    // Mindest-Fallhoehe (in Bloecken) bevor Gleiten aktiviert wird.
    // Verhindert Aktivierung beim normalen Huepfen (~1-2 Bloecke).
    // Ein echter Sprung von der Spawn-Insel faellt deutlich mehr.
    public double minFallHeight = 5.0;

    // Boost-Kraft (Spacebar waehrend Gleiten)
    public double boostForce = 18.0;

    // Cooldown zwischen zwei Boosts in Millisekunden
    public long boostCooldownMs = 3000;

    // Benachrichtigung wenn Gleiten aktiviert wird (leer lassen = deaktiviert)
    public String notificationTitle = "Gleiten aktiv";
    public String notificationSubtitle = "Leertaste für Boost!";
}
