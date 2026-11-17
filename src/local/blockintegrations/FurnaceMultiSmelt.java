package local.blockintegrations;

final class FurnaceMultiSmelt {
    private FurnaceMultiSmelt() {
    }

    static Plan plan(int level, int sourceAmount, int storedOutputAmount,
            int eventResultAmount, int resultPerInput, int maximumStackSize) {
        int safeLevel = Math.max(0, level);
        int availableExtraInputs = Math.max(0, sourceAmount - 1);
        int safeResultPerInput = Math.max(1, resultPerInput);
        int availableOutputSpace = Math.max(0,
                maximumStackSize - Math.max(0, storedOutputAmount)
                        - Math.max(0, eventResultAmount));
        int additionalInputs = Math.min(safeLevel,
                Math.min(availableExtraInputs, availableOutputSpace / safeResultPerInput));
        return new Plan(additionalInputs, additionalInputs * safeResultPerInput);
    }

    static int itemsPerCycle(int level) {
        return 1 + Math.max(0, level);
    }

    record Plan(int additionalInputs, int additionalOutput) {
    }
}
