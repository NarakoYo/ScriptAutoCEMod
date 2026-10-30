package dev.scriptautoce.client.ui;

import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public final class JobReasonDisplay {
    private JobReasonDisplay() {
    }

    public static Component label(String reasonCode) {
        String key = reasonCode == null || reasonCode.isBlank() ? "unknown" : reasonCode.toLowerCase(Locale.ROOT);
        return Component.translatable("job.scriptautoce.reason." + key);
    }

    public static Component description(String reasonCode) {
        String key = reasonCode == null || reasonCode.isBlank() ? "unknown" : reasonCode.toLowerCase(Locale.ROOT);
        return Component.translatable("job.scriptautoce.reason." + key + ".description");
    }

    public static ChatFormatting color(String reasonCode) {
        return switch (reasonCode == null ? "UNKNOWN" : reasonCode) {
            case "COMPLETED" -> ChatFormatting.GREEN;
            case "RUNNING" -> ChatFormatting.AQUA;
            case "USER_CANCELLED", "LIMIT_EXCEEDED" -> ChatFormatting.YELLOW;
            case "EXTERNAL_DISCONNECT", "EXTERNAL_EMERGENCY" -> ChatFormatting.GOLD;
            case "SCRIPT_ERROR", "COMMAND_ERROR", "COMMAND_DENIED", "POLICY_BLOCKED" -> ChatFormatting.RED;
            default -> ChatFormatting.GRAY;
        };
    }

    public static boolean active(String reasonCode) {
        return "RUNNING".equals(reasonCode);
    }
}
