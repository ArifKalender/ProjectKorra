package com.projectkorra.projectkorra.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.EarthAbility;
import com.projectkorra.projectkorra.configuration.ConfigManager;

public class RevertChecker implements Runnable {

	private final ProjectKorra plugin;

	private static final FileConfiguration config = ConfigManager.defaultConfig.get();
	private static final boolean safeRevert = config.getBoolean("Properties.Earth.SafeRevert");
	public static Map<Block, Block> earthRevertQueue = new ConcurrentHashMap<>();
	static Map<Integer, Integer> airRevertQueue = new ConcurrentHashMap<>();

	private long time;

	public RevertChecker(final ProjectKorra bending) {
		this.plugin = bending;
	}

	public static void revertAirBlocks() {
		for (int id : airRevertQueue.keySet()) {
			Information info = EarthAbility.getTempAirLocations().get(id);
			TempBlock tempBlock = TempBlock.get(info.getBlock());
			if (tempBlock == null) {
				airRevertQueue.remove(info.getID());
				continue;
			}
			Location loc = tempBlock.getLocation();
			ProjectKorra.getSchedulerUtil().runLocation(() -> {
				EarthAbility.revertAirBlock(id);
				airRevertQueue.remove(id);
			}, loc);
		}
	}

	public static void revertEarthBlocks() {
		for (Block block : new HashSet<>(earthRevertQueue.keySet())) {
			Location loc = block.getLocation();

			ProjectKorra.getSchedulerUtil().runLocation(() -> {
				EarthAbility.revertBlock(block);
				earthRevertQueue.remove(block);
			}, loc);
		}
	}

	private void addToAirRevertQueue(final int i) {
		if (!airRevertQueue.containsKey(i)) {
			airRevertQueue.put(i, i);
		}
	}

	private void addToRevertQueue(final Block block) {
		ProjectKorra.getSchedulerUtil().runLocation(() -> {
			if (!earthRevertQueue.containsKey(block)) {
				earthRevertQueue.put(block, block);
			}
		}, block.getLocation());
	}

	@Override
	public void run() {
		if (!this.plugin.isEnabled()) {
			return;
		}

		this.time = System.currentTimeMillis();
		if (config.getBoolean("Properties.Earth.RevertEarthbending")) {

			try {
				final long revertCheckTime = config.getLong("Properties.Earth.RevertCheckTime");

				final Map<Block, Information> earth = new HashMap<>(EarthAbility.getMovedEarth());

				for (final Block block : earth.keySet()) {
					if (earthRevertQueue.containsKey(block)) {
						continue;
					}

					final Information info = earth.get(block);

					if (this.time > (info.getTime() + revertCheckTime)) {
						ProjectKorra.getSchedulerUtil().runLocation(() -> {
							if (!(safeRevert && playerInChunk(block))) {
								earthRevertQueue.putIfAbsent(block, block);
							}
						}, block.getLocation());
					}
				}

				final Map<Integer, Information> air = new HashMap<>(EarthAbility.getTempAirLocations());

				for (final Integer i : air.keySet()) {
					if (airRevertQueue.containsKey(i)) {
						continue;
					}

					final Information info = air.get(i);
					final Block block = info.getBlock();

					if (this.time > (info.getTime() + revertCheckTime)) {
						ProjectKorra.getSchedulerUtil().runLocation(() -> {
							if (!(safeRevert && playerInChunk(block))) {
								airRevertQueue.putIfAbsent(i, i);
							}
						}, block.getLocation());
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private static boolean playerInChunk(final Block b) {
		if (!b.getWorld().isChunkLoaded(b.getX() >> 4, b.getZ() >> 4)) {
			return false;
		}
		for (final Entity entity : b.getChunk().getEntities()) {
			if (entity instanceof Player) {
				return true;
			}
		}
		return false;
	}

}
