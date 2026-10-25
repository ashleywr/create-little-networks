package com.sanhiruzu.createlittlenetworks.schedule;

import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.condition.ScheduledDelay;
import com.simibubi.create.content.trains.schedule.condition.IdleCargoCondition;
import com.simibubi.create.content.trains.schedule.destination.DeliverPackagesInstruction;
import com.simibubi.create.content.trains.schedule.destination.FetchPackagesInstruction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CourierScheduleTest {
    @Test
    void acceptsStandardCreatePackageScheduleWithoutDepot() {
        FetchPackagesInstruction fetch = new FetchPackagesInstruction();
        fetch.getData().putString("Text", "Factory *");
        Schedule schedule = new Schedule(List.of(
                new ScheduleEntry(fetch, List.of()),
                new ScheduleEntry(new DeliverPackagesInstruction(), List.of())
        ), true, 0);

        CourierSchedule.Validated validated = CourierSchedule.validate(schedule).value().orElseThrow();
        assertEquals(CourierSchedule.Mode.CREATE_COMPATIBLE, validated.mode());
        assertFalse(validated.usesDepot());
        assertEquals("Factory *", validated.addressFilter());
    }

    @Test
    void acceptsCanonicalCourierScheduleAndExtractsFilters() {
        LittleLogisticsDepotInstruction depot = new LittleLogisticsDepotInstruction();
        depot.getData().putString("Text", "Main Depot");
        FetchPackagesInstruction fetch = new FetchPackagesInstruction();
        fetch.getData().putString("Text", "Factory *");

        Schedule schedule = new Schedule(List.of(
                new ScheduleEntry(depot, List.of()),
                new ScheduleEntry(fetch, List.of()),
                new ScheduleEntry(new DeliverPackagesInstruction(), List.of())
        ), true, 0);

        CourierSchedule.Result result = CourierSchedule.validate(schedule);
        assertTrue(result.value().isPresent());
        assertEquals(CourierSchedule.Mode.DEPOT, result.value().orElseThrow().mode());
        assertEquals("Main Depot", result.value().orElseThrow().depotFilter());
        assertEquals("Factory *", result.value().orElseThrow().addressFilter());
    }

    @Test
    void rejectsWrongOrderAndNonCyclicSchedules() {
        Schedule wrongOrder = new Schedule(List.of(
                new ScheduleEntry(new FetchPackagesInstruction(), List.of()),
                new ScheduleEntry(new LittleLogisticsDepotInstruction(), List.of()),
                new ScheduleEntry(new DeliverPackagesInstruction(), List.of())
        ), true, 0);
        assertTrue(CourierSchedule.validate(wrongOrder).value().isEmpty());

        wrongOrder.cyclic = false;
        assertTrue(CourierSchedule.validate(wrongOrder).value().isEmpty());
    }

    @Test
    void carriesScheduledDelayIntoPickupPhase() {
        ScheduledDelay delay = new ScheduledDelay();
        delay.getData().putInt("Value", 3);
        delay.getData().putInt("TimeUnit", 1);
        Schedule schedule = new Schedule(List.of(
                new ScheduleEntry(new LittleLogisticsDepotInstruction(), List.of()),
                new ScheduleEntry(new FetchPackagesInstruction(), List.of(List.of(delay))),
                new ScheduleEntry(new DeliverPackagesInstruction(), List.of())
        ), true, 0);
        assertEquals(60, CourierSchedule.validate(schedule).value().orElseThrow().pickupDelay());
    }

    @Test
    void preservesConfiguredCargoInactivityDuration() {
        IdleCargoCondition idle = new IdleCargoCondition();
        idle.getData().putInt("Value", 4);
        idle.getData().putInt("TimeUnit", 1);
        Schedule schedule = new Schedule(List.of(
                new ScheduleEntry(new FetchPackagesInstruction(), List.of(List.of(idle))),
                new ScheduleEntry(new DeliverPackagesInstruction(), List.of())
        ), true, 0);

        assertEquals(80, CourierSchedule.validate(schedule).value().orElseThrow().pickupDelay());
    }
}
