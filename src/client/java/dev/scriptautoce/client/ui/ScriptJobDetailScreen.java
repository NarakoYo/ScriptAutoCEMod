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
        ChatFormatting color = JobReasonDisplay.color(job.reasonCode());
        addRenderableWidget(new StringWidget(16, 14, width - 32, 10,
                Component.translatable("screen.scriptautoce.job_detail"), font));
        addRenderableWidget(new StringWidget(16, 38, width - 32, 10,
                JobReasonDisplay.label(job.reasonCode()).copy().withStyle(color), font));
        addRenderableWidget(new StringWidget(16, 54, width - 32, 10,
                JobReasonDisplay.description(job.reasonCode()).copy().withStyle(ChatFormatting.GRAY), font));
        addRenderableWidget(new StringWidget(16, 72, width - 32, 10,
                Component.translatable("screen.scriptautoce.job_progress",
                        job.progress()).withStyle(ChatFormatting.GRAY), font));
        if (!job.currentCommand().isBlank()) {
            addRenderableWidget(new StringWidget(16, 88, width - 32, 10,
                    Component.translatable("screen.scriptautoce.job_command", job.currentCommand())
                            .withStyle(ChatFormatting.AQUA), font));
        }
        if (job.line() > 0) {
            addRenderableWidget(new StringWidget(16, 104, width - 32, 10,
                    Component.translatable("screen.scriptautoce.job_line", job.line())
                            .withStyle(ChatFormatting.GRAY), font));
        }
        if (!job.detail().isBlank()) {
            addRenderableWidget(new StringWidget(16, 116, width - 32, 10,
                    Component.translatable("screen.scriptautoce.job_technical_detail", job.detail())
                            .withStyle(ChatFormatting.DARK_GRAY), font));
        }

        int y = 134;
        int start = Math.max(0, logs.size() - 8);
        for (int index = start; index < logs.size(); index++) {
            addRenderableWidget(new StringWidget(16, y, width - 32, 10,
                    Component.literal(logs.get(index)).withStyle(ChatFormatting.GRAY), font));
            y += 12;
        }
        if (logs.isEmpty()) {
            addRenderableWidget(new StringWidget(16, 126, width - 32, 10,
                    Component.translatable("screen.scriptautoce.no_logs").withStyle(ChatFormatting.DARK_GRAY),
                    font));
        }
        if (JobReasonDisplay.active(job.reasonCode())) {
            addRenderableWidget(Button.builder(Component.translatable("screen.scriptautoce.stop"),
                    button -> ClientNetworkController.stopJob(job.jobId()))
                    .bounds(16, height - 28, 70, 18).build());
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
