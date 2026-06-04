package wintahh.rageutils.mixin;

import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.feature.HeadFeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wintahh.rageutils.HudConfig;

@Mixin(ArmorFeatureRenderer.class)
public class LivingEntityRendererMixin<S extends BipedEntityRenderState, M extends BipedEntityModel<S>> {

    @Inject(
        method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/BipedEntityRenderState;FF)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void hideArmor(MatrixStack matrices, OrderedRenderCommandQueue queue,
                           int light, S state, float limbAngle, float limbDistance,
                           CallbackInfo ci) {
        if (HudConfig.INSTANCE.hideArmor) ci.cancel();
    }
}
