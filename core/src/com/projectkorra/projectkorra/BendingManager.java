package com.projectkorra.projectkorra;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projectkorra.projectkorra.event.WorldTimeEvent;
import com.projectkorra.projectkorra.util.*;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.ability.ElementalAbility;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.object.HorizontalVelocityTracker;

//TODO: Migrate scheduler
public class BendingManager implements Runnable {

	private static BendingManager instance;
	@Deprecated(since = "1.13.0", forRemoval = true)
	public static HashMap<World, String> events = new HashMap<>(); // holds any current event.

	long time;
	long interval;
	private final Map<World, WorldTimeEvent.Time> times = new ConcurrentHashMap<World, WorldTimeEvent.Time>(); // true if day time
	private final TempBlock.TempBlockRevertTask tempBlockRevertTask = new TempBlock.TempBlockRevertTask();
	public BendingManager() {
		instance = this;
		this.time = System.currentTimeMillis();

		times.clear();

		handleDayNight();
	}

	public static BendingManager getInstance() {
		return instance;
	}

	public void handleCooldowns() {
		for (Map.Entry<UUID, BendingPlayer> entry : BendingPlayer.getPlayers().entrySet()) {
			BendingPlayer bPlayer = entry.getValue();

			bPlayer.removeOldCooldowns();
		}
	}



	public void handleDayNight() {
		for (World world : Bukkit.getWorlds()) {
			if (ConfigManager.defaultConfig.get()
					.getStringList("Properties.DisabledWorlds")
					.contains(world.getName())) {
				continue;
			}

			ProjectKorra.getSchedulerUtil().runGlobal(() -> {
				WorldTimeEvent.Time from = times.get(world);
				WorldTimeEvent.Time to = getWorldTime(world);

				if (from == null) {
					from = WorldTimeEvent.Time.values()[
							(to.ordinal() - 1 + WorldTimeEvent.Time.values().length)
									% WorldTimeEvent.Time.values().length
							];
				}

				if (from == to) {
					return;
				}

				Bukkit.getPluginManager().callEvent(
						new WorldTimeEvent(world, from, to)
				);

				times.put(world, to);

				if (GeneralMethods.getRPG() == null) {
					sendDayNightMessages(world, from, to);
				}
			});
		}
	}

	private WorldTimeEvent.Time getWorldTime(World world) {
		if (ElementalAbility.isDay(world)) {
			return WorldTimeEvent.Time.DAY;
		}
		if (ElementalAbility.isNight(world)) {
			return WorldTimeEvent.Time.NIGHT;
		}
		if (ElementalAbility.isDusk(world)) {
			return WorldTimeEvent.Time.DUSK;
		}
		return WorldTimeEvent.Time.DAWN;
	}

	private void sendDayNightMessages(World world, WorldTimeEvent.Time from,
									  WorldTimeEvent.Time to) {
		for (Player player : world.getPlayers()) {
			ProjectKorra.getSchedulerUtil().runEntity(() -> {
				BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);

				if (bPlayer == null) {
					return;
				}

				boolean water = bPlayer.hasElement(Element.WATER);
				boolean fire = bPlayer.hasElement(Element.FIRE);

				if (water && player.hasPermission("bending.message.daymessage")
						&& from == WorldTimeEvent.Time.NIGHT
						&& to != WorldTimeEvent.Time.NIGHT) {
					player.sendMessage(Element.WATER.getColor() + getMoonsetMessage());
				} else if (water && player.hasPermission("bending.message.nightmessage")
						&& to == WorldTimeEvent.Time.NIGHT) {
					player.sendMessage(Element.WATER.getColor() + getMoonriseMessage());
				}

				if (fire && player.hasPermission("bending.message.nightmessage")
						&& from == WorldTimeEvent.Time.DAY
						&& to != WorldTimeEvent.Time.DAY) {
					player.sendMessage(Element.FIRE.getColor() + getSunsetMessage());
				} else if (fire && player.hasPermission("bending.message.daymessage")
						&& to == WorldTimeEvent.Time.DAY) {
					player.sendMessage(Element.FIRE.getColor() + getSunriseMessage());
				}
			}, player);
		}
	}

	@Override
	public void run() {
		this.interval = System.currentTimeMillis() - this.time;
		this.time = System.currentTimeMillis();
		ProjectKorra.time_step = this.interval;

		CoreAbility.progressAll();
		TempPotionEffect.progressAll();
		this.handleDayNight();
		RevertChecker.revertAirBlocks();
		HorizontalVelocityTracker.updateAll();
		this.handleCooldowns();
		TempArmor.cleanup();

		TempFallingBlock.manage();

		tempBlockRevertTask.run();
	}

	public static String getSunriseMessage() {
		return ChatUtil.color(ConfigManager.languageConfig.get().getString("Extras.Fire.DayMessage"));
	}

	public static String getSunsetMessage() {
		return ChatUtil.color(ConfigManager.languageConfig.get().getString("Extras.Fire.NightMessage"));
	}

	public static String getMoonriseMessage() {
		return ChatUtil.color(ConfigManager.languageConfig.get().getString("Extras.Water.NightMessage"));
	}

	public static String getMoonsetMessage() {
		return ChatUtil.color(ConfigManager.languageConfig.get().getString("Extras.Water.DayMessage"));
	}

	/**
	 * A runnable that manages temp elements for players.
	 * It runs for online players and only polls the next element that is due to expire.
	 * This runnable runs every 20 ticks (1 second).
	 */
	public static class TempElementsRunnable implements Runnable {
		//TODO: Migrate scheduler
		@Override
		public void run() {
			//Manage Temp elements
			while (!BendingPlayer.TEMP_ELEMENTS.isEmpty()) { //We use a while loop so if multiple expire in the same tick, all are done together
				Pair<Player, Long> pair = BendingPlayer.TEMP_ELEMENTS.peek();

				if (System.currentTimeMillis() > pair.getRight()) { //Check if the top temp element has expired
					BendingPlayer.TEMP_ELEMENTS.poll(); //And if it has, remove from the queue, and recalculate temp elements for that player
					BendingPlayer.getBendingPlayer(pair.getLeft()).recalculateTempElements(false);
				} else {
					break; //Break the loop if the top element hasn't expired, as all elements below it won't have either
				}
			}
		}
	}

}
