package com.projectkorra.projectkorra.firebending;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import com.projectkorra.projectkorra.attribute.markers.DayNightFactor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.projectkorra.projectkorra.Element.SubElement;
import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ability.AirAbility;
import com.projectkorra.projectkorra.ability.BlueFireAbility;
import com.projectkorra.projectkorra.ability.FireAbility;
import com.projectkorra.projectkorra.attribute.Attribute;
import com.projectkorra.projectkorra.firebending.util.FireDamageTimer;
import com.projectkorra.projectkorra.util.DamageHandler;
import com.projectkorra.projectkorra.util.TempBlock;

public class WallOfFire extends FireAbility {

	private int damageTick;
	private int intervalTick;
	@Attribute(Attribute.RANGE) @DayNightFactor
	private double range;
	@Attribute(Attribute.HEIGHT) @DayNightFactor
	private double height;
	@Attribute(Attribute.WIDTH) @DayNightFactor
	private double width;
	@Attribute(Attribute.DAMAGE) @DayNightFactor
	private double damage;
	@Attribute(Attribute.COOLDOWN) @DayNightFactor(invert = true)
	private long cooldown;
	private long damageInterval;
	@Attribute(Attribute.DURATION) @DayNightFactor
	private long duration;
	private long time;
	private long interval;
	@Attribute(Attribute.FIRE_TICK) @DayNightFactor
	private double fireTicks;
	private double maxAngle;
	private Random random;
	private Location origin;
	private List<Block> blocks;

	public WallOfFire(final Player player) {
		super(player);

		this.maxAngle = getConfig().getDouble("Abilities.Fire.WallOfFire.MaxAngle");
		this.interval = getConfig().getLong("Abilities.Fire.WallOfFire.Interval");
		this.range = getConfig().getDouble("Abilities.Fire.WallOfFire.Range");
		this.height = getConfig().getDouble("Abilities.Fire.WallOfFire.Height");
		this.width = getConfig().getDouble("Abilities.Fire.WallOfFire.Width");
		this.damage = getConfig().getDouble("Abilities.Fire.WallOfFire.Damage");
		this.cooldown = getConfig().getLong("Abilities.Fire.WallOfFire.Cooldown");
		this.damageInterval = getConfig().getLong("Abilities.Fire.WallOfFire.DamageInterval");
		this.duration = getConfig().getLong("Abilities.Fire.WallOfFire.Duration");
		this.fireTicks = getConfig().getDouble("Abilities.Fire.WallOfFire.FireTicks");
		this.random = new Random();
		this.blocks = new ArrayList<>();

		if (hasAbility(player, WallOfFire.class)) {
			return;
		} else if (this.bPlayer.isOnCooldown(this)) {
			return;
		}

		this.origin = GeneralMethods.getTargetedLocation(player, this.range);

		this.time = System.currentTimeMillis();
		final Block block = this.origin.getBlock();
		if (block.isLiquid() || GeneralMethods.isSolid(block)) {
			return;
		}

		final Vector direction = player.getEyeLocation().getDirection();
		final Vector compare = direction.clone();
		compare.setY(0);
		if (Math.abs(direction.angle(compare)) > Math.toRadians(this.maxAngle)) {
			return;
		}

		this.recalculateAttributes();
		this.initializeBlocks();
		this.start();
	}

	private void affect(final Entity entity) {
		GeneralMethods.setVelocity(this, entity, new Vector(0, 0, 0));
		if (entity instanceof LivingEntity) {
			final Block block = ((LivingEntity) entity).getEyeLocation().getBlock();
			if (TempBlock.isTempBlock(block) && isIce(block)) {
				return;
			}
			DamageHandler.damageEntity(entity, this.damage, this);
			AirAbility.breakBreathbendingHold(entity);
		}
		entity.setFireTicks((int) (this.fireTicks * 20));
		new FireDamageTimer(entity, this.player, this);
	}

	private void damage() {
		double radius = this.height;
		if (radius < this.width) {
			radius = this.width;
		}

		radius = radius + 1;
		final List<Entity> entities = GeneralMethods.getEntitiesAroundPoint(this.origin, radius);
		if (entities.contains(this.player)) {
			entities.remove(this.player);
		}
		for (final Entity entity : entities) {
			if (GeneralMethods.isRegionProtectedFromBuild(this, entity.getLocation())) {
				continue;
			}
			for (final Block block : this.blocks) {
				if (entity.getLocation().distanceSquared(block.getLocation()) <= 1.5 * 1.5) {
					this.affect(entity);
					break;
				}
			}
		}
	}

	private void display() {
		for (final Block block : this.blocks) {
			if (!this.isTransparent(block)) {
				dryWetBlocks(block, this, ThreadLocalRandom.current().nextInt(5) == 0);
				continue;
			}

			playFirebendingParticles(block.getLocation(), 3, 0.6, 0.6, 0.6);
			emitFirebendingLight(block.getLocation());
			if (this.random.nextInt(7) == 0) {
				playFirebendingSound(block.getLocation());
			}
		}
	}

	private void initializeBlocks() {
		this.recalculateAttributes();

		Vector direction = this.player.getEyeLocation().getDirection();
		direction = direction.normalize();

		Vector ortholr = GeneralMethods.getOrthogonalVector(direction, 0, 1);
		ortholr = ortholr.normalize();

		Vector orthoud = GeneralMethods.getOrthogonalVector(direction, 90, 1);
		orthoud = orthoud.normalize();

		final double w = this.width;
		final double h = this.height;

		for (double i = -w; i <= w; i++) {
			for (double j = -h; j <= h; j++) {
				Location location = this.origin.clone().add(orthoud.clone().multiply(j));
				location = location.add(ortholr.clone().multiply(i));
				if (GeneralMethods.isRegionProtectedFromBuild(this, location)) {
					continue;
				}
				final Block block = location.getBlock();
				if (!this.blocks.contains(block)) {
					this.blocks.add(block);
				}
			}
		}
	}

	@Override
	public void progress() {
		this.time = System.currentTimeMillis();

		if (this.time > this.getStartTime() + this.duration) {
			this.remove();
			return;
		}
		if (this.time - this.getStartTime() > this.intervalTick * this.interval) {
			this.intervalTick++;
			this.display();
		}
		if (this.time - this.getStartTime() > this.damageTick * this.damageInterval) {
			this.damageTick++;
			this.damage();
		}
	}

	@Override
	public void remove() {
		super.remove();
		this.bPlayer.addCooldown(this);
	}

	@Override
	public String getName() {
		return "WallOfFire";
	}

	@Override
	public Location getLocation() {
		return this.origin;
	}

	@Override
	public long getCooldown() {
		return this.cooldown;
	}

	@Override
	public boolean isSneakAbility() {
		return false;
	}

	@Override
	public boolean isHarmlessAbility() {
		return false;
	}

	@Override
	public List<Location> getLocations() {
		final ArrayList<Location> locations = new ArrayList<>();
		for (final Block block : this.blocks) {
			locations.add(block.getLocation());
		}
		return locations;
	}

	public int getDamageTick() {
		return this.damageTick;
	}

	public void setDamageTick(final int damageTick) {
		this.damageTick = damageTick;
	}

	public int getIntervalTick() {
		return this.intervalTick;
	}

	public void setIntervalTick(final int intervalTick) {
		this.intervalTick = intervalTick;
	}

	public double getRange() {
		return this.range;
	}

	public void setRange(final int range) {
		this.range = range;
	}

	public double getHeight() {
		return this.height;
	}

	public void setHeight(final int height) {
		this.height = height;
	}

	public double getWidth() {
		return this.width;
	}

	public void setWidth(final int width) {
		this.width = width;
	}

	public double getDamage() {
		return this.damage;
	}

	public void setDamage(final int damage) {
		this.damage = damage;
	}

	public long getDamageInterval() {
		return this.damageInterval;
	}

	public void setDamageInterval(final long damageInterval) {
		this.damageInterval = damageInterval;
	}

	public long getDuration() {
		return this.duration;
	}

	public void setDuration(final long duration) {
		this.duration = duration;
	}

	public long getTime() {
		return this.time;
	}

	public void setTime(final long time) {
		this.time = time;
	}

	public long getInterval() {
		return this.interval;
	}

	public void setInterval(final long interval) {
		this.interval = interval;
	}

	public double getFireTicks() {
		return this.fireTicks;
	}

	public void setFireTicks(final double fireTicks) {
		this.fireTicks = fireTicks;
	}

	public double getMaxAngle() {
		return this.maxAngle;
	}

	public void setMaxAngle(final double maxAngle) {
		this.maxAngle = maxAngle;
	}

	public Location getOrigin() {
		return this.origin;
	}

	public void setOrigin(final Location origin) {
		this.origin = origin;
	}

	public List<Block> getBlocks() {
		return this.blocks;
	}

	public void setCooldown(final long cooldown) {
		this.cooldown = cooldown;
	}

}
