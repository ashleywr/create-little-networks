package com.sanhiruzu.createlittlenetworks.schedule;

import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.condition.IdleCargoCondition;
import com.simibubi.create.content.trains.schedule.condition.ScheduledDelay;
import com.simibubi.create.content.trains.schedule.destination.DeliverPackagesInstruction;
import com.simibubi.create.content.trains.schedule.destination.FetchPackagesInstruction;

import java.util.Optional;

/** Validation and the subset of Create schedule semantics supported by LL couriers. */
public final class CourierSchedule {
    public enum Mode { CREATE_COMPATIBLE, DEPOT }

    public record Validated(Mode mode, String depotFilter, String addressFilter,
                            int pickupDelay, int deliveryDelay) {
        public boolean usesDepot() {
            return mode == Mode.DEPOT;
        }
    }
    public record Result(Optional<Validated> value, String error) {
        static Result success(Validated value) { return new Result(Optional.of(value), ""); }
        static Result error(String error) { return new Result(Optional.empty(), error); }
    }

    private CourierSchedule() {}

    public static Result validate(Schedule schedule) {
        if (!schedule.cyclic) return Result.error("Courier schedules must be cyclic.");
        Mode mode;
        String depotFilter = "";
        ScheduleEntry pickupEntry;
        ScheduleEntry deliveryEntry;
        if (schedule.entries.size() == 2) {
            mode = Mode.CREATE_COMPATIBLE;
            pickupEntry = schedule.entries.get(0);
            deliveryEntry = schedule.entries.get(1);
        } else if (schedule.entries.size() == 3
                && schedule.entries.get(0).instruction instanceof LittleLogisticsDepotInstruction depot) {
            mode = Mode.DEPOT;
            depotFilter = depot.getFilter();
            pickupEntry = schedule.entries.get(1);
            deliveryEntry = schedule.entries.get(2);
        } else {
            return Result.error("Use Retrieve Packages, Deliver Packages; optionally prefix them with Little Logistics Depot.");
        }
        if (!(pickupEntry.instruction instanceof FetchPackagesInstruction pickup)
                || !(deliveryEntry.instruction instanceof DeliverPackagesInstruction)) {
            return Result.error(mode == Mode.DEPOT
                    ? "Schedule order must be Depot, Retrieve Packages, Deliver Packages."
                    : "Schedule order must be Retrieve Packages, Deliver Packages.");
        }
        int pickupDelay = delayTicks(pickupEntry);
        int deliveryDelay = delayTicks(deliveryEntry);
        if (pickupDelay < 0 || deliveryDelay < 0) {
            return Result.error("Only Scheduled Delay and Cargo Inactivity conditions are supported.");
        }
        return Result.success(new Validated(mode, depotFilter, pickup.getFilter(), pickupDelay, deliveryDelay));
    }

    private static int delayTicks(ScheduleEntry entry) {
        int delay = 0;
        for (var column : entry.conditions) {
            for (var condition : column) {
                if (condition instanceof ScheduledDelay scheduledDelay) {
                    delay = Math.max(delay, scheduledDelay.totalWaitTicks());
                } else if (condition instanceof IdleCargoCondition idleCargo) {
                    delay = Math.max(delay, idleCargo.totalWaitTicks());
                } else {
                    return -1;
                }
            }
        }
        return delay;
    }
}
