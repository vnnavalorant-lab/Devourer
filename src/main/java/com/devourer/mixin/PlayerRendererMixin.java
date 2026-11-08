package com.devourer.mixin;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Esconde as partes vanilla que o modelo zombie_form substitui:
 *  - braço esquerdo e perna direita (o modelo tem versões mutantes/segmentadas delas);
 *  - a segunda camada da skin (chapéu, jaqueta, mangas, calças), que ficava por cima do modelo.
 * A primeira camada da skin original continua visível por baixo do modelo.
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerRendererMixin {
    /** Coloque false para mostrar a segunda camada da skin (ela vai atravessar o modelo). */
    private static final boolean HIDE_SECOND_LAYER = true;

    @Inject(method = "setModelPose", at = @At("TAIL"), require = 0)
    private void devourer$hideParts(AbstractClientPlayerEntity player, CallbackInfo ci) {
        if (player.isSpectator() || player.isInvisible()) return;
        if (!com.devourer.client.ClientData.isInfected(player.getUuid())) return; // /infectado off: skin vanilla completa
        PlayerEntityModel<AbstractClientPlayerEntity> m = ((PlayerEntityRenderer) (Object) this).getModel();

        m.leftArm.visible = false;
        m.leftSleeve.visible = false;
        m.rightLeg.visible = false;
        m.rightPants.visible = false;

        if (HIDE_SECOND_LAYER) {
            m.hat.visible = false;
            m.jacket.visible = false;
            m.rightSleeve.visible = false;
            m.leftPants.visible = false;
        }
    }
}
