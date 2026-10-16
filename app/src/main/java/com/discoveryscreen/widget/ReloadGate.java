package com.discoveryscreen.widget;

public final class ReloadGate {
    private ReloadGate() {}

    public enum RequestDecision { RUN_NOW, DEFERRED }
    public enum SettleAction { NOTHING, FLUSH_NORMAL, FLUSH_FORCE }

    public static final class Gate {
        private boolean swipeActive;
        private boolean pending;
        private boolean pendingForce;

        public RequestDecision onReloadRequest(boolean force) {
            if (swipeActive) {
                pending = true;
                if (force) pendingForce = true;
                return RequestDecision.DEFERRED;
            }
            return RequestDecision.RUN_NOW;
        }

        public void onSwipeStart() {
            swipeActive = true;
        }

        public SettleAction onSettled(boolean opened) {
            swipeActive = false;
            if (!pending) return SettleAction.NOTHING;
            boolean f = pendingForce;
            pending = false;
            pendingForce = false;
            if (!opened) return SettleAction.NOTHING;
            return f ? SettleAction.FLUSH_FORCE : SettleAction.FLUSH_NORMAL;
        }

        public void reset() {
            swipeActive = false;
            pending = false;
            pendingForce = false;
        }

        public boolean isSwipeActive() {
            return swipeActive;
        }

        public boolean hasPending() {
            return pending;
        }
    }
}
