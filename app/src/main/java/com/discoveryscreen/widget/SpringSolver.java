package com.discoveryscreen.widget;

public final class SpringSolver {
    public static final float THRESHOLD_MULTIPLIER = 0.75f;
    public static final float VELOCITY_THRESHOLD_FACTOR = 62.5f;

    public static final float MIN_VISIBLE_CHANGE_ALPHA = 0.00390625f;
    public static final float MIN_VISIBLE_CHANGE_SCALE = 0.002f;
    public static final float MIN_VISIBLE_CHANGE_ROTATION = 0.1f;
    public static final float MIN_VISIBLE_CHANGE_DEFAULT = 1.0f;

    private final double naturalFreq;
    private final double dampingRatio;
    private final double valueThreshold;
    private final double velocityThreshold;

    private double gammaPlus;
    private double gammaMinus;
    private double dampedFreq;
    private boolean initialized;

    private double finalPosition;
    private float value;
    private float velocity;

    public SpringSolver(SpringConfig config) {
        this(config, MIN_VISIBLE_CHANGE_DEFAULT);
    }

    public SpringSolver(SpringConfig config, float minVisibleChange) {
        this.naturalFreq = Math.sqrt(config.stiffness);
        this.dampingRatio = config.dampingRatio;
        double threshold = Math.abs(minVisibleChange * THRESHOLD_MULTIPLIER);
        this.valueThreshold = threshold;
        this.velocityThreshold = threshold * VELOCITY_THRESHOLD_FACTOR;
        this.value = 0.0f;
        this.velocity = config.startVelocity;
        this.finalPosition = config.hasFinalPosition() ? config.finalPosition : Double.MAX_VALUE;
    }

    public void setFinalPosition(float finalPosition) {
        this.finalPosition = finalPosition;
    }

    public boolean hasFinalPosition() {
        return finalPosition != Double.MAX_VALUE;
    }

    public float getFinalPosition() {
        if (!hasFinalPosition()) {
            throw new IllegalStateException("Final position has not been set");
        }
        return (float) finalPosition;
    }

    public float getValue() {
        return value;
    }

    public void setValue(float value) {
        this.value = value;
    }

    public float getVelocity() {
        return velocity;
    }

    public void setVelocity(float velocity) {
        this.velocity = velocity;
    }

    public double getValueThreshold() {
        return valueThreshold;
    }

    public double getVelocityThreshold() {
        return velocityThreshold;
    }

    public boolean isAtEquilibrium() {
        if (!hasFinalPosition()) {
            return false;
        }
        return Math.abs(velocity) < velocityThreshold
                && Math.abs(finalPosition - value) < valueThreshold;
    }

    /** @return false once settled; throws if no final position was set. */
    public boolean advance(long deltaMillis) {
        if (isAtEquilibrium()) {
            return false;
        }
        if (deltaMillis <= 0L) {
            return true;
        }
        if (!initialized) {
            if (finalPosition == Double.MAX_VALUE) {
                throw new IllegalStateException(
                        "Error: Final position of the spring must be set before the animation starts");
            }
            if (dampingRatio > 1.0d) {
                double root = Math.sqrt((dampingRatio * dampingRatio) - 1.0d) * naturalFreq;
                gammaPlus = root + ((-dampingRatio) * naturalFreq);
                gammaMinus = ((-dampingRatio) * naturalFreq) - root;
            } else if (dampingRatio < 1.0d) {
                dampedFreq = Math.sqrt(1.0d - (dampingRatio * dampingRatio)) * naturalFreq;
            }
            initialized = true;
        }

        double dt = deltaMillis / 1000.0d;
        double displacement = value - finalPosition;
        double newValue;
        double newVelocity;
        if (dampingRatio > 1.0d) {
            double coefficientA = ((gammaMinus * displacement) - velocity) / (gammaMinus - gammaPlus);
            double coefficientB = displacement - coefficientA;
            newValue = (Math.exp(gammaPlus * dt) * coefficientA)
                    + (Math.exp(gammaMinus * dt) * coefficientB);
            newVelocity = (Math.exp(gammaMinus * dt) * coefficientB * gammaMinus)
                    + (Math.exp(gammaPlus * dt) * coefficientA * gammaPlus);
        } else if (dampingRatio == 1.0d) {
            double coefficientA = (naturalFreq * displacement) + velocity;
            double coefficientB = (coefficientA * dt) + displacement;
            double decay = Math.exp((-naturalFreq) * dt);
            newValue = decay * coefficientB;
            newVelocity = (decay * coefficientA) + (coefficientB * (-naturalFreq) * decay);
        } else {
            double coefficient = (((dampingRatio * naturalFreq) * displacement) + velocity) / dampedFreq;
            double angle = dampedFreq * dt;
            newValue = ((Math.sin(angle) * coefficient) + (Math.cos(angle) * displacement))
                    * Math.exp((-dampingRatio) * naturalFreq * dt);
            double decay = Math.exp((-dampingRatio) * naturalFreq * dt);
            double velocityTerm = (Math.cos(angle) * coefficient * dampedFreq)
                    + (Math.sin(angle) * (-dampedFreq) * displacement);
            newVelocity = (velocityTerm * decay) + ((-naturalFreq) * newValue * dampingRatio);
        }

        value = (float) (newValue + finalPosition);
        velocity = (float) newVelocity;
        return !isAtEquilibrium();
    }
}
