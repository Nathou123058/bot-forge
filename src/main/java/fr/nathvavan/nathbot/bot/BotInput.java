package fr.nathvavan.nathbot.bot;

/** Entrees de deplacement que le bot impose au joueur (remises a zero chaque tick). */
public final class BotInput {
    public float forward;
    public float strafe;
    public boolean jump;
    public boolean sneak;
    public boolean sprint;
    /** true si une tache ou une fonction a demande le controle ce tick. */
    public boolean active;

    public void reset() {
        forward = 0;
        strafe = 0;
        jump = false;
        sneak = false;
        sprint = false;
        active = false;
    }
}
