package com.discoveryscreen.widget;

import java.util.ArrayDeque;

public final class StagedQueue {

    public interface CostGate {
        boolean shouldYield(long costMs);
    }

    private static final class Stage {
        final int generation;
        final long costMs;
        final Runnable body;

        Stage(int generation, long costMs, Runnable body) {
            this.generation = generation;
            this.costMs = costMs;
            this.body = body;
        }
    }

    private final ArrayDeque<Stage> mQueue = new ArrayDeque<>();

    public void offer(int generation, long costMs, Runnable body) {
        if (body == null) throw new NullPointerException("body == null");
        mQueue.addLast(new Stage(generation, costMs, body));
    }

    public Runnable takeReady(int currentGeneration, CostGate gate) {
        while (true) {
            Stage head = mQueue.peekFirst();
            if (head == null) return null;
            if (head.generation != currentGeneration) {
                mQueue.pollFirst();
                continue;
            }
            if (gate.shouldYield(head.costMs)) return null;
            return mQueue.pollFirst().body;
        }
    }

    public void clear() {
        mQueue.clear();
    }

    public boolean isEmpty() {
        return mQueue.isEmpty();
    }

    public int size() {
        return mQueue.size();
    }
}
