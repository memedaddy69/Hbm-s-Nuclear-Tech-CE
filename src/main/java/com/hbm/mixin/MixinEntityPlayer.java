package com.hbm.mixin;

import com.hbm.util.DamageResistanceHandler;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts player setHealth calls to enforce the Hard Damage Cap (HDC).
 * This catches direct setHealth() calls that bypass the Forge event system entirely,
 * such as SRP's minimum damage enforcement in attackEntityAsMobMinimum.
 *
 * The HDC is a set bonus: if the player wears a full set with an HDC configured,
 * their health cannot drop by more than HDC points from a single setHealth call.
 * Only applies on the non-SEDNA path (isSEDNADamage == false).
 */
@Mixin(EntityLivingBase.class)
public abstract class MixinEntityPlayer {

    /**
     * Guards against reentrancy from our own clamped setHealth call below.
     * Thread-local so it's safe even if multiple players are processed simultaneously.
     */
    private static final ThreadLocal<Boolean> hbm$hdcActive = ThreadLocal.withInitial(() -> false);

    @Inject(method = "setHealth", at = @At("HEAD"), cancellable = true)
    private void hbm$enforceHDC(float health, CallbackInfo ci) {
        // Prevent reentrant calls from our own correction below.
        if (hbm$hdcActive.get()) return;

        // Only apply outside of SEDNA.
        if (DamageResistanceHandler.isSEDNADamage) return;

        EntityLivingBase living = (EntityLivingBase) (Object) this;
        if (!(living instanceof EntityPlayer)) return;
        if (!DamageResistanceHandler.isMobDamage(living)) return;

        EntityPlayer self = (EntityPlayer) living;
        float currentHealth = living.getHealth();

        // Only intercept health reductions, not healing.
        if (health >= currentHealth) return;

        float hdc = DamageResistanceHandler.getHDCFor(self);
        if (hdc <= 0F) return;

        float proposedDamage = currentHealth - health;
        if (proposedDamage > hdc) {
            ci.cancel();
            hbm$hdcActive.set(true);
            try {
                living.setHealth(currentHealth - hdc);
            } finally {
                hbm$hdcActive.set(false);
            }
        }
    }
}
