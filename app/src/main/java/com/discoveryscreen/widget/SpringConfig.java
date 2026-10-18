package com.discoveryscreen.widget;

public final class SpringConfig {
    public static final float DEFAULT_STIFFNESS = 1500.0f;
    public static final float DEFAULT_DAMPING_RATIO = 0.5f;

    public static final float SNAP_BACK_STIFFNESS = 200.0f;
    public static final float SNAP_BACK_DAMPING_RATIO = 0.75f;

    public static final SpringConfig DEFAULT =
            new SpringConfig(DEFAULT_STIFFNESS, DEFAULT_DAMPING_RATIO);

    public static final SpringConfig SNAP_BACK =
            new SpringConfig(SNAP_BACK_STIFFNESS, SNAP_BACK_DAMPING_RATIO);

    public static final float UNSET_POSITION = Float.NaN;

    public final float stiffness;
    public final float dampingRatio;
    public final float startVelocity;
    public final float finalPosition;

    public SpringConfig(float stiffness, float dampingRatio) {
        this(stiffness, dampingRatio, 0.0f, UNSET_POSITION);
    }

    public SpringConfig(float stiffness, float dampingRatio, float startVelocity, float finalPosition) {
        if (stiffness <= 0.0f) {
            throw new IllegalArgumentException("Spring stiffness constant must be positive.");
        }
        if (dampingRatio < 0.0f) {
            throw new IllegalArgumentException("Damping ratio must be non-negative");
        }
        this.stiffness = stiffness;
        this.dampingRatio = dampingRatio;
        this.startVelocity = startVelocity;
        this.finalPosition = finalPosition;
    }

    public SpringConfig withStartVelocity(float startVelocity) {
        return new SpringConfig(stiffness, dampingRatio, startVelocity, finalPosition);
    }

    public SpringConfig withFinalPosition(float finalPosition) {
        return new SpringConfig(stiffness, dampingRatio, startVelocity, finalPosition);
    }

    public boolean hasFinalPosition() {
        return !Float.isNaN(finalPosition);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SpringConfig)) {
            return false;
        }
        SpringConfig other = (SpringConfig) o;
        return Float.compare(stiffness, other.stiffness) == 0
                && Float.compare(dampingRatio, other.dampingRatio) == 0
                && Float.compare(startVelocity, other.startVelocity) == 0
                && Float.compare(finalPosition, other.finalPosition) == 0;
    }

    @Override
    public int hashCode() {
        int result = Float.hashCode(stiffness);
        result = 31 * result + Float.hashCode(dampingRatio);
        result = 31 * result + Float.hashCode(startVelocity);
        result = 31 * result + Float.hashCode(finalPosition);
        return result;
    }

    @Override
    public String toString() {
        return "SpringConfig(stiffness=" + stiffness + ", dampingRatio=" + dampingRatio
                + ", startVelocity=" + startVelocity + ", finalPosition=" + finalPosition + ")";
    }
}
