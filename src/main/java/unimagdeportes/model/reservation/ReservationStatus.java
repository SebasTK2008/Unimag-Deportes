package unimagdeportes.model.reservation;

public enum ReservationStatus {
    CONFIRMED(true, true), IN_USE(true, true), COMPLETED(true, true),
    CANCELLED_ON_TIME(false, false), CANCELLED_LATE(true, false), NO_SHOW(true, true),
    CANCELLED_BY_ADMIN(false, false), CANCELLED_BY_SUSPENSION(false, false);

    private final boolean countsForQuota;
    private final boolean occupiesSpace;

    ReservationStatus(boolean countsForQuota, boolean occupiesSpace) {
        this.countsForQuota = countsForQuota;
        this.occupiesSpace = occupiesSpace;
    }

    public boolean countsForQuota() { return countsForQuota; }
    public boolean occupiesSpace() { return occupiesSpace; }
}
