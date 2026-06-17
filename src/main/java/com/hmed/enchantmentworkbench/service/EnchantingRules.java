package com.hmed.enchantmentworkbench.service;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public final class EnchantingRules {
	private EnchantingRules() {
	}

	public static boolean acceptsInput(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		return isBookLike(stack)
			|| stack.isEnchantable()
			|| stack.isEnchanted()
			|| stack.hasNonDefault(DataComponents.ENCHANTMENTS)
			|| stack.hasNonDefault(DataComponents.STORED_ENCHANTMENTS);
	}

	public static boolean isBookLike(ItemStack stack) {
		return stack.getItem() == Items.BOOK || stack.getItem() == Items.ENCHANTED_BOOK;
	}

	public static boolean canApply(Holder<Enchantment> enchantment, ItemStack stack) {
		return isBookLike(stack) || enchantment.value().canEnchant(stack);
	}

	public static ItemEnchantments enchantmentsOn(ItemStack stack) {
		if (isBookLike(stack)) {
			return stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
		}
		return stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
	}

	public static int currentLevel(ItemStack stack, Holder<Enchantment> enchantment) {
		return enchantmentsOn(stack).getLevel(enchantment);
	}

	public static boolean conflictsWithExisting(ItemStack stack, Holder<Enchantment> candidate) {
		for (Holder<Enchantment> existing : enchantmentsOn(stack).keySet()) {
			if (!existing.is(candidate) && !Enchantment.areCompatible(existing, candidate)) {
				return true;
			}
		}
		return false;
	}
}
