package wintahh.rageutils.mixin;

import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wintahh.rageutils.RageActionBar;

@Mixin(InGameHud.class)
public class InGameHudMixin {
    @Inject(method = "setOverlayMessage", at = @At("HEAD"))
    private void rageutils$onSetOverlayMessage(Text message, boolean tinted, CallbackInfo ci) {
        RageActionBar.onExternalActionbar(message);
    }
}
