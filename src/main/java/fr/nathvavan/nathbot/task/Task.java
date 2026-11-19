package fr.nathvavan.nathbot.task;

import net.minecraft.client.Minecraft;

/**
 * Tache du bot. Une tache peut lancer une sous-tache (enfant) et reprendre la main quand elle finit.
 */
public abstract class Task {
    public enum Status { RUNNING, SUCCESS, FAILED }

    protected final Minecraft mc = Minecraft.getInstance();
    private Task child;
    protected String status = "";
    protected int ticks = 0;

    public abstract String name();

    /** Logique propre de la tache (appelee quand aucun enfant ne tourne). */
    protected abstract Status update();

    /** Appele quand un enfant se termine. */
    protected void onChildDone(Task child, Status result) {}

    /** Permet d'interrompre l'enfant (ex. on a trouve un minerai pendant l'exploration). */
    protected boolean shouldInterruptChild(Task child) { return false; }

    public final Status tick() {
        ticks++;
        if (child != null) {
            if (shouldInterruptChild(child)) {
                child.stop();
                Task c = child;
                child = null;
                onChildDone(c, Status.FAILED);
            } else {
                Status s = child.tick();
                if (s == Status.RUNNING) return Status.RUNNING;
                Task c = child;
                child = null;
                onChildDone(c, s);
            }
        }
        if (child != null) return Status.RUNNING;
        return update();
    }

    protected void run(Task t) {
        if (child != null) child.stop();
        child = t;
    }

    protected Task child() { return child; }

    /** Arret propre (relache les touches, annule les calculs...). */
    public void stop() {
        if (child != null) child.stop();
        child = null;
        onStop();
    }

    protected void onStop() {}

    public String describe() {
        String me = name() + (status.isEmpty() ? "" : " (" + status + ")");
        return child != null ? me + " > " + child.describe() : me;
    }
}
