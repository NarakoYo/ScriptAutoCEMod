package dev.scriptautoce.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.scriptautoce.ExecutionMode;
import dev.scriptautoce.client.network.ClientNetworkController;
import dev.scriptautoce.config.ClientSettings;
import dev.scriptautoce.config.ServerSettings;
import dev.scriptautoce.storage.JsonFiles;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class ScriptSettingsScreen extends Screen {
    private final Screen parent;
    private ClientSettings clientSettings;
    private ServerSettings serverSettings;
    private EditBox commandsPerTick;
    private EditBox runtimeTicks;
    private EditBox emergencyMillis;
    private String status = "";
    private StringWidget statusWidget;
    private Button shortcutButton;
    private boolean listeningForKey;

    public ScriptSettingsScreen(Screen parent) {
        super(Component.translatable("screen.scriptautoce.settings"));
        this.parent = parent;
        this.clientSettings = ClientUiState.settings();
        ClientNetworkController.requestServerSettings();
    }

    @Override
    protected void init() {
        clearWidgets();
        try {
            serverSettings = JsonFiles.GSON.fromJson(ClientUiState.serverSettingsJson(), ServerSettings.class);
        } catch (RuntimeException exception) {
            serverSettings = null;
        }
        if (serverSettings == null) {
            serverSettings = ServerSettings.defaults();
        }
        int x = width / 2 - 210;
        int y = 40;
        addRenderableWidget(new StringWidget((width - font.width(title)) / 2, 12,
                font.width(title) + 4, 10, title, font));
        addRenderableWidget(new StringWidget(x, 28, 190, 10,
                Component.translatable("screen.scriptautoce.settings.client"), font));
        addRenderableWidget(new StringWidget(x + 220, 28, 190, 10,
                Component.translatable("screen.scriptautoce.settings.server"), font));
        addRenderableWidget(Button.builder(modeLabel(), button -> {
            ExecutionMode[] modes = ExecutionMode.values();
            clientSettings = clientSettings.withMode(
                    modes[(clientSettings.executionMode().ordinal() + 1) % modes.length]);
            ClientUiState.updateSettings(clientSettings);
            button.setMessage(modeLabel());
        }).bounds(x, y, 190, 20).build());
        addRenderableWidget(Button.builder(confirmLabel(), button -> {
            clientSettings = clientSettings.withConfirmation(!clientSettings.confirmBeforeRun());
            ClientUiState.updateSettings(clientSettings);
            button.setMessage(confirmLabel());
        }).bounds(x, y + 26, 190, 20).build());
        shortcutButton = addRenderableWidget(Button.builder(shortcutLabel(), button -> {
            listeningForKey = true;
            status = Component.translatable("screen.scriptautoce.settings.shortcutListening").getString();
            statusWidget.setMessage(Component.literal(status));
            button.setMessage(shortcutLabel());
        }).bounds(x, y + 52, 150, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.scriptautoce.settings.resetKey"),
                button -> {
                    ClientNetworkController.resetOpenLibraryKey();
                    listeningForKey = false;
                    shortcutButton.setMessage(shortcutLabel());
                    status = Component.translatable("screen.scriptautoce.settings.shortcutReset").getString();
                    statusWidget.setMessage(Component.literal(status));
                }).bounds(x + 155, y + 52, 35, 20).build());

        addRenderableWidget(Button.builder(serverSharingLabel(), button -> {
            serverSettings = new ServerSettings(!serverSettings.sharingEnabled(), serverSettings.maxSourceBytes(),
                    serverSettings.maxInstructions(), serverSettings.maxCommandsPerRun(),
                    serverSettings.maxCommandsPerTick(), serverSettings.maxRuntimeTicks(),
                    serverSettings.maxConcurrentPerPlayer(), serverSettings.maxConcurrentGlobal(),
                    serverSettings.maxUploadsPerPlayer(), serverSettings.maxSharedBytesPerPlayer(),
                    serverSettings.emergencyTickMillis(), serverSettings.blockedCommands(),
                    serverSettings.allowedCommands());
            button.setMessage(serverSharingLabel());
        }).bounds(x + 220, y, 200, 20).build());

        addRenderableWidget(new StringWidget(x + 220, y + 24, 120, 10,
                Component.translatable("screen.scriptautoce.settings.commands"), font));
        commandsPerTick = new EditBox(font, x + 220, y + 36, 80, 20,
                Component.translatable("screen.scriptautoce.settings.commands"));
        commandsPerTick.setValue(Integer.toString(serverSettings.maxCommandsPerTick()));
        commandsPerTick.setMaxLength(4);
        addRenderableWidget(commandsPerTick);

        addRenderableWidget(new StringWidget(x + 220, y + 60, 120, 10,
                Component.translatable("screen.scriptautoce.settings.runtime"), font));
        runtimeTicks = new EditBox(font, x + 220, y + 72, 80, 20,
                Component.translatable("screen.scriptautoce.settings.runtime"));
        runtimeTicks.setValue(Integer.toString(serverSettings.maxRuntimeTicks()));
        runtimeTicks.setMaxLength(6);
        addRenderableWidget(runtimeTicks);

        addRenderableWidget(new StringWidget(x + 220, y + 96, 190, 10,
                Component.translatable("screen.scriptautoce.settings.emergency"), font));
        emergencyMillis = new EditBox(font, x + 220, y + 108, 80, 20,
                Component.translatable("screen.scriptautoce.settings.emergency"));
        emergencyMillis.setValue(Integer.toString(serverSettings.emergencyTickMillis()));
        emergencyMillis.setMaxLength(5);
        addRenderableWidget(emergencyMillis);

        addRenderableWidget(Button.builder(Component.translatable("screen.scriptautoce.settings.saveServer"),
                button -> saveServer()).bounds(x + 220, y + 136, 120, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.scriptautoce.settings.reload"),
                button -> ClientNetworkController.requestServerSettings())
                .bounds(x + 346, y + 136, 74, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(width / 2 - 40, height - 28, 80, 20).build());
        statusWidget = addRenderableWidget(new StringWidget(x, height - 46, width - 2 * x, 10,
                Component.literal(status), font));
        if (!ClientUiState.serverAvailable()) {
            addRenderableWidget(new StringWidget(x + 220, y + 164, 190, 10,
                    Component.translatable("screen.scriptautoce.settings.noServer"), font));
        }
    }

    private void saveServer() {
        try {
            int commands = Integer.parseInt(commandsPerTick.getValue());
            int ticks = Integer.parseInt(runtimeTicks.getValue());
            int emergency = Integer.parseInt(emergencyMillis.getValue());
            serverSettings = new ServerSettings(serverSettings.sharingEnabled(), serverSettings.maxSourceBytes(),
                    serverSettings.maxInstructions(), serverSettings.maxCommandsPerRun(), commands, ticks,
                    serverSettings.maxConcurrentPerPlayer(), serverSettings.maxConcurrentGlobal(),
                    serverSettings.maxUploadsPerPlayer(), serverSettings.maxSharedBytesPerPlayer(),
                    emergency, serverSettings.blockedCommands(),
                    serverSettings.allowedCommands());
            ClientNetworkController.updateServerSettings(JsonFiles.GSON.toJson(serverSettings));
            status = Component.translatable("screen.scriptautoce.settings.sent").getString();
            statusWidget.setMessage(Component.literal(status));
        } catch (NumberFormatException exception) {
            status = Component.translatable("screen.scriptautoce.settings.invalid").getString();
            statusWidget.setMessage(Component.literal(status));
        }
    }

    private Component modeLabel() {
        return Component.translatable("screen.scriptautoce.settings.mode",
                Component.translatable("screen.scriptautoce.mode."
                        + clientSettings.executionMode().name().toLowerCase()));
    }

    private Component confirmLabel() {
        return Component.translatable("screen.scriptautoce.settings.confirm",
                Component.translatable(clientSettings.confirmBeforeRun() ? "options.on" : "options.off"));
    }

    private Component serverSharingLabel() {
        return Component.translatable("screen.scriptautoce.settings.sharing",
                Component.translatable(serverSettings.sharingEnabled() ? "options.on" : "options.off"));
    }

    private Component shortcutLabel() {
        return listeningForKey
                ? Component.translatable("screen.scriptautoce.settings.shortcutListening")
                : Component.translatable("screen.scriptautoce.settings.shortcut",
                ClientNetworkController.openLibraryKey().getTranslatedKeyMessage());
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (listeningForKey) {
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                listeningForKey = false;
            } else {
                ClientNetworkController.setOpenLibraryKey(InputConstants.getKey(event));
                listeningForKey = false;
                status = Component.translatable("screen.scriptautoce.settings.shortcutSaved").getString();
                statusWidget.setMessage(Component.literal(status));
            }
            shortcutButton.setMessage(shortcutLabel());
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, 14, 0xFFFFFF);
        graphics.text(font, Component.translatable("screen.scriptautoce.settings.client"),
                width / 2 - 210, 28, 0xA0A0A0);
        graphics.text(font, Component.translatable("screen.scriptautoce.settings.server"),
                width / 2 + 10, 28, 0xA0A0A0);
        if (!status.isBlank()) {
            graphics.text(font, status, width / 2 - 200, height - 46, 0xD0D0D0);
        }
        if (!ClientUiState.serverAvailable()) {
            graphics.text(font, Component.translatable("screen.scriptautoce.settings.noServer"),
                    width / 2 + 10, 140, 0xFFAA55);
        }
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
