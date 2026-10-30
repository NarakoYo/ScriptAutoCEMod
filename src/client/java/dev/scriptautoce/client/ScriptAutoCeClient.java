package dev.scriptautoce.client;

import dev.scriptautoce.client.network.ClientNetworkController;
import dev.scriptautoce.client.ui.ClientUiState;
import net.fabricmc.api.ClientModInitializer;

public final class ScriptAutoCeClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientUiState.initialize();
        ClientNetworkController.initialize();
    }
}
