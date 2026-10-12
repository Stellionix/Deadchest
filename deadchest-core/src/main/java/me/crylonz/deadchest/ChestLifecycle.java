package me.crylonz.deadchest;

import java.util.concurrent.CompletableFuture;

/** Durable intent before effects; completion follows the queued core SQL deletion. */
public final class ChestLifecycle {
    public enum Outcome { RECOVERED, EXPIRED, DROPPED, DELETED, GIVEBACK }
    public interface Observer {
        void created(ChestData chest);
        CompletableFuture<Void> before(ChestData snapshot, Outcome outcome, String actor);
        void after(ChestData snapshot, Outcome outcome, String actor);
        default CompletableFuture<Void> reconcile(ChestData snapshot, String state, String actor) {
            return CompletableFuture.completedFuture(null);
        }
    }
    private static volatile Observer observer;
    public static boolean enabled() { return observer != null; }
    public static void observe(Observer value) { observer = value; }
    public static CompletableFuture<Void> reconcile(ChestData chest, String state, String actor) {
        Observer current=observer;
        return current==null ? CompletableFuture.completedFuture(null) : current.reconcile(new ChestData(chest),state,actor);
    }
    public static void created(ChestData chest) {
        Observer current = observer;
        if (current != null) current.created(new ChestData(chest));
    }
    public static boolean transition(ChestData chest, Outcome outcome, String actor, Runnable effects) {
        if (observer == null) { effects.run(); return true; }
        return transitionAsync(chest, outcome, actor, () -> {
            effects.run(); return CompletableFuture.completedFuture(null);
        });
    }
    public static boolean transitionAsync(ChestData chest, Outcome outcome, String actor,
            java.util.function.Supplier<CompletableFuture<Void>> effects) {
        if (!chest.claimTransition()) return false;
        Observer current = observer;
        ChestData snapshot = new ChestData(chest);
        CompletableFuture<Void> ready = current == null ? CompletableFuture.completedFuture(null)
                : current.before(snapshot, outcome, actor);
        ready.whenComplete((ignored, error) -> {
            if (error != null) {
                DeadChestLoader.log.severe("DeadChest transition suspended: " + chest.getId() + " " + error);
                return;
            }
            DeadChestLoader.getSchedulerAdapter().runAtLocation(chest.getChestLocation(), () -> {
                try {
                    effects.get().whenComplete((result, failure) -> {
                        if (failure != null) {
                            DeadChestLoader.log.severe("DeadChest transition requires review: " + chest.getId() + " " + failure);
                        } else {
                            DeadChestLoader.sqlExecutor.runAsync(() -> {
                                if (outcome == Outcome.RECOVERED && RecoveryJournal.active().pending(snapshot.getId())) {
                                    try (java.sql.PreparedStatement q = DeadChestLoader.db.connection().prepareStatement("SELECT 1 FROM chest_data WHERE chest_uuid=?")) {
                                        q.setString(1, snapshot.getId().toString());
                                        try (java.sql.ResultSet r = q.executeQuery()) {
                                            if (r.next()) throw new IllegalStateException("Chest deletion not committed");
                                        }
                                        RecoveryJournal.active().complete(snapshot.getId());
                                    } catch (Exception ex) {
                                        DeadChestLoader.log.severe("DeadChest recovery requires REVIEW: " + snapshot.getId() + " " + ex);
                                        return;
                                    }
                                }
                                if (current != null) current.after(snapshot, outcome, actor);
                            });
                        }
                    });
                } catch (Exception ex) {
                    DeadChestLoader.log.severe("DeadChest transition requires review: " + chest.getId() + " " + ex);
                }
            });
        });
        return true;
    }
    private ChestLifecycle() {}
}
