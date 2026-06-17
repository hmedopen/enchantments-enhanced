package com.hmed.enchantmentworkbench;

import com.hmed.enchantmentworkbench.config.WorkbenchCostConfig;
import com.hmed.enchantmentworkbench.network.EnchantRequestPayload;
import com.hmed.enchantmentworkbench.screen.EnchantmentWorkbenchMenu;
import com.hmed.enchantmentworkbench.service.EnchantingService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EnchantmentWorkbenchMod implements ModInitializer {
	public static final String MOD_ID = "enchantment_workbench";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final Identifier MENU_ID = id("workbench");

	public static final MenuType<EnchantmentWorkbenchMenu> MENU_TYPE = Registry.register(
		BuiltInRegistries.MENU,
		MENU_ID,
		new MenuType<>(EnchantmentWorkbenchMenu::new, FeatureFlags.DEFAULT_FLAGS)
	);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		WorkbenchCostConfig.load();
		PayloadTypeRegistry.serverboundPlay().register(EnchantRequestPayload.TYPE, EnchantRequestPayload.STREAM_CODEC);
		ServerPlayNetworking.registerGlobalReceiver(EnchantRequestPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				if (context.player().containerMenu instanceof EnchantmentWorkbenchMenu menu) {
					EnchantingService.applySelection(context.player(), menu, payload.selections());
				}
			});
		});
		LOGGER.info("Enchantment Workbench v2 loaded.");
	}
}
