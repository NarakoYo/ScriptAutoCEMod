package dev.scriptautoce.mixin.client;

import dev.scriptautoce.client.chat.ChatHintController;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
    @Shadow
    @Final
    protected EditBox input;

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void scriptautoce$completeHint(KeyEvent event, CallbackInfoReturnable<Boolean> callback) {
        if (event.key() != GLFW.GLFW_KEY_TAB) {
            return;
        }
        String completion = ChatHintController.cycleCompletion(this, input.getValue(),
                event.hasShiftDown());
        if (completion != null) {
            input.setValue(completion);
            input.setCursorPosition(completion.length());
            callback.setReturnValue(true);
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void scriptautoce$renderHints(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                          float partialTick, CallbackInfo callback) {
        ChatHintController.render(this, graphics, ((Screen) (Object) this).getFont(),
                input.getValue(), input.getX(), input.getY(), input.getWidth(), mouseX, mouseY);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void scriptautoce$clickHint(MouseButtonEvent event, boolean doubleClick,
                                        CallbackInfoReturnable<Boolean> callback) {
        String completion = ChatHintController.mouseClicked(this, input.getValue(),
                event.x(), event.y(), event.button());
        if (completion != null) {
            input.setValue(completion);
            input.setCursorPosition(completion.length());
            callback.setReturnValue(true);
        }
    }
}
