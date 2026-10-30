package dev.scriptautoce.client.ui;

import dev.scriptautoce.client.editor.SyntaxHighlightRegistry;
import dev.scriptautoce.client.network.ClientNetworkController;
import dev.scriptautoce.storage.SharedScriptStore;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class SharedScriptDetailScreen extends Screen {
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private final Screen parent;
    private final SharedScriptStore.Info info;
    private final String source;

    public SharedScriptDetailScreen(Screen parent, SharedScriptStore.Info info, String source) {
        super(Component.translatable("screen.scriptautoce.shared_detail"));
        this.parent = parent;
        this.info = info;
        this.source = source;
    }

    @Override
    protected void init() {
        clearWidgets();
        addRenderableWidget(new StringWidget((width - font.width(title)) / 2, 10,
                font.width(title) + 4, 10, title, font));
        addRenderableWidget(new StringWidget(12, 32, width - 24, 10,
                Component.translatable("screen.scriptautoce.shared_title", info.title())
                        .withStyle(ChatFormatting.AQUA), font));
        addRenderableWidget(new StringWidget(12, 47, width - 24, 10,
                Component.translatable("screen.scriptautoce.shared_author", info.ownerName(),
                        info.ownerUuid()).withStyle(ChatFormatting.GRAY), font));
        addRenderableWidget(new StringWidget(12, 62, width - 24, 10,
                Component.translatable("screen.scriptautoce.shared_revision", info.revision(),
                        formatTime(info.updatedAt())).withStyle(ChatFormatting.GRAY), font));
        addRenderableWidget(new StringWidget(12, 77, width - 24, 10,
                Component.translatable("screen.scriptautoce.shared_metadata",
                        abbreviate(info.hash(), 16), info.size()).withStyle(ChatFormatting.DARK_GRAY), font));

        MultiLineEditBox preview = MultiLineEditBox.builder()
                .setX(12)
                .setY(96)
                .setShowBackground(true)
                .setShowDecorations(true)
                .setTextColor(0xFFD4D4D4)
                .setTextShadow(false)
                .build(font, width - 24, height - 132,
                        Component.translatable("screen.scriptautoce.shared_content"));
        preview.setValue(source);
        preview.active = false;
        SyntaxHighlightRegistry.register(preview);
        addRenderableWidget(preview);

        addRenderableWidget(Button.builder(Component.translatable("screen.scriptautoce.download"),
                button -> ClientNetworkController.downloadShared(info.id()))
                .bounds(12, height - 28, 80, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds(width - 72, height - 28, 60, 18).build());
    }

    private static String formatTime(String timestamp) {
        try {
            return TIME_FORMAT.format(Instant.parse(timestamp));
        } catch (DateTimeParseException exception) {
            return timestamp;
        }
    }

    private static String abbreviate(String value, int length) {
        return value == null || value.length() <= length ? value : value.substring(0, length) + "...";
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, 0xFF1E1E1E);
        graphics.fill(0, 0, width, 28, 0xFF252526);
        graphics.fill(0, 88, width, 89, 0xFF2B2B2B);
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
