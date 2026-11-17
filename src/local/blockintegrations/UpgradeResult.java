package local.blockintegrations;

/**
 * Result of one upgrade attempt. The in-game dialogs and the web page call the same check-and-apply method of the
 * manager; the dialog turns the outcome into its chat messages, the web page into an HTTP answer.
 */
record UpgradeResult(Outcome outcome, int level, int cost) {
    enum Outcome {
        /** Missing {@code *.upgrade} permission. */
        NO_PERMISSION,
        /** Not the owner (and not allowed to manage others). */
        DENIED,
        /** Structure missing, incomplete or its chunk is not loaded. */
        GONE,
        /** Starter tower: no upgrade paths. */
        NOT_UPGRADEABLE,
        /** The structure does not have this upgrade path. */
        UNSUPPORTED,
        MAXED,
        /** Level or cost differ from the offer the player saw. */
        CHANGED,
        NEED_XP,
        DONE
    }

    static UpgradeResult of(Outcome outcome) {
        return new UpgradeResult(outcome, 0, 0);
    }

    boolean done() {
        return outcome == Outcome.DONE;
    }
}
