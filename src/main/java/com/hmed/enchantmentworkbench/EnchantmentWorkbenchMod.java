package com.hmed.enchantmentworkbench;

import com.hmed.enchantmentworkbench.client.EnchantmentWorkbenchClient;
import com.hmed.enchantmentworkbench.config.WorkbenchCostConfig;
import com.hmed.enchantmentworkbench.network.EnchantRequestPayload;
import com.hmed.enchantmentworkbench.screen.EnchantmentWorkbenchMenu;
import com.hmed.enchantmentworkbench.service.EnchantingService;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

@Mod(EnchantmentWorkbenchMod.MOD_ID)
public final class EnchantmentWorkbenchMod {
    public static final String MOD_ID = "enchantment_workbench";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final Identifier MENU_ID = id("workbench");

    private static final DeferredRegister<MenuType<?>> MENUS =
        DeferredRegister.create(ForgeRegistries.MENU_TYPES, MOD_ID);

    public static final RegistryObject<MenuType<EnchantmentWorkbenchMenu>> MENU_TYPE = MENUS.register(
        "workbench",
        () -> new MenuType<>(EnchantmentWorkbenchMenu::new, FeatureFlags.DEFAULT_FLAGS)
    );

    public static final SimpleChannel NETWORK = ChannelBuilder.named(id("main"))
        .networkProtocolVersion(1)
        .simpleChannel()
        .messageBuilder(EnchantRequestPayload.class, NetworkDirection.PLAY_TO_SERVER)
        .codec(EnchantRequestPayload.STREAM_CODEC)
        .consumerMainThread(EnchantmentWorkbenchMod::handleEnchantRequest)
        .add()
        .build();

    public EnchantmentWorkbenchMod(FMLJavaModLoadingContext context) {
        MENUS.register(context.getModBusGroup());
        PlayerInteractEvent.RightClickBlock.BUS.addListener(EnchantmentWorkbenchMod::onRightClickBlock);
        WorkbenchCostConfig.load();
        LOGGER.info("Enchantments Enhanced 2.0 loaded on Forge.");
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    public static void sendEnchantRequest(EnchantRequestPayload payload) {
        NETWORK.send(payload, PacketDistributor.SERVER.noArg());
    }

    private static void handleEnchantRequest(EnchantRequestPayload payload, net.minecraftforge.event.network.CustomPayloadEvent.Context context) {
        ServerPlayer player = context.getSender();
        if (player != null && player.containerMenu instanceof EnchantmentWorkbenchMenu menu) {
            EnchantingService.applySelection(player, menu, payload.selections());
        }
    }

    private static boolean onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND
            || !event.getLevel().getBlockState(event.getPos()).is(Blocks.ENCHANTING_TABLE)) {
            return false;
        }

        event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof ServerPlayer player) {
            player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, owner) -> new EnchantmentWorkbenchMenu(
                    containerId,
                    inventory,
                    ContainerLevelAccess.create(event.getLevel(), event.getPos())
                ),
                Component.translatable("container.enchantment_workbench.title")
            ));
        }
        return true;
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, value = Dist.CLIENT)
    public static final class ClientEvents {
        private ClientEvents() {
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(EnchantmentWorkbenchClient::initialize);
        }
    }
}
