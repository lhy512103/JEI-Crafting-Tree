package com.lhy.jeict.client;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import com.lhy.jeict.planning.InventorySnapshot;
import com.lhy.jeict.planning.PlanTarget;
import com.lhy.jeict.planning.RecipePlanResult;
import com.lhy.jeict.planning.RecipePlanSolver;
import com.lhy.jeict.planning.SubstitutionStrategy;

import net.minecraft.client.Minecraft;

/**
 * Runs immutable planning snapshots away from the render thread. A monotonically increasing
 * generation prevents stale work from replacing a newer tree or inventory result.
 */
public final class AsyncRecipePlanService implements AutoCloseable {
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "JEICT Recipe Planner");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicLong generation = new AtomicLong();
    private volatile Future<?> pending;

    public long submit(List<PlanTarget> targets, InventorySnapshot inventory, SubstitutionStrategy strategy,
            String preferredNamespace, Consumer<RecipePlanResult> callback) {
        long request = generation.incrementAndGet();
        Future<?> previous = pending;
        if (previous != null) previous.cancel(true);
        List<PlanTarget> immutableTargets = List.copyOf(targets);
        InventorySnapshot immutableInventory = inventory == null ? InventorySnapshot.EMPTY : inventory;
        pending = executor.submit(() -> {
            RecipePlanResult result = new RecipePlanSolver(strategy, preferredNamespace)
                    .solve(immutableTargets, immutableInventory);
            if (Thread.currentThread().isInterrupted() || generation.get() != request) return;
            Minecraft.getInstance().execute(() -> {
                if (generation.get() == request) callback.accept(result);
            });
        });
        return request;
    }

    public void cancel() {
        generation.incrementAndGet();
        Future<?> task = pending;
        if (task != null) task.cancel(true);
        pending = null;
    }

    @Override
    public void close() {
        cancel();
        executor.shutdownNow();
    }
}
