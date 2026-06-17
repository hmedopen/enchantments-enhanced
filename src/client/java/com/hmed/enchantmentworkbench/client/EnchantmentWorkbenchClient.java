package com.hmed.enchantmentworkbench.client;

import com.hmed.enchantmentworkbench.EnchantmentWorkbenchMod;
import com.hmed.enchantmentworkbench.client.screen.EnchantmentWorkbenchScreen;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;

public class EnchantmentWorkbenchClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MenuScreens.register(EnchantmentWorkbenchMod.MENU_TYPE, EnchantmentWorkbenchScreen::new);
	}
}
