package com.smsforwarder.app;

record SimRules(boolean bySim, String sim1, String sim2) {
    static final int UNKNOWN = -1;

    String destinationId(int slot, String defaultId) {
        if (!bySim) {
            return defaultId;
        }
        return switch (slot) {
            case 0 -> sim1;
            case 1 -> sim2;
            default -> "";
        };
    }

    SimRules without(String destinationId) {
        return new SimRules(bySim, sim1.equals(destinationId) ? "" : sim1,
                sim2.equals(destinationId) ? "" : sim2);
    }
}
