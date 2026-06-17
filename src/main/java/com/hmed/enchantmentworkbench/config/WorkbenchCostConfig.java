package com.hmed.enchantmentworkbench.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hmed.enchantmentworkbench.EnchantmentWorkbenchMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WorkbenchCostConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static CostData data = defaults();

	private WorkbenchCostConfig() {
	}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("enchantment_workbench_v2.json");
		if (Files.notExists(path)) {
			writeDefaults(path);
			return;
		}

		try (Reader reader = Files.newBufferedReader(path)) {
			CostData loaded = GSON.fromJson(reader, CostData.class);
			if (loaded != null && loaded.xpCosts != null && loaded.lapisPerLevel > 0) {
				data = loaded;
			}
		} catch (IOException exception) {
			EnchantmentWorkbenchMod.LOGGER.warn("Could not read {}, using built-in costs.", path, exception);
			data = defaults();
		}
	}

	public static int getLapisPerLevel() {
		return data.lapisPerLevel;
	}

	public static int getXpCost(Identifier enchantmentId, int level) {
		if (level <= 0) {
			return 0;
		}

		List<Integer> costs = data.xpCosts.get(enchantmentId.toString());
		if (costs == null || costs.isEmpty()) {
			return fallbackCost(level);
		}
		if (level <= costs.size()) {
			return Math.max(0, costs.get(level - 1));
		}
		return costs.get(costs.size() - 1) + (level - costs.size()) * 8;
	}

	private static int fallbackCost(int level) {
		return switch (level) {
			case 1 -> 5;
			case 2 -> 9;
			case 3 -> 16;
			case 4 -> 25;
			case 5 -> 36;
			default -> 36 + (level - 5) * 10;
		};
	}

	private static void writeDefaults(Path path) {
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path)) {
				GSON.toJson(data, writer);
			}
		} catch (IOException exception) {
			EnchantmentWorkbenchMod.LOGGER.warn("Could not write default cost config at {}", path, exception);
		}
	}

	private static CostData defaults() {
		CostData costData = new CostData();
		costData.lapisPerLevel = 5;
		costData.xpCosts = new LinkedHashMap<>();

		put(costData, "protection", 3, 6, 12, 20);
		put(costData, "fire_protection", 3, 7, 13, 22);
		put(costData, "feather_falling", 4, 8, 14, 24);
		put(costData, "blast_protection", 3, 7, 13, 22);
		put(costData, "projectile_protection", 3, 6, 12, 20);
		put(costData, "respiration", 5, 10, 18);
		put(costData, "aqua_affinity", 8);
		put(costData, "thorns", 8, 18, 30);
		put(costData, "depth_strider", 5, 10, 18);
		put(costData, "frost_walker", 8, 16);
		put(costData, "binding_curse", 10);
		put(costData, "soul_speed", 8, 16, 28);
		put(costData, "swift_sneak", 8, 16, 28);
		put(costData, "sharpness", 2, 4, 8, 16, 32);
		put(costData, "smite", 2, 4, 8, 16, 32);
		put(costData, "bane_of_arthropods", 2, 4, 8, 16, 32);
		put(costData, "knockback", 5, 12);
		put(costData, "fire_aspect", 6, 14);
		put(costData, "looting", 4, 8, 15);
		put(costData, "sweeping_edge", 4, 8, 15);
		put(costData, "efficiency", 2, 4, 8, 16, 32);
		put(costData, "silk_touch", 12);
		put(costData, "unbreaking", 3, 6, 12);
		put(costData, "fortune", 6, 14, 26);
		put(costData, "power", 2, 4, 8, 16, 32);
		put(costData, "punch", 6, 14);
		put(costData, "flame", 12);
		put(costData, "infinity", 18);
		put(costData, "luck_of_the_sea", 5, 10, 18);
		put(costData, "lure", 4, 8, 15);
		put(costData, "loyalty", 5, 10, 18);
		put(costData, "impaling", 2, 4, 8, 16, 32);
		put(costData, "riptide", 8, 16, 28);
		put(costData, "channeling", 14);
		put(costData, "multishot", 12);
		put(costData, "quick_charge", 4, 8, 15);
		put(costData, "piercing", 4, 8, 16, 28);
		put(costData, "density", 3, 6, 12, 20, 32);
		put(costData, "breach", 4, 8, 16, 28);
		put(costData, "wind_burst", 10, 22, 36);
		put(costData, "lunge", 8, 16, 28);
		put(costData, "mending", 12);
		put(costData, "vanishing_curse", 10);
		return costData;
	}

	private static void put(CostData costData, String path, Integer... costs) {
		costData.xpCosts.put("minecraft:" + path, List.of(costs));
	}

	private static final class CostData {
		int lapisPerLevel;
		Map<String, List<Integer>> xpCosts;
	}
}
