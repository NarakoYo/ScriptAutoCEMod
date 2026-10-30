package dev.scriptautoce.mixin.client;

import dev.scriptautoce.client.editor.SyntaxHighlightRegistry;
import dev.scriptautoce.client.editor.SyntaxHighlightRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiLineEditBox.class)
public abstract class MultiLineEditBoxSyntaxMixin {
    @Shadow
    @Final
    private Font font;

    @Shadow
    @Final
    private MultilineTextField textField;

    @Inject(method = "extractContents", at = @At("TAIL"))
    private void scriptautoce$renderSyntaxHighlighting(GuiGraphicsExtractor graphics, int mouseX,
                                                       int mouseY, float partialTick, CallbackInfo callback) {
        MultiLineEditBox editor = (MultiLineEditBox) (Object) this;
        if (SyntaxHighlightRegistry.isEnabled(editor)) {
            SyntaxHighlightRenderer.render(graphics, editor, font, textField);
        }
    }
}
