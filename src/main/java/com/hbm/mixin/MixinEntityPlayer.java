package com.hbm.mixin;

import com.hbm.util.DamageResistanceHandler;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts all player setHealth calls to enforce the Hard Damage Cap (HDC).
 *
 * This mixin operates on two levels:
 *
 * 1. Per-call HDC enforcement (legacy path):
 *    If a single setHealth call would drop the player's health by more than
 *    the HDC in one go, the drop is clamped to the HDC. This handles cases
 *    like SRP's attackEntityAsMobMinimum that bypass Forge events entirely.
 *
 * 2. Per-tick accumulated damage floor (new path):
 *    Once the HDC has been triggered for a player this tick (either by the
 *    Forge LivingDamageEvent handler or by this mixin), a "health floor" is
 *    recorded via DamageResistanceHandler.setHDCFloor. Every subsequent
 *    setHealth call this tick — from any source, including potion effects such
 *    as SRP's needler — is checked against that floor. If the proposed health
 *    value would go below the floor, it is clamped to the floor, giving the
 *    player a full tick of invulnerability after the cap is hit.
 *
 * Only applies on the non-SEDNA path (isSEDNADamage == false) when the player
 * wears a full armor set that has an HDC configured.
 */
@Mixin(EntityLivingBase.class)
public abstract class MixinEntityPlayer {

    /**
     * Guards against reentrancy from our own clamped setHealth call below.
     * Thread-local so it is safe even if multiple players are processed simultaneously.
     */
    private static final ThreadLocal<Boolean> hbm$hdcActive = ThreadLocal.withInitial(() -> false);

    @Inject(method = "setHealth", at = @At("HEAD"), cancellable = true)
    private void hbm$enforceHDC(float health, CallbackInfo ci) {
        // Prevent reentrant calls triggered by our own correction below.
        if (hbm$hdcActive.get()) return;

        // Only apply outside of SEDNA.
        if (DamageResistanceHandler.isSEDNADamage) return;

        EntityLivingBase living = (EntityLivingBase) (Object) this;
        if (!(living instanceof EntityPlayer player)) return;

        // Server-side only — client health updates are cosmetic.
        if (player.world.isRemote) return;

        // Only intercept health reductions, not healing.
        float currentHealth = living.getHealth();
        if (health >= currentHealth) return;

        // --- Step 1: check the active per-tick floor first ---
        // This covers any source arriving after the HDC was already triggered
        // this tick (e.g. a potion effect tick, the SRP needler, a second mob hit).
        float floored = DamageResistanceHandler.applyHDCFloor(player, health);
        if (floored > health) {
            // The floor blocked some (or all) of this damage.
            ci.cancel();
            hbm$hdcActive.set(true);
            try {
                living.setHealth(floored);
            } finally {
                hbm$hdcActive.set(false);
            }
            return;
        }

        // --- Step 2: per-call HDC cap ---
        // If no floor is active yet, check whether this single hit exceeds the HDC.
        // This handles direct setHealth calls that bypass the Forge event system.
        // We only do this when isMobDamage returns true, matching the event handler's
        // scope, so that SEDNA damage and player-vs-player hits are unaffected.
        if (!DamageResistanceHandler.isMobDamage(living)) return;

        float hdc = DamageResistanceHandler.getHDCFor(player);
        if (hdc <= 0F) return;

        float proposedDamage = currentHealth - health;
        if (proposedDamage > hdc) {
            float clampedHealth = currentHealth - hdc;
            // Record the floor so the rest of this tick is protected.
            DamageResistanceHandler.setHDCFloor(player, clampedHealth);
            ci.cancel();
            hbm$hdcActive.set(true);
            try {
                living.setHealth(clampedHealth);
            } finally {
                hbm$hdcActive.set(false);
            }
        } else {
            // This hit is within the HDC but is the first hit this tick that matters.
            // Record the floor as health-after-hit so subsequent sources can't pile on.
            float floorAfterHit = currentHealth - hdc;
            DamageResistanceHandler.setHDCFloor(player, floorAfterHit);
        }
    }
}
