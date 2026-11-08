package com.devourer.mixin;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * O braço esquerdo do modelo zombie_form é mutante e substitui o braço esquerdo vanilla,
 * então o vanilla é escondido (o resto da skin original continua visível por baixo do modelo).
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerRendererMixin {
    @Inject(method = "setModelPose", at = @At("TAIL"), require = 0)
    private void devourer$hideLeftArm(AbstractClientPlayerEntity player, CallbackInfo ci) {
        if (player.isSpectator() || player.isInvisible()) return;
        PlayerEntityModel<AbstractClientPlayerEntity> model = ((PlayerEntityRenderer) (Object) this).getModel();
        model.leftArm.visible = false;
        model.leftSleeve.visible = false;
    }
}
