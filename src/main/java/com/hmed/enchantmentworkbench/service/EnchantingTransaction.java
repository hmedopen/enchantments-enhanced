package com.hmed.enchantmentworkbench.service;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.LinkedHashMap;
import java.util.Map;

public record EnchantingTransaction(
	boolean valid,
	Component message,
	int xpCost,
	int lapisCost,
	Map<Holder.Reference<Enchantment>, Integer> selections
) {
	public static EnchantingTransaction invalid(Component message) {
		return new EnchantingTransaction(false, message, 0, 0, Map.of());
	}

	public static EnchantingTransaction valid(int xpCost, int lapisCost, Map<Holder.Reference<Enchantment>, Integer> selections) {
		return new EnchantingTransaction(true, Component.empty(), xpCost, lapisCost, new LinkedHashMap<>(selections));
	}
}
