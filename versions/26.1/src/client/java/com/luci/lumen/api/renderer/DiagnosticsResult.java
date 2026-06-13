package com.luci.lumen.api.renderer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.Map;

@Environment(EnvType.CLIENT)
public record DiagnosticsResult(
        boolean backendAvailable,
        Map<String, StepStatus> steps
) {
    public enum Status {
        OK,
        FAILED,
        SKIPPED,
        NOT_APPLICABLE
    }

    public record StepStatus(String label, Status status, String message) {
        public boolean isOk() { return status == Status.OK; }
        public boolean isFailed() { return status == Status.FAILED; }
    }
}
