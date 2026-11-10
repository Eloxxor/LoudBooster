package de.loudbooster.app;

final class BoostGain {
    static final int MAX_STEPS = 12;
    static final int INITIAL_STEPS = 2;

    private BoostGain() {}

    static int toMillibels(int steps) {
        return Math.max(0, Math.min(steps, MAX_STEPS)) * 50;
    }
}
