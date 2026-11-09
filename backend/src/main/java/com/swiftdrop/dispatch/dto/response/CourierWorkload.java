package com.swiftdrop.dispatch.dto.response;

/**
 * What a courier has on today. Open assignments are the ones not ended yet.
 *
 * @param assigned       open assignments the courier has not started
 * @param inProgress     open assignments the courier is delivering
 * @param completedToday assignments completed since midnight, Sri Lanka time
 * @param open           {@code assigned + inProgress}
 * @param todaysTotal    {@code open + completedToday}: the "12 / 15" on the Couriers screen is
 *                       {@code completedToday / todaysTotal}
 */
public record CourierWorkload(int assigned, int inProgress, int completedToday, int open, int todaysTotal) {

    public static final CourierWorkload EMPTY = of(0, 0, 0);

    public static CourierWorkload of(int assigned, int inProgress, int completedToday) {
        int open = assigned + inProgress;
        return new CourierWorkload(assigned, inProgress, completedToday, open, open + completedToday);
    }
}
