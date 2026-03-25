package com.capellaagent.simulation.orchestrator;

import java.util.HashMap;
import java.util.Map;

/**
 * Data Transfer Object specifying a what-if parameter sweep.
 * <p>
 * Contains a base simulation configuration and a set of parameter ranges to vary.
 * The {@link WhatIfManager} generates all combinations from the parameter ranges
 * and runs each through the simulation orchestrator.
 *
 * <h3>Example</h3>
 * <pre>{@code
 * WhatIfSpec spec = new WhatIfSpec();
 * spec.setBaseConfig(config);
 * spec.getParameterRanges().put("speed", new double[]{10.0, 20.0, 30.0});
 * spec.getParameterRanges().put("mass", new double[]{100.0, 200.0});
 * // This produces 6 simulation runs (3 x 2 combinations)
 * }</pre>
 */
public class WhatIfSpec {

    private SimulationConfig baseConfig;
    private final Map<String, double[]> parameterRanges;

    /**
     * Constructs a new WhatIfSpec with empty parameter ranges.
     */
    public WhatIfSpec() {
        this.parameterRanges = new HashMap<>();
    }

    /**
     * Returns the base simulation configuration.
     *
     * @return the base config
     */
    public SimulationConfig getBaseConfig() {
        return baseConfig;
    }

    /**
     * Sets the base simulation configuration.
     *
     * @param baseConfig the base config
     */
    public void setBaseConfig(SimulationConfig baseConfig) {
        this.baseConfig = baseConfig;
    }

    /**
     * Returns the parameter ranges for the sweep.
     * <p>
     * Each entry maps a parameter name to an array of values to try.
     * The WhatIfManager generates all combinations across all parameters.
     *
     * @return the parameter ranges map
     */
    public Map<String, double[]> getParameterRanges() {
        return parameterRanges;
    }

    /**
     * Computes the total number of simulation runs this spec will produce.
     *
     * @return the total number of parameter combinations
     */
    public int getTotalCombinations() {
        if (parameterRanges.isEmpty()) {
            return 1;
        }
        int total = 1;
        for (double[] values : parameterRanges.values()) {
            total *= values.length;
        }
        return total;
    }

    /**
     * Validates this specification.
     *
     * @return {@code true} if the spec has a valid base config and at least one parameter range
     */
    public boolean isValid() {
        return baseConfig != null && baseConfig.isValid() && !parameterRanges.isEmpty();
    }
}
