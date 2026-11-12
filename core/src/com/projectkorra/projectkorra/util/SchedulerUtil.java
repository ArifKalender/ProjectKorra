package com.projectkorra.projectkorra.util;

import com.projectkorra.projectkorra.ProjectKorra;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.TimeUnit;

public class SchedulerUtil {

    public final static boolean IS_FOLIA;
    static {
        boolean isFolia;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            isFolia = true;
        } catch (ClassNotFoundException ignored) {
            isFolia = false;
        }
        IS_FOLIA = isFolia;
    }

    public PKTask runEntity(Runnable runnable, Entity entity) {
        return new PKTask(runnable, entity).run();
    }
    public PKTask runLocation(Runnable runnable, Location location) {
        return new PKTask(runnable, location).run();
    }
    public PKTask runGlobal(Runnable runnable) {
        return new PKTask(runnable, false).run();
    }
    public PKTask runAsync(Runnable runnable) {
        return new PKTask(runnable, true).run();
    }


    public PKTask runEntityDelayed(Runnable runnable, Entity entity, long delay) {
        return new PKTask(runnable, entity, delay).runDelayed();
    }
    public PKTask runLocationDelayed(Runnable runnable, Location location, long delay) {
        return new PKTask(runnable, location, delay).runDelayed();
    }
    public PKTask runGlobalDelayed(Runnable runnable, long delay) {
        return new PKTask(runnable, false, delay).runDelayed();
    }
    public PKTask runAsyncDelayed(Runnable runnable, long delay) {
        return new PKTask(runnable, true, delay).runDelayed();
    }

    public PKTask runEntityRepeating(Runnable runnable, Entity entity, long delay, long interval) {
        return new PKTask(runnable, entity, delay, interval).runRepeating();
    }
    public PKTask runLocationRepeating(Runnable runnable, Location location, long delay, long interval) {
        return new PKTask(runnable, location, delay, interval).runRepeating();
    }
    public PKTask runGlobalRepeating(Runnable runnable, long delay, long interval) {
        return new PKTask(runnable, false, delay, interval).runRepeating();
    }
    public PKTask runAsyncRepeating(Runnable runnable, long delay, long interval) {
        return new PKTask(runnable, true, delay, interval).runRepeating();
    }




    private class PKTask {
        Runnable runnable;
        Entity entity;
        Location location;
        boolean isAsync;
        long delay;
        long interval;
        Object task;

        PKTask(Runnable runnable, Entity entity, Location location, boolean isAsync, long delay, long interval) {
            this.runnable=runnable;
            this.entity=entity;
            this.location=location;
            this.isAsync=isAsync;
            this.delay=delay;
            this.interval=interval;
        }

        PKTask(Runnable runnable, Entity entity){
            this(runnable, entity, null, false, 0, 0);
        }
        PKTask(Runnable runnable, Location location){
            this(runnable, null, location, false, 0, 0);
        }
        PKTask(Runnable runnable, boolean isAsync) {
            this(runnable, null, null, isAsync, 0, 0);
        }

        PKTask(Runnable runnable, Entity entity, long delay){
            this(runnable, entity, null, false, delay, 0);
        }
        PKTask(Runnable runnable, Location location, long delay){
            this(runnable, null, location, false, delay, 0);
        }
        PKTask(Runnable runnable, boolean isAsync, long delay) {
            this(runnable, null, null, isAsync, delay, 0);
        }

        PKTask(Runnable runnable, Entity entity, long delay, long interval){
            this(runnable, entity, null, false, delay, interval);
        }
        PKTask(Runnable runnable, Location location, long delay, long interval){
            this(runnable, null, location, false, delay, interval);
        }
        PKTask(Runnable runnable, boolean isAsync, long delay, long interval) {
            this(runnable, null, null, isAsync, delay, interval);
        }

        public PKTask run() {
            if (!IS_FOLIA) {
                if (isAsync) task = Bukkit.getScheduler().runTaskAsynchronously(ProjectKorra.plugin, runnable);
                else task = Bukkit.getScheduler().runTask(ProjectKorra.plugin, runnable);
            }
            else if (entity != null) task = entity.getScheduler().run(ProjectKorra.plugin, t -> runnable.run(), null);
            else if (location != null) task = Bukkit.getRegionScheduler().run(ProjectKorra.plugin, location, t -> runnable.run());
            else if (isAsync) task = Bukkit.getAsyncScheduler().runNow(ProjectKorra.plugin, t -> runnable.run());
            else task = Bukkit.getGlobalRegionScheduler().run(ProjectKorra.plugin, t -> runnable.run());
            return this;
        }
        public PKTask runDelayed() {
            if (!IS_FOLIA) {
                if (isAsync) task = Bukkit.getScheduler().runTaskLaterAsynchronously(ProjectKorra.plugin, runnable, delay);
                else task = Bukkit.getScheduler().runTaskLater(ProjectKorra.plugin, runnable, delay);
            }
            else if (entity != null) task = entity.getScheduler().runDelayed(ProjectKorra.plugin, t -> runnable.run(), null, delay);
            else if (location != null) task = Bukkit.getRegionScheduler().runDelayed(ProjectKorra.plugin, location, t -> runnable.run(), delay);
            else if (isAsync) {
                long foliaDelay = delay * 50L;
                // Different scheduling models require different time units
                // due to how they are designed. This conversion feels hacky
                // but whatever, TimeUnit (obviously) doesn't support TICKS :(
                task = Bukkit.getAsyncScheduler().runDelayed(ProjectKorra.plugin, t -> runnable.run(), foliaDelay, TimeUnit.MILLISECONDS);
            }
            else task = Bukkit.getGlobalRegionScheduler().runDelayed(ProjectKorra.plugin, t -> runnable.run(), delay);
            return this;
        }

        public PKTask runRepeating() {
            if (!IS_FOLIA) {
                if (isAsync) task = Bukkit.getScheduler().runTaskTimerAsynchronously(ProjectKorra.plugin, runnable, delay, interval);
                else task = Bukkit.getScheduler().runTaskTimer(ProjectKorra.plugin, runnable, delay, interval);
            }
            else if (entity != null) task = entity.getScheduler().runAtFixedRate(ProjectKorra.plugin, t -> runnable.run(), null, delay, interval);
            else if (location != null) task = Bukkit.getRegionScheduler().runAtFixedRate(ProjectKorra.plugin, location, t -> runnable.run(), delay, interval);
            else if (isAsync) {
                long foliaDelay = delay * 50L;
                long foliaInterval = interval * 50L;
                task = Bukkit.getAsyncScheduler().runAtFixedRate(ProjectKorra.plugin, t -> runnable.run(), foliaDelay, foliaInterval, TimeUnit.MILLISECONDS);
            }
            else task = Bukkit.getGlobalRegionScheduler().runAtFixedRate(ProjectKorra.plugin, t -> runnable.run(), delay, interval);
            return this;
        }
        public void cancel() {
            if (task == null) return;
            if (IS_FOLIA) ((ScheduledTask) task).cancel();
            else ((BukkitTask) task).cancel();
        }
    }
}