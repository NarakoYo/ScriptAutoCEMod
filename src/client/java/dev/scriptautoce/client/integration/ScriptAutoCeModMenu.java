package dev.scriptautoce.client.integration;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.scriptautoce.client.ui.ScriptSettingsScreen;

public final class ScriptAutoCeModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ScriptSettingsScreen::new;
    }
}
