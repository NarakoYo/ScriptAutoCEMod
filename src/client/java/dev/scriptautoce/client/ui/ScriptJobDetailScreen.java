package dev.scriptautoce.client.ui;

import com.google.gson.reflect.TypeToken;
import dev.scriptautoce.client.network.ClientNetworkController;
import dev.scriptautoce.network.ScriptPayloads.JobEventPayload;
import java.lang.reflect.Type;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class ScriptJobDetailScreen extends Screen {
    private static final Type STRING_LIST = new TypeToken<List<String>>() {
    }.getType();
    private final Screen parent;
    private final JobEventPayload job;
    private final List<String> logs;

    ScriptJobDetailScreen(Screen parent, JobEventPayload job) {
        super(Component.translatable("screen.scriptautoce.job_detail"));
        this.parent = parent;
        this.job = job;
        List<String> parsed = dev.scriptautoce.storage.JsonFiles.GSON.fromJson(job.logsJson(), STRING_LIST);
        this.logs = parsed == null ? List.of() : List.copyOf(parsed);
    }

    @Override
    protected void init() {
        clearWidgets();
        JobEventPayload latest = ClientUiState.job(job.jobId());
        JobEventPayload current = latest == null ? job : latest;
        ChatFormatting color = JobReasonDisplay.color(job.reasonCode());
        addRenderableWidget(new StringWidget(16, 14, width - 32, 10,
                Component.translatable("screen.scriptautoce.job_detail"), font));
        addRenderableWidget(new StringWidget(16, 38, width - 32, 10,
                JobReasonDisplay.label(current.reasonCode()).copy().withStyle(color), font));
        addRenderableWidget(new StringWidget(16, 54, width - 32, 10,
                JobReasonDisplay.description(current.reasonCode()).copy().withStyle(ChatFormatting.GRAY),
                font));
        addRenderableWidget(new StringWidget(16, 78, width / 2 - 24, 10,
                Component.translatable("screen.scriptautoce.job_started_at",
                        JobTimeDisplay.start(current)).withStyle(ChatFormatting.GRAY), font));
        addRenderableWidget(new StringWidget(width / 2 + 8, 78, width / 2 - 24, 10,
                Component.translatable("screen.scriptautoce.job_duration",
                        JobTimeDisplay.duration(current)).withStyle(ChatFormatting.GRAY), font));
        addRenderableWidget(new StringWidget(16, 92, width / 2 - 24, 10,
                Component.translatable("screen.scriptautoce.job_ended_at",
                        JobTimeDisplay.end(current)).withStyle(ChatFormatting.GRAY), font));
        addRenderableWidget(new StringWidget(width / 2 + 8, 92, width / 2 - 24, 10,
                Component.translatable("screen.scriptautoce.job_clock_source",
                        JobTimeDisplay.clockSource(current)).withStyle(ChatFormatting.DARK_AQUA), font));
        addRenderableWidget(new StringWidget(16, 110, width - 32, 10,
                Component.translatable("screen.scriptautoce.job_progress",
                        current.progress()).withStyle(ChatFormatting.GRAY), font));
        if (!current.currentCommand().isBlank()) {
            addRenderableWidget(new StringWidget(16, 124, width - 32, 10,
                    Component.translatable("screen.scriptautoce.job_command", current.currentCommand())
                            .withStyle(ChatFormatting.AQUA), font));
        }
        if (current.line() > 0) {
            addRenderableWidget(new StringWidget(16, 138, width - 32, 10,
                    Component.translatable("screen.scriptautoce.job_line", current.line())
                            .withStyle(ChatFormatting.GRAY), font));
        }
        if (!current.detail().isBlank()) {
            addRenderableWidget(new StringWidget(16, 152, width - 32, 10,
                    Component.translatable("screen.scriptautoce.job_technical_detail", current.detail())
                            .withStyle(ChatFormatting.DARK_GRAY), font));
        }
        if ("EXTERNAL_EMERGENCY".equals(current.reasonCode())) {
            addRenderableWidget(new StringWidget(16, 166, width - 32, 10,
                    Component.translatable("screen.scriptautoce.job_emergency_hint")
                            .withStyle(ChatFormatting.YELLOW), font));
        }

        int y = 182;
        int maxLogs = Math.max(1, (height - 30 - y) / 12);
        int start = Math.max(0, logs.size() - maxLogs);
        for (int index = start; index < logs.size(); index++) {
            addRenderableWidget(new StringWidget(16, y, width - 32, 10,
                    Component.literal(logs.get(index)).withStyle(ChatFormatting.GRAY), font));
            y += 12;
        }
        if (logs.isEmpty()) {
            addRenderableWidget(new StringWidget(16, 182, width - 32, 10,
                    Component.translatable("screen.scriptautoce.no_logs").withStyle(ChatFormatting.DARK_GRAY),
                    font));
        }
        if (JobReasonDisplay.active(current.reasonCode())) {
            addRenderableWidget(Button.builder(Component.translatable("screen.scriptautoce.stop"),
                    button -> ClientNetworkController.stopJob(current.jobId()))
                    .bounds(16, height - 28, 70, 18).build());
        } else {
            addRenderableWidget(Button.builder(
                    Component.translatable("screen.scriptautoce.deleteRecord"),
                    button -> {
                        ClientUiState.removeJob(current.jobId());
                        onClose();
                    }).bounds(16, height - 28, 86, 18).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds(width - 76, height - 28, 60, 18).build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, 0xFF1E1E1E);
        graphics.fill(0, 0, width, 28, 0xFF252526);
        graphics.fill(0, height - 20, width, height - 19, 0xFF3B82F6);
        graphics.fill(0, height - 19, width, height, 0xFF1F2937);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        minecraft.setScreenAndShow(parent);
    }
}
