package dev.hypershot.core;

import org.junit.jupiter.api.Test;

final class CoreRegressionTest {
    @Test
    void runsDependencyFreeCoreRegressionSuite() throws Exception {
        CoreTestMain.main(new String[0]);
    }
}
