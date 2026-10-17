public final class RunTests {
    public static void main(String[] args) {
        int failures = 0;
        failures += runOne("EvieProtocol", EvieProtocolTest::run);
        failures += runOne("WidgetListCodec", WidgetListCodecTest::run);
        failures += runOne("WidgetHeightPolicy", WidgetHeightPolicyTest::run);
        failures += runOne("WidgetSizeTable", WidgetSizeTableTest::run);
        failures += runOne("WidgetReloadPolicy", WidgetReloadPolicyTest::run);
        failures += runOne("WidgetInflationPolicy", WidgetInflationPolicyTest::run);
        failures += runOne("StagedQueue", StagedQueueTest::run);
        failures += runOne("ReloadGate", ReloadGateTest::run);
        failures += runOne("MotionMath", MotionMathTest::run);
        failures += runOne("SpringConfig", SpringConfigTest::run);
        failures += runOne("SpringSolver", SpringSolverTest::run);
        if (failures > 0) {
            System.out.println("RESULT: " + failures + " SUITE(S) FAILED");
            System.exit(1);
        }
        System.out.println("RESULT: ALL SUITES PASSED");
    }

    private static int runOne(String name, Runnable r) {
        try {
            r.run();
            return 0;
        } catch (Throwable t) {
            System.out.println(name + ": FAIL");
            t.printStackTrace(System.out);
            return 1;
        }
    }
}
