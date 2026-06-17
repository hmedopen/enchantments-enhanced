package com.hmed.enchantmentworkbench.service;

import com.hmed.enchantmentworkbench.config.WorkbenchCostConfig;
import com.hmed.enchantmentworkbench.screen.EnchantmentWorkbenchMenu;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class EnchantingService {
	private EnchantingService() {
	}

	public static void applySelection(ServerPlayer player, EnchantmentWorkbenchMenu menu, Map<Identifier, Integer> requested) {
		ItemStack input = menu.getSlot(EnchantmentWorkbenchMenu.INPUT_SLOT).getItem();
		ItemStack lapis = menu.getSlot(EnchantmentWorkbenchMenu.LAPIS_SLOT).getItem();
		EnchantingTransaction transaction = validate(player, input, lapis, requested);

		if (!transaction.valid()) {
			player.sendSystemMessage(transaction.message());
			return;
		}

		if (!player.isCreative()) {
			player.giveExperienceLevels(-transaction.xpCost());
			lapis.shrink(transaction.lapisCost());
		}

		ItemStack output = input.getItem() == Items.BOOK ? input.transmuteCopy(Items.ENCHANTED_BOOK) : input;
		ItemEnchantments base = EnchantingRules.isBookLike(output)
			? output.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY)
			: output.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
		ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(base);

		transaction.selections().forEach(mutable::set);

		if (EnchantingRules.isBookLike(output)) {
			output.set(DataComponents.STORED_ENCHANTMENTS, mutable.toImmutable());
		} else {
			output.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
		}

		menu.getSlot(EnchantmentWorkbenchMenu.INPUT_SLOT).set(output);
		player.level().playSound(
			null,
			player.blockPosition(),
			SoundEvents.ENCHANTMENT_TABLE_USE,
			SoundSource.BLOCKS,
			1.0F,
			player.getRandom().nextFloat() * 0.1F + 0.95F
		);
		menu.broadcastChanges();
	}

	public static EnchantingTransaction validate(ServerPlayer player, ItemStack input, ItemStack lapis, Map<Identifier, Integer> requested) {
		if (input.isEmpty()) {
			return EnchantingTransaction.invalid(Component.literal("Insert an item first."));
		}
		if (!EnchantingRules.acceptsInput(input)) {
			return EnchantingTransaction.invalid(Component.literal("This item cannot be enchanted here."));
		}
		if (requested.isEmpty()) {
			return EnchantingTransaction.invalid(Component.literal("Select at least one enchantment."));
		}

		HolderLookup.RegistryLookup<Enchantment> registry = player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		Map<Holder.Reference<Enchantment>, Integer> resolved = new LinkedHashMap<>();

		for (Map.Entry<Identifier, Integer> entry : requested.entrySet()) {
			ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, entry.getKey());
			Optional<Holder.Reference<Enchantment>> optional = registry.get(key);
			if (optional.isEmpty()) {
				return EnchantingTransaction.invalid(Component.literal("Unknown enchantment: " + entry.getKey()));
			}

			Holder.Reference<Enchantment> enchantment = optional.get();
			int targetLevel = entry.getValue();
			if (targetLevel < enchantment.value().getMinLevel() || targetLevel > enchantment.value().getMaxLevel()) {
				return EnchantingTransaction.invalid(Component.literal("Invalid level for " + enchantment.value().description().getString() + "."));
			}
			if (!EnchantingRules.canApply(enchantment, input)) {
				return EnchantingTransaction.invalid(Component.literal(enchantment.value().description().getString() + " cannot be applied to this item."));
			}
			if (targetLevel <= EnchantingRules.currentLevel(input, enchantment)) {
				return EnchantingTransaction.invalid(Component.literal(enchantment.value().description().getString() + " is already at that level or higher."));
			}
			if (EnchantingRules.conflictsWithExisting(input, enchantment)) {
				return EnchantingTransaction.invalid(Component.literal(enchantment.value().description().getString() + " conflicts with an existing enchantment."));
			}

			for (Holder.Reference<Enchantment> selected : resolved.keySet()) {
				if (!Enchantment.areCompatible(selected, enchantment)) {
					return EnchantingTransaction.invalid(Component.literal(enchantment.value().description().getString() + " conflicts with another selected enchantment."));
				}
			}

			resolved.put(enchantment, targetLevel);
		}

		int totalXp = 0;
		int totalLevels = 0;
		for (Map.Entry<Holder.Reference<Enchantment>, Integer> entry : resolved.entrySet()) {
			Identifier id = entry.getKey().key().identifier();
			int currentLevel = EnchantingRules.currentLevel(input, entry.getKey());
			int targetLevel = entry.getValue();
			totalXp += Math.max(0, WorkbenchCostConfig.getXpCost(id, targetLevel) - WorkbenchCostConfig.getXpCost(id, currentLevel));
			totalLevels += targetLevel;
		}

		int lapisCost = totalLevels * WorkbenchCostConfig.getLapisPerLevel();
		if (!player.isCreative()) {
			if (player.experienceLevel < totalXp) {
				return EnchantingTransaction.invalid(Component.literal("Not enough XP levels."));
			}
			if (lapis.getItem() != Items.LAPIS_LAZULI || lapis.getCount() < lapisCost) {
				return EnchantingTransaction.invalid(Component.literal("Not enough lapis lazuli."));
			}
		}

		return EnchantingTransaction.valid(totalXp, lapisCost, resolved);
	}
}
