package com.hmed.enchantmentworkbench.client;

import com.hmed.enchantmentworkbench.EnchantmentWorkbenchMod;
import com.hmed.enchantmentworkbench.client.screen.EnchantmentWorkbenchScreen;
import net.minecraft.client.gui.screens.MenuScreens;

public final class EnchantmentWorkbenchClient {
    private EnchantmentWorkbenchClient() {
    }

    public static void initialize() {
        MenuScreens.register(EnchantmentWorkbenchMod.MENU_TYPE.get(), EnchantmentWorkbenchScreen::new);
    }
}
