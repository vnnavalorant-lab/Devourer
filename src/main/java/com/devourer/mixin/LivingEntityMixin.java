package com.devourer.mixin;

import com.devourer.tentacle.AnchorFlags;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Âncora de Aderência: com a habilidade ativa, o jogador conta como "escalando" (como numa escada) quando está
 * encostado numa parede. Aí o próprio jogo cuida do resto: avançar/pular contra a parede sobe, agachar segura,
 * soltar escorrega devagar e a queda é zerada. Roda no cliente e no servidor (por isso é um mixin comum).
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "isClimbing", at = @At("HEAD"), cancellable = true, require = 0)
    private void devourer$anchorClimb(CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof PlayerEntity p) || p.isSpectator() || !AnchorFlags.isAnchored(p)) return;
        // parede colada ao corpo (só nas laterais, para o chão sob os pés não contar)
        Box wallBox = p.getBoundingBox().expand(0.12, 0.0, 0.12);
        boolean wall = !p.getWorld().isSpaceEmpty(p, wallBox);
        // no chão só começa a escalar se estiver empurrando contra a parede
        if (wall && (!p.isOnGround() || p.horizontalCollision)) cir.setReturnValue(true);
    }
}
