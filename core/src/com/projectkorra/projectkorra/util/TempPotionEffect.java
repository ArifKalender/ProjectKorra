package com.projectkorra.projectkorra.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.projectkorra.projectkorra.ProjectKorra;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;

public class TempPotionEffect {

    private static final Map<LivingEntity, TempPotionEffect> instances = new ConcurrentHashMap<LivingEntity, TempPotionEffect>();
    private static final long tick = 21;

    private AtomicInteger ID = new AtomicInteger(Integer.MIN_VALUE);
    private final Map<Integer, PotionInfo> infos = new ConcurrentHashMap<Integer, PotionInfo>();
    private final LivingEntity entity;

    public TempPotionEffect(final LivingEntity entity, final PotionEffect effect) {
        this(entity, effect, System.currentTimeMillis());
    }

    public TempPotionEffect(final LivingEntity entity, final PotionEffect effect, final long starttime) {
        this.entity = entity;
        ProjectKorra.getSchedulerUtil().runEntity(() -> {
            instances.compute(entity, (entityKey, instance) -> {
                if (instance == null) instance = this;
                instance.infos.put(
                        instance.ID.incrementAndGet(),
                        new PotionInfo(starttime, effect));
                return instance;
            });

        }, entity);
    }

    public static void progressAll() {
        for (final TempPotionEffect effect : instances.values()) {
            effect.progress();
        }
    }

    private void addEffect(final PotionEffect effect) {
        for (final PotionEffect peffect : this.entity.getActivePotionEffects()) {
            if (!peffect.getType().equals(effect.getType())) continue;
            final boolean stronger = peffect.getAmplifier() > effect.getAmplifier();
            final boolean longer = peffect.getDuration() > effect.getDuration();
            if (stronger) {
                if (!longer) {
                    final int dt = effect.getDuration() - peffect.getDuration();
                    final PotionEffect newEffect = new PotionEffect(
                            effect.getType(), dt, effect.getAmplifier()
                    );
                    new TempPotionEffect(this.entity, newEffect,
                            System.currentTimeMillis() + peffect.getDuration() * tick);
                }
                return;
            }

            this.entity.removePotionEffect(peffect.getType());
            this.entity.addPotionEffect(effect);

            if (longer) {
                final int dt = peffect.getDuration() - effect.getDuration();
                final PotionEffect newEffect = new PotionEffect(peffect.getType(), dt, peffect.getAmplifier());
                new TempPotionEffect(this.entity, newEffect, System.currentTimeMillis() + effect.getDuration() * tick);
            }
            return;
        }

        this.entity.addPotionEffect(effect);
    }

    private void progress() {
        ProjectKorra.getSchedulerUtil().runEntity(() -> {
            for (final int id : this.infos.keySet()) {
                final PotionInfo info = this.infos.get(id);

                if (info.getTime() < System.currentTimeMillis()) {
                    this.addEffect(info.getEffect());
                    this.infos.remove(id);
                }
            }

            instances.computeIfPresent(this.entity, (key, instance) -> {
                if (instance != this) return instance;
                return this.infos.isEmpty() ? null : instance;
            });

        }, this.entity).runRetired(() -> {
            instances.remove(this.entity, this);
        });
    }

    private record PotionInfo(long starttime, PotionEffect effect) {
        public long getTime() {
            return this.starttime;
        }

        public PotionEffect getEffect() {
            return this.effect;
        }
    }

}
