package com.nexflow.sla;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;

@Component
public class SlaCalculator {

    public static final String DEFAULT_CALENDAR = "{\"weekdays\":[1,2,3,4,5],\"start\":\"09:00\",\"end\":\"18:00\"}";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public record BusinessCalendar(int[] weekdays, LocalTime start, LocalTime end) {

        public static BusinessCalendar defaultCalendar() {
            return new BusinessCalendar(new int[]{1, 2, 3, 4, 5}, LocalTime.of(9, 0), LocalTime.of(18, 0));
        }
    }

    /**
     * 纯函数：按业务日历（工作日 + 节假日 + 每日窗口）从 from 起累加 minutes 分钟，返回到期时间。
     */
    public LocalDateTime dueAt(LocalDateTime from, int minutes, SlaPolicy policy, Set<LocalDate> holidays) {
        BusinessCalendar cal = parseCalendar(policy == null ? null : policy.getBusinessCalendarJson());
        int remaining = minutes;
        LocalDateTime cursor = from;
        while (remaining > 0) {
            LocalDate date = cursor.toLocalDate();
            LocalDateTime windowStart = LocalDateTime.of(date, cal.start());
            LocalDateTime windowEnd = LocalDateTime.of(date, cal.end());
            if (isWorkingDay(date, cal, holidays)) {
                LocalDateTime effectiveStart = cursor.isAfter(windowStart) ? cursor : windowStart;
                if (effectiveStart.isBefore(windowEnd)) {
                    long available = Duration.between(effectiveStart, windowEnd).toMinutes();
                    if (available >= remaining) {
                        return effectiveStart.plusMinutes(remaining);
                    }
                    remaining -= (int) available;
                }
            }
            cursor = LocalDateTime.of(date.plusDays(1), cal.start());
        }
        return cursor;
    }

    public boolean isWorkingDay(LocalDate date, SlaPolicy policy, Set<LocalDate> holidays) {
        return isWorkingDay(date, parseCalendar(policy == null ? null : policy.getBusinessCalendarJson()), holidays);
    }

    private boolean isWorkingDay(LocalDate date, BusinessCalendar cal, Set<LocalDate> holidays) {
        if (holidays != null && holidays.contains(date)) {
            return false;
        }
        int dow = date.getDayOfWeek().getValue();
        for (int w : cal.weekdays()) {
            if (w == dow) {
                return true;
            }
        }
        return false;
    }

    private BusinessCalendar parseCalendar(String json) {
        if (json == null || json.isBlank()) {
            return BusinessCalendar.defaultCalendar();
        }
        try {
            JsonCalendar parsed = objectMapper.readValue(json, JsonCalendar.class);
            if (parsed.weekdays == null || parsed.weekdays.length == 0) {
                return BusinessCalendar.defaultCalendar();
            }
            Set<Integer> weekdays = new HashSet<>();
            for (int w : parsed.weekdays) {
                weekdays.add(w);
            }
            return new BusinessCalendar(parsed.weekdays, parsed.startTime(), parsed.endTime());
        } catch (Exception e) {
            return BusinessCalendar.defaultCalendar();
        }
    }

    public record JsonCalendar(int[] weekdays, String start, String end) {

        LocalTime startTime() {
            return start != null ? LocalTime.parse(start) : LocalTime.of(9, 0);
        }

        LocalTime endTime() {
            return end != null ? LocalTime.parse(end) : LocalTime.of(18, 0);
        }
    }
}
