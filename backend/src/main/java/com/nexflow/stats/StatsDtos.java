package com.nexflow.stats;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

public class StatsDtos {

    @Data
    public static class Overview {
        private long total;
        private long open;
        private long unassigned;
        private long atRisk;
        private long breached;
        private long todayCreated;
        private long todayResolved;
        private double avgResolveHours;
        private double slaRate;
        private boolean demo = true;
    }

    @Data
    public static class TrendPoint {
        private LocalDate date;
        private long created;
        private long resolved;

        public TrendPoint(LocalDate date, long created, long resolved) {
            this.date = date;
            this.created = created;
            this.resolved = resolved;
        }
    }

    @Data
    public static class DeptPerformance {
        private Long deptId;
        private String deptName;
        private long total;
        private long resolved;
        private double avgHours;
        private double slaRate;
    }

    @Data
    public static class Workload {
        private Long userId;
        private String userName;
        private long total;
        private long inProgress;
        private boolean exceedLimit;
    }

    @Data
    public static class Queues {
        private long pendingApprovals;
        private long unassigned;
    }
}
