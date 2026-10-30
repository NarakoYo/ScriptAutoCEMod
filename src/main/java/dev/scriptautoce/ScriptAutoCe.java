package dev.scriptautoce;

import carpet.CarpetServer;
import dev.scriptautoce.network.ScriptPayloads;
import dev.scriptautoce.server.ScriptAutoCeCarpetExtension;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ScriptAutoCe implements ModInitializer {
    public static final String MOD_ID = "scriptautoce";
    public static final String VERSION = "0.1.0-alpha";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ScriptPayloads.registerPayloadTypes();
        ScriptPayloads.registerServerReceivers();
        CarpetServer.manageExtension(new ScriptAutoCeCarpetExtension());
        LOGGER.info("Script Auto CE {} initialized", VERSION);
    }
}
