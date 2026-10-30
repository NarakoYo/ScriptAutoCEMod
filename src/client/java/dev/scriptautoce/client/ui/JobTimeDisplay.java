package dev.scriptautoce.client.ui;

import dev.scriptautoce.network.ScriptPayloads.JobEventPayload;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import net.minecraft.network.chat.Component;

public final class JobTimeDisplay {
    private static final DateTimeFormatter FULL =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter SHORT =
            DateTimeFormatter.ofPattern("MM-dd HH:mm:ss");

    private JobTimeDisplay() {
    }

    public static String start(JobEventPayload job) {
        return format(job, FULL, job.startedAtMillis());
    }

    public static String end(JobEventPayload job) {
        return job.endedAtMillis() <= 0 ? "-" : format(job, FULL, job.endedAtMillis());
    }

    public static String rowTime(JobEventPayload job) {
        String start = format(job, SHORT, job.startedAtMillis());
        String end = job.endedAtMillis() <= 0 ? "-" : format(job, SHORT, job.endedAtMillis());
        return start + " -> " + end + "  " + duration(job);
    }

    public static String duration(JobEventPayload job) {
        long end = job.endedAtMillis() > 0 ? job.endedAtMillis() : System.currentTimeMillis();
        long millis = Math.max(0, end - job.startedAtMillis());
        long hours = millis / 3_600_000;
        long minutes = millis % 3_600_000 / 60_000;
        long seconds = millis % 60_000 / 1_000;
        long tenths = millis % 1_000 / 100;
        if (hours > 0) {
            return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds);
        }
        if (minutes > 0) {
            return String.format(Locale.ROOT, "%d:%02d", minutes, seconds);
        }
        return String.format(Locale.ROOT, "%d.%ds", seconds, tenths);
    }

    public static Component clockSource(JobEventPayload job) {
        return Component.translatable(job.serverClock()
                ? "screen.scriptautoce.time.server"
                : "screen.scriptautoce.time.local");
    }

    private static String format(JobEventPayload job, DateTimeFormatter formatter, long timestamp) {
        if (timestamp <= 0) {
            return "-";
        }
        ZoneId zone;
        try {
            zone = ZoneId.of(job.timeZone());
        } catch (RuntimeException exception) {
            zone = ZoneId.systemDefault();
        }
        return formatter.withZone(zone).format(Instant.ofEpochMilli(timestamp));
    }
}
