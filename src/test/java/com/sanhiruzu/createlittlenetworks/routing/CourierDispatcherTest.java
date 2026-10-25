package com.sanhiruzu.createlittlenetworks.routing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourierDispatcherTest {
    @Test
    void freshServerSentinelDispatchesWithoutLongOverflow() {
        assertTrue(CourierDispatcher.dispatchDue(0, Long.MIN_VALUE));
        assertTrue(CourierDispatcher.dispatchDue(24_000, Long.MIN_VALUE));
    }

    @Test
    void throttleRequiresTwentyTicksAfterFirstDispatch() {
        assertFalse(CourierDispatcher.dispatchDue(119, 100));
        assertTrue(CourierDispatcher.dispatchDue(120, 100));
    }
}
