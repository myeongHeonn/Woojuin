package com.ssafy.woojuin.domain.ai.usage;

public record AiUsageReservation(
        Long billedUserId,
        Status status,
        String usageKey,
        String reservationKey) {

    public enum Status {
        COUNTED,
        UNLIMITED,
        DISABLED
    }

    public boolean counted() {
        return status == Status.COUNTED;
    }

    public static AiUsageReservation counted(
            Long billedUserId, String usageKey, String reservationKey) {
        return new AiUsageReservation(
                billedUserId, Status.COUNTED, usageKey, reservationKey);
    }

    public static AiUsageReservation unlimited(Long billedUserId) {
        return new AiUsageReservation(
                billedUserId, Status.UNLIMITED, null, null);
    }

    public static AiUsageReservation disabled() {
        return new AiUsageReservation(
                null, Status.DISABLED, null, null);
    }
}
