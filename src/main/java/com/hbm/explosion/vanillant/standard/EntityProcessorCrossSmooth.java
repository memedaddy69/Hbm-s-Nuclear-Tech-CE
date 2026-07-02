package com.hbm.explosion.vanillant.standard;

import com.hbm.explosion.vanillant.ExplosionVNT;
import com.hbm.items.weapon.sedna.BulletConfig;
import com.hbm.items.weapon.sedna.factory.ConfettiUtil;
import com.hbm.util.EntityDamageUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.DamageSource;

import static com.hbm.util.DamageResistanceHandler.DamageClass;

public class EntityProcessorCrossSmooth extends EntityProcessorCross {

    protected float fixedDamage;
    protected float pierceDT = 0;
    protected float pierceDR = 0;
    protected DamageClass clazz = DamageClass.EXPLOSIVE;

    public EntityProcessorCrossSmooth(double nodeDist, float fixedDamage) {
        super(nodeDist);
        this.fixedDamage = fixedDamage;
        this.setAllowSelfDamage();
    }

    public EntityProcessorCrossSmooth setupPiercing(float pierceDT, float pierceDR) {
        this.pierceDT = pierceDT;
        this.pierceDR = pierceDR;
        return this;
    }

    public EntityProcessorCrossSmooth setDamageClass(DamageClass clazz) {
        this.clazz = clazz;
        return this;
    }

    @Override
    public void attackEntity(Entity entity, ExplosionVNT source, float amount) {
        if (!entity.isEntityAlive()) return;
        if (source.exploder == entity) amount *= 0.5F;
        DamageSource dmg = BulletConfig.getDamage(null, source.exploder instanceof EntityLivingBase ? (EntityLivingBase) source.exploder : null, clazz);
        if (!(entity instanceof EntityLivingBase)) {
            entity.attackEntityFrom(dmg, amount);
        } else {
            EntityLivingBase living = (EntityLivingBase) entity;
            if (clazz == DamageClass.FIRE) {
                EntityLivingBase trueAttacker = source.compat != null ? source.compat.getExplosivePlacedBy() : null;
                if (trueAttacker == null && source.exploder instanceof EntityLivingBase) {
                    trueAttacker = (EntityLivingBase) source.exploder;
                }
                EntityDamageUtil.attackEntityFromNTUsingVanillaSource(living, DamageSource.ON_FIRE, trueAttacker, amount, true, false, 0F, pierceDT, pierceDR, false);
            } else {
                EntityDamageUtil.attackEntityFromNT(living, dmg, amount, true, false, 0F, pierceDT, pierceDR);
            }
            if (!living.isEntityAlive()) ConfettiUtil.decideConfetti(living, dmg);
        }
    }

    @Override
    public float calculateDamage(double distanceScaled, double density, double knockback, float size) {
        return (float) (fixedDamage * (1 - distanceScaled));
    }
}
