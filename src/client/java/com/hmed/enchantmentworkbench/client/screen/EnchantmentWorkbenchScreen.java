package com.hmed.enchantmentworkbench.client.screen;

import com.hmed.enchantmentworkbench.config.WorkbenchCostConfig;
import com.hmed.enchantmentworkbench.network.EnchantRequestPayload;
import com.hmed.enchantmentworkbench.screen.EnchantmentWorkbenchMenu;
import com.hmed.enchantmentworkbench.service.EnchantingRules;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class EnchantmentWorkbenchScreen extends AbstractContainerScreen<EnchantmentWorkbenchMenu> {
	private static final int IMAGE_WIDTH = 390;
	private static final int IMAGE_HEIGHT = 260;
	private static final int CATALOG_X = 146;
	private static final int CATALOG_Y = 91;
	private static final int CATALOG_W = 228;
	private static final int CATALOG_H = 84;
	private static final int ROW_HEIGHT = 28;
	private static final int SCROLLBAR_W = 6;
	private static final int SELECTED_PANEL_X = 8;
	private static final int SELECTED_PANEL_Y = 179;
	private static final int SELECTED_PANEL_W = 176;
	private static final int SELECTED_PANEL_H = 78;
	private static final int SELECTED_LIST_X = 14;
	private static final int SELECTED_LIST_Y = 199;
	private static final int SELECTED_LIST_W = 164;
	private static final int SELECTED_LIST_H = 51;
	private static final int SELECTED_ROW_HEIGHT = 11;

	private final Map<Identifier, Integer> cart = new LinkedHashMap<>();
	private final List<Holder.Reference<Enchantment>> catalog = new ArrayList<>();
	private final List<Holder.Reference<Enchantment>> filteredCatalog = new ArrayList<>();
	private ItemStack cachedInput = ItemStack.EMPTY;
	private Category category = Category.ALL;
	private EditBox searchBox;
	private Button enchantButton;
	private Button closeButton;
	private float scrollAmount;
	private float selectedScrollAmount;
	private boolean scrolling;
	private boolean selectedScrolling;

	public EnchantmentWorkbenchScreen(EnchantmentWorkbenchMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, IMAGE_WIDTH, IMAGE_HEIGHT);
	}

	@Override
	protected void init() {
		super.init();
		this.titleLabelX = 10;
		this.titleLabelY = 8;
		this.inventoryLabelX = 8;
		this.inventoryLabelY = -1000;

		this.searchBox = new EditBox(this.font, this.leftPos + CATALOG_X, this.topPos + 48, 198, 18, Component.translatable("gui.enchantment_workbench.search"));
		this.searchBox.setMaxLength(40);
		this.searchBox.setHint(Component.translatable("gui.enchantment_workbench.search"));
		this.searchBox.setResponder(value -> rebuildFilter());
		this.addRenderableWidget(this.searchBox);

		this.enchantButton = Button.builder(Component.translatable("gui.enchantment_workbench.enchant"), button -> {
			if (canEnchantNow()) {
				ClientPlayNetworking.send(new EnchantRequestPayload(new LinkedHashMap<>(this.cart)));
				this.cart.clear();
				this.selectedScrollAmount = 0.0F;
				rebuildFilter();
			}
		}).bounds(this.leftPos + 14, this.topPos + 154, 120, 20).build();
		this.addRenderableWidget(this.enchantButton);

		this.closeButton = Button.builder(Component.translatable("gui.enchantment_workbench.close"), button -> this.onClose())
			.bounds(this.leftPos + IMAGE_WIDTH - 27, this.topPos + 5, 20, 16)
			.build();
		this.addRenderableWidget(this.closeButton);
		rebuildCatalog();
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		ItemStack current = this.menu.getSlot(EnchantmentWorkbenchMenu.INPUT_SLOT).getItem();
		if (!ItemStack.matches(current, this.cachedInput)) {
			this.cachedInput = current.copy();
			rebuildCatalog();
		}
		if (this.enchantButton != null) {
			this.enchantButton.active = canEnchantNow();
		}
	}

	@Override
	public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		drawWorkbench(graphics, mouseX, mouseY);
		drawSelectedPanel(graphics);
		drawCatalog(graphics, mouseX, mouseY);
		super.extractContents(graphics, mouseX, mouseY, partialTick);
		drawHoverTooltips(graphics, mouseX, mouseY);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (this.searchBox != null && this.searchBox.isFocused()) {
			if (this.minecraft != null && this.minecraft.options.keyInventory.matches(event)) {
				return true;
			}
			if (this.searchBox.keyPressed(event)) {
				return true;
			}
		}
		return super.keyPressed(event);
	}

	private void rebuildCatalog() {
		this.catalog.clear();
		this.cart.clear();
		this.scrollAmount = 0.0F;
		this.selectedScrollAmount = 0.0F;
		this.cachedInput = this.menu.getSlot(EnchantmentWorkbenchMenu.INPUT_SLOT).getItem().copy();
		if (!this.cachedInput.isEmpty() && this.minecraft != null && this.minecraft.level != null) {
			var registry = this.minecraft.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
			registry.listElements()
				.filter(ref -> EnchantingRules.canApply(ref, this.cachedInput))
				.filter(ref -> ref.value().getMaxLevel() > EnchantingRules.currentLevel(this.cachedInput, ref))
				.sorted(Comparator.comparing(ref -> ref.value().description().getString()))
				.forEach(this.catalog::add);
		}
		rebuildFilter();
	}

	private void rebuildFilter() {
		this.filteredCatalog.clear();
		String query = this.searchBox == null ? "" : this.searchBox.getValue().trim().toLowerCase(Locale.ROOT);
		for (Holder.Reference<Enchantment> ref : this.catalog) {
			Identifier id = ref.key().identifier();
			String name = ref.value().description().getString().toLowerCase(Locale.ROOT);
			if (!query.isEmpty() && !name.contains(query) && !id.toString().contains(query)) {
				continue;
			}
			if (this.category != Category.ALL && !this.category.matches(id)) {
				continue;
			}
			this.filteredCatalog.add(ref);
		}
		this.scrollAmount = Mth.clamp(this.scrollAmount, 0.0F, 1.0F);
	}

	private void drawWorkbench(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int x = this.leftPos;
		int y = this.topPos;
		fillPanel(graphics, x, y, IMAGE_WIDTH, IMAGE_HEIGHT, 0xFF6A6A6A, 0xFF1C1C1C, 0xFFA7A7A7);
		fillPanel(graphics, x + 5, y + 5, IMAGE_WIDTH - 10, 18, 0xFF3A3A3A, 0xFF111111, 0xFF8B8B8B);
		graphics.text(this.font, Component.translatable("container.enchantment_workbench.title"), x + 12, y + 10, 0xFFFFFFFF);

		fillPanel(graphics, x + 8, y + 28, 126, 147, 0xFF242424, 0xFF0D0D0D, 0xFF737373);
		fillPanel(graphics, x + 140, y + 28, 242, 147, 0xFF242424, 0xFF0D0D0D, 0xFF737373);
		fillPanel(graphics, x + SELECTED_PANEL_X, y + SELECTED_PANEL_Y, SELECTED_PANEL_W, SELECTED_PANEL_H, 0xFF2E2E2E, 0xFF121212, 0xFF707070);
		fillPanel(graphics, x + 192, y + 179, 190, 78, 0xFF2E2E2E, 0xFF121212, 0xFF707070);

		graphics.text(this.font, "ITEM", x + 17, y + 36, 0xFFFFE45C);
		graphics.text(this.font, "COSTS", x + 83, y + 36, 0xFFFFE45C);
		graphics.text(this.font, "ENCHANTMENT CATALOG", x + 148, y + 36, 0xFFFFE45C);
		graphics.text(this.font, "Insert Item", x + 54, y + 59, 0xFFECECEC);

		drawSlotFrame(graphics, x + EnchantmentWorkbenchMenu.INPUT_X, y + EnchantmentWorkbenchMenu.INPUT_Y, false);
		drawInventoryGrid(graphics, x, y);
		drawTabs(graphics, mouseX, mouseY);
	}

	private void drawSelectedPanel(GuiGraphicsExtractor graphics) {
		int x = this.leftPos;
		int y = this.topPos;
		int requiredLapis = getRequiredLapis();
		int requiredXp = getRequiredXp();
		int availableLapis = getLapisCount();
		int playerXp = this.minecraft != null && this.minecraft.player != null ? this.minecraft.player.experienceLevel : 0;

		boolean hasLapis = availableLapis >= requiredLapis;
		boolean hasXp = this.minecraft != null && this.minecraft.player != null && (this.minecraft.player.isCreative() || playerXp >= requiredXp);
		graphics.text(this.font, "REQUIREMENTS:", x + 15, y + 85, 0xFFFFFFFF);
		fillPanel(graphics, x + 14, y + 96, 112, 31, 0xFF171717, 0xFF050505, 0xFF4C4C4C);
		drawSlotFrame(graphics, x + EnchantmentWorkbenchMenu.LAPIS_X, y + EnchantmentWorkbenchMenu.LAPIS_Y, false);
		graphics.text(this.font, "Lapis", x + 47, y + 101, 0xFF6BA0FF);
		graphics.text(this.font, availableLapis + " / " + requiredLapis, x + 47, y + 113, hasLapis ? 0xFF55FF55 : 0xFFFF6060);
		graphics.text(this.font, hasLapis ? "OK" : "NO", x + 105, y + 113, hasLapis ? 0xFF55FF55 : 0xFFFF6060);
		fillPanel(graphics, x + 14, y + 131, 112, 15, 0xFF171717, 0xFF050505, 0xFF4C4C4C);
		graphics.text(this.font, playerXp + " / " + requiredXp + " XP", x + 20, y + 135, hasXp ? 0xFF55FF55 : 0xFFFF6060);
		graphics.text(this.font, hasXp ? "OK" : "NO", x + 105, y + 135, hasXp ? 0xFF55FF55 : 0xFFFF6060);

		drawSelectedList(graphics, x, y);
	}

	private void drawSelectedList(GuiGraphicsExtractor graphics, int x, int y) {
		graphics.text(this.font, "SELECTED ENCHANTMENTS", x + 16, y + 187, 0xFFFFE45C);
		fillPanel(graphics, x + SELECTED_LIST_X, y + SELECTED_LIST_Y - 3, SELECTED_LIST_W, SELECTED_LIST_H + 6, 0xFF171717, 0xFF050505, 0xFF4C4C4C);

		int contentHeight = this.cart.size() * SELECTED_ROW_HEIGHT;
		int scrollOffset = contentHeight > SELECTED_LIST_H ? (int) (this.selectedScrollAmount * (contentHeight - SELECTED_LIST_H)) : 0;
		int listX = x + SELECTED_LIST_X + 4;
		int listY = y + SELECTED_LIST_Y;
		int listRight = x + SELECTED_LIST_X + SELECTED_LIST_W - 9;
		int listBottom = y + SELECTED_LIST_Y + SELECTED_LIST_H;

		graphics.enableScissor(listX, listY, listRight, listBottom);
		if (this.cart.isEmpty()) {
			graphics.text(this.font, "None selected", listX, listY + 15, 0xFF777777);
		} else {
			int index = 0;
			for (Map.Entry<Identifier, Integer> entry : this.cart.entrySet()) {
				int rowY = listY + index * SELECTED_ROW_HEIGHT - scrollOffset;
				if (rowY + SELECTED_ROW_HEIGHT >= listY && rowY <= listBottom) {
					String text = shortName(entry.getKey()) + " " + roman(entry.getValue());
					graphics.text(this.font, this.font.plainSubstrByWidth(text, SELECTED_LIST_W - 18), listX, rowY, 0xFFCE82FF);
				}
				index++;
			}
		}
		graphics.disableScissor();

		if (contentHeight > SELECTED_LIST_H) {
			int barX = x + SELECTED_LIST_X + SELECTED_LIST_W - 7;
			int barY = y + SELECTED_LIST_Y;
			graphics.fill(barX, barY, barX + 4, barY + SELECTED_LIST_H, 0xFF111111);
			int thumbHeight = Math.max(12, (int) ((float) SELECTED_LIST_H / contentHeight * SELECTED_LIST_H));
			int thumbY = barY + (int) (this.selectedScrollAmount * (SELECTED_LIST_H - thumbHeight));
			graphics.fill(barX + 1, thumbY, barX + 3, thumbY + thumbHeight, 0xFFA16BFF);
		}
	}

	private void drawCatalog(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int x = this.leftPos + CATALOG_X;
		int y = this.topPos + CATALOG_Y;
		fillPanel(graphics, x - 4, y - 4, CATALOG_W + 8, CATALOG_H + 8, 0xFF1B1B1B, 0xFF060606, 0xFF5D5D5D);

		if (this.cachedInput.isEmpty()) {
			graphics.centeredText(this.font, "Insert an item to browse enchantments", x + CATALOG_W / 2, y + 25, 0xFF8F8F8F);
			return;
		}

		int totalHeight = this.filteredCatalog.size() * ROW_HEIGHT;
		int scrollOffset = totalHeight > CATALOG_H ? (int) (this.scrollAmount * (totalHeight - CATALOG_H)) : 0;
		graphics.enableScissor(x, y, x + CATALOG_W - SCROLLBAR_W - 2, y + CATALOG_H);
		for (int i = 0; i < this.filteredCatalog.size(); i++) {
			int rowY = y + i * ROW_HEIGHT - scrollOffset;
			if (rowY + ROW_HEIGHT >= y && rowY <= y + CATALOG_H) {
				drawCatalogRow(graphics, this.filteredCatalog.get(i), x, rowY, mouseX, mouseY);
			}
		}
		graphics.disableScissor();

		int barX = x + CATALOG_W - SCROLLBAR_W;
		graphics.fill(barX, y, barX + SCROLLBAR_W, y + CATALOG_H, 0xFF111111);
		if (totalHeight > CATALOG_H) {
			int thumbHeight = Math.max(16, (int) ((float) CATALOG_H / totalHeight * CATALOG_H));
			int thumbY = y + (int) (this.scrollAmount * (CATALOG_H - thumbHeight));
			graphics.fill(barX + 1, thumbY, barX + SCROLLBAR_W - 1, thumbY + thumbHeight, 0xFFA16BFF);
		}
	}

	private void drawCatalogRow(GuiGraphicsExtractor graphics, Holder.Reference<Enchantment> ref, int x, int y, int mouseX, int mouseY) {
		Identifier id = ref.key().identifier();
		boolean incompatible = isConflicting(ref);
		graphics.fill(x, y, x + CATALOG_W - SCROLLBAR_W - 4, y + ROW_HEIGHT - 2, incompatible ? 0xFF2B1818 : 0xFF2E2E2E);
		graphics.outline(x, y, CATALOG_W - SCROLLBAR_W - 4, ROW_HEIGHT - 2, incompatible ? 0xFF9B3E3E : 0xFF555555);

		String name = ref.value().description().getString();
		name = this.font.plainSubstrByWidth(name, 92);
		graphics.text(this.font, name, x + 6, y + 5, incompatible ? 0xFFFF8888 : 0xFFFFFFFF);
		graphics.text(this.font, "(i)", x + CATALOG_W - 29, y + 5, 0xFFD0D0D0);

		int current = EnchantingRules.currentLevel(this.cachedInput, ref);
		for (int level = 1; level <= ref.value().getMaxLevel(); level++) {
			int buttonX = x + 8 + (level - 1) * 35;
			int buttonY = y + 16;
			boolean selected = this.cart.getOrDefault(id, 0) == level;
			boolean locked = level <= current || incompatible;
			boolean hovered = mouseX >= buttonX && mouseX < buttonX + 30 && mouseY >= buttonY && mouseY < buttonY + 12;
			int fill = locked ? 0xFF242424 : selected ? 0xFF54258A : hovered ? 0xFF5D5D5D : 0xFF444444;
			int border = selected ? 0xFFD275FF : locked ? 0xFF343434 : 0xFF8A8A8A;
			graphics.fill(buttonX, buttonY, buttonX + 30, buttonY + 12, fill);
			graphics.outline(buttonX, buttonY, 30, 12, border);
			graphics.centeredText(this.font, roman(level), buttonX + 15, buttonY + 2, locked ? 0xFF777777 : selected ? 0xFFFFD15A : 0xFFECECEC);
		}
	}

	private void drawTabs(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int x = this.leftPos + CATALOG_X;
		int y = this.topPos + 70;
		int tabX = x;
		for (Category value : Category.values()) {
			int width = value.width;
			boolean selected = this.category == value;
			boolean hovered = mouseX >= tabX && mouseX < tabX + width && mouseY >= y && mouseY < y + 18;
			graphics.fill(tabX, y, tabX + width, y + 18, selected ? 0xFF6B4A14 : hovered ? 0xFF555555 : 0xFF3A3A3A);
			graphics.outline(tabX, y, width, 18, selected ? 0xFFFFD45C : 0xFF777777);
			graphics.centeredText(this.font, value.label, tabX + width / 2, y + 5, selected ? 0xFFFFE45C : 0xFFE0E0E0);
			tabX += width + 3;
		}
	}

	private void drawHoverTooltips(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		HoveredLevel hovered = getHoveredLevel(mouseX, mouseY);
		if (hovered != null) {
			int current = EnchantingRules.currentLevel(this.cachedInput, hovered.enchantment);
			int targetCost = WorkbenchCostConfig.getXpCost(hovered.enchantment.key().identifier(), hovered.level);
			int currentCost = WorkbenchCostConfig.getXpCost(hovered.enchantment.key().identifier(), current);
			List<Component> lines = new ArrayList<>();
			lines.add(Enchantment.getFullname(hovered.enchantment, hovered.level));
			lines.add(Component.literal("XP: " + Math.max(0, targetCost - currentCost)));
			lines.add(Component.literal("Lapis: " + hovered.level * WorkbenchCostConfig.getLapisPerLevel()));
			if (isConflicting(hovered.enchantment)) {
				lines.add(Component.literal("Incompatible with current selection."));
			}
			graphics.setComponentTooltipForNextFrame(this.font, lines, mouseX, mouseY);
			return;
		}

		Holder.Reference<Enchantment> hoveredInfo = getHoveredInfo(mouseX, mouseY);
		if (hoveredInfo != null) {
			graphics.setComponentTooltipForNextFrame(this.font, buildInfoTooltip(hoveredInfo), mouseX, mouseY);
		}
	}

	private List<Component> buildInfoTooltip(Holder.Reference<Enchantment> enchantment) {
		Identifier id = enchantment.key().identifier();
		List<Component> lines = new ArrayList<>();
		lines.add(enchantment.value().description());
		lines.add(Component.literal(describeEnchantment(id)));
		lines.add(Component.literal("Max level: " + enchantment.value().getMaxLevel()));
		lines.add(Component.literal("Category: " + Category.bestFor(id).label));
		lines.add(Component.literal("Cost: " + costRange(id, enchantment.value().getMaxLevel()) + " XP"));
		lines.add(Component.literal(EnchantingRules.canApply(enchantment, this.cachedInput) ? "Works with inserted item." : "Insert a compatible item or book."));

		String incompatibility = incompatibilityNote(id);
		if (!incompatibility.isEmpty()) {
			lines.add(Component.literal("Incompatible: " + incompatibility));
		}
		if (isConflicting(enchantment)) {
			lines.add(Component.literal("Currently blocked by your selection or item."));
		}
		return lines;
	}

	private boolean canEnchantNow() {
		return !this.cachedInput.isEmpty()
			&& !this.cart.isEmpty()
			&& getLapisCount() >= getRequiredLapis()
			&& this.minecraft != null
			&& this.minecraft.player != null
			&& (this.minecraft.player.isCreative() || this.minecraft.player.experienceLevel >= getRequiredXp());
	}

	private int getRequiredLapis() {
		int levels = 0;
		for (int level : this.cart.values()) {
			levels += level;
		}
		return levels * WorkbenchCostConfig.getLapisPerLevel();
	}

	private int getRequiredXp() {
		int total = 0;
		for (Map.Entry<Identifier, Integer> entry : this.cart.entrySet()) {
			Holder.Reference<Enchantment> ref = getEnchantment(entry.getKey()).orElse(null);
			if (ref == null) {
				continue;
			}
			int current = EnchantingRules.currentLevel(this.cachedInput, ref);
			total += Math.max(0, WorkbenchCostConfig.getXpCost(entry.getKey(), entry.getValue()) - WorkbenchCostConfig.getXpCost(entry.getKey(), current));
		}
		return total;
	}

	private int getLapisCount() {
		ItemStack lapis = this.menu.getSlot(EnchantmentWorkbenchMenu.LAPIS_SLOT).getItem();
		return lapis.getItem() == Items.LAPIS_LAZULI ? lapis.getCount() : 0;
	}

	private Optional<Holder.Reference<Enchantment>> getEnchantment(Identifier id) {
		if (this.minecraft == null || this.minecraft.level == null) {
			return Optional.empty();
		}
		return this.minecraft.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(ResourceKey.create(Registries.ENCHANTMENT, id));
	}

	private boolean isConflicting(Holder.Reference<Enchantment> ref) {
		if (EnchantingRules.conflictsWithExisting(this.cachedInput, ref)) {
			return true;
		}
		for (Identifier id : this.cart.keySet()) {
			if (id.equals(ref.key().identifier())) {
				continue;
			}
			Optional<Holder.Reference<Enchantment>> selected = getEnchantment(id);
			if (selected.isPresent() && !Enchantment.areCompatible(selected.get(), ref)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0) {
			double mouseX = event.x();
			double mouseY = event.y();
			if (this.searchBox != null && !this.searchBox.isMouseOver(mouseX, mouseY)) {
				this.searchBox.setFocused(false);
				this.clearFocus();
			}

			if (handleTabClick(mouseX, mouseY)) {
				return true;
			}
			if (handleScrollClick(mouseX, mouseY)) {
				return true;
			}
			if (handleSelectedScrollClick(mouseX, mouseY)) {
				return true;
			}
			if (handleLevelClick(mouseX, mouseY)) {
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	private boolean handleTabClick(double mouseX, double mouseY) {
		int x = this.leftPos + CATALOG_X;
		int y = this.topPos + 70;
		int tabX = x;
		for (Category value : Category.values()) {
			if (mouseX >= tabX && mouseX < tabX + value.width && mouseY >= y && mouseY < y + 18) {
				this.category = value;
				this.scrollAmount = 0.0F;
				rebuildFilter();
				playClick();
				return true;
			}
			tabX += value.width + 3;
		}
		return false;
	}

	private boolean handleScrollClick(double mouseX, double mouseY) {
		int x = this.leftPos + CATALOG_X + CATALOG_W - SCROLLBAR_W;
		int y = this.topPos + CATALOG_Y;
		if (mouseX >= x && mouseX < x + SCROLLBAR_W && mouseY >= y && mouseY < y + CATALOG_H) {
			this.scrolling = true;
			updateScroll(mouseY);
			return true;
		}
		return false;
	}

	private boolean handleSelectedScrollClick(double mouseX, double mouseY) {
		int contentHeight = this.cart.size() * SELECTED_ROW_HEIGHT;
		if (contentHeight <= SELECTED_LIST_H) {
			return false;
		}
		int x = this.leftPos + SELECTED_LIST_X + SELECTED_LIST_W - 8;
		int y = this.topPos + SELECTED_LIST_Y;
		if (mouseX >= x && mouseX < x + 6 && mouseY >= y && mouseY < y + SELECTED_LIST_H) {
			this.selectedScrolling = true;
			updateSelectedScroll(mouseY);
			return true;
		}
		return false;
	}

	private boolean handleLevelClick(double mouseX, double mouseY) {
		HoveredLevel hovered = getHoveredLevel(mouseX, mouseY);
		if (hovered == null) {
			return false;
		}
		Identifier id = hovered.enchantment.key().identifier();
		int current = EnchantingRules.currentLevel(this.cachedInput, hovered.enchantment);
		if (hovered.level <= current || isConflicting(hovered.enchantment)) {
			return true;
		}

		if (this.cart.getOrDefault(id, 0) == hovered.level) {
			this.cart.remove(id);
		} else {
			this.cart.put(id, hovered.level);
		}
		clampSelectedScroll();
		playClick();
		return true;
	}

	private HoveredLevel getHoveredLevel(double mouseX, double mouseY) {
		int x = this.leftPos + CATALOG_X;
		int y = this.topPos + CATALOG_Y;
		if (mouseX < x || mouseX >= x + CATALOG_W - SCROLLBAR_W - 4 || mouseY < y || mouseY >= y + CATALOG_H) {
			return null;
		}
		int totalHeight = this.filteredCatalog.size() * ROW_HEIGHT;
		int scrollOffset = totalHeight > CATALOG_H ? (int) (this.scrollAmount * (totalHeight - CATALOG_H)) : 0;
		int row = (int) ((mouseY - y + scrollOffset) / ROW_HEIGHT);
		if (row < 0 || row >= this.filteredCatalog.size()) {
			return null;
		}

		Holder.Reference<Enchantment> ref = this.filteredCatalog.get(row);
		int rowY = y + row * ROW_HEIGHT - scrollOffset;
		for (int level = 1; level <= ref.value().getMaxLevel(); level++) {
			int buttonX = x + 8 + (level - 1) * 35;
			int buttonY = rowY + 16;
			if (mouseX >= buttonX && mouseX < buttonX + 30 && mouseY >= buttonY && mouseY < buttonY + 12) {
				return new HoveredLevel(ref, level);
			}
		}
		return null;
	}

	private Holder.Reference<Enchantment> getHoveredInfo(double mouseX, double mouseY) {
		int x = this.leftPos + CATALOG_X;
		int y = this.topPos + CATALOG_Y;
		if (mouseX < x || mouseX >= x + CATALOG_W || mouseY < y || mouseY >= y + CATALOG_H) {
			return null;
		}
		int totalHeight = this.filteredCatalog.size() * ROW_HEIGHT;
		int scrollOffset = totalHeight > CATALOG_H ? (int) (this.scrollAmount * (totalHeight - CATALOG_H)) : 0;
		int row = (int) ((mouseY - y + scrollOffset) / ROW_HEIGHT);
		if (row < 0 || row >= this.filteredCatalog.size()) {
			return null;
		}
		int rowY = y + row * ROW_HEIGHT - scrollOffset;
		if (mouseX >= x + CATALOG_W - 31 && mouseX < x + CATALOG_W - 12 && mouseY >= rowY + 3 && mouseY < rowY + 15) {
			return this.filteredCatalog.get(row);
		}
		return null;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (event.button() == 0) {
			this.scrolling = false;
			this.selectedScrolling = false;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (this.scrolling) {
			updateScroll(event.y());
			return true;
		}
		if (this.selectedScrolling) {
			updateSelectedScroll(event.y());
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (isMouseOverSelectedList(mouseX, mouseY)) {
			int contentHeight = this.cart.size() * SELECTED_ROW_HEIGHT;
			if (contentHeight > SELECTED_LIST_H) {
				this.selectedScrollAmount = Mth.clamp(this.selectedScrollAmount - (float) verticalAmount / this.cart.size(), 0.0F, 1.0F);
				return true;
			}
		}
		if (mouseX >= this.leftPos + CATALOG_X && mouseX < this.leftPos + CATALOG_X + CATALOG_W && mouseY >= this.topPos + CATALOG_Y && mouseY < this.topPos + CATALOG_Y + CATALOG_H) {
			int totalHeight = this.filteredCatalog.size() * ROW_HEIGHT;
			if (totalHeight > CATALOG_H) {
				this.scrollAmount = Mth.clamp(this.scrollAmount - (float) verticalAmount / this.filteredCatalog.size(), 0.0F, 1.0F);
				return true;
			}
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	private void updateScroll(double mouseY) {
		int y = this.topPos + CATALOG_Y;
		this.scrollAmount = Mth.clamp((float) (mouseY - y) / CATALOG_H, 0.0F, 1.0F);
	}

	private void updateSelectedScroll(double mouseY) {
		int y = this.topPos + SELECTED_LIST_Y;
		this.selectedScrollAmount = Mth.clamp((float) (mouseY - y) / SELECTED_LIST_H, 0.0F, 1.0F);
	}

	private void clampSelectedScroll() {
		if (this.cart.size() * SELECTED_ROW_HEIGHT <= SELECTED_LIST_H) {
			this.selectedScrollAmount = 0.0F;
		} else {
			this.selectedScrollAmount = Mth.clamp(this.selectedScrollAmount, 0.0F, 1.0F);
		}
	}

	private boolean isMouseOverSelectedList(double mouseX, double mouseY) {
		return mouseX >= this.leftPos + SELECTED_PANEL_X
			&& mouseX < this.leftPos + SELECTED_PANEL_X + SELECTED_PANEL_W
			&& mouseY >= this.topPos + SELECTED_PANEL_Y
			&& mouseY < this.topPos + SELECTED_PANEL_Y + SELECTED_PANEL_H;
	}

	private void playClick() {
		this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
	}

	private void fillPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int fill, int dark, int light) {
		graphics.fill(x, y, x + width, y + height, fill);
		graphics.outline(x, y, width, height, dark);
		graphics.horizontalLine(x + 1, x + width - 2, y + 1, light);
		graphics.verticalLine(x + 1, y + 1, y + height - 2, light);
	}

	private void drawSlotFrame(GuiGraphicsExtractor graphics, int x, int y, boolean active) {
		graphics.fill(x - 2, y - 2, x + 20, y + 20, active ? 0xFF8F5AFF : 0xFF565656);
		graphics.fill(x - 1, y - 1, x + 19, y + 19, 0xFF111111);
		graphics.fill(x, y, x + 18, y + 18, 0xFF8B8B8B);
		graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF232323);
	}

	private void drawInventoryGrid(GuiGraphicsExtractor graphics, int x, int y) {
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) {
				drawInventorySlot(graphics, x + EnchantmentWorkbenchMenu.PLAYER_INV_X + column * 18, y + EnchantmentWorkbenchMenu.PLAYER_INV_Y + row * 18);
			}
		}

		for (int column = 0; column < 9; column++) {
			drawInventorySlot(graphics, x + EnchantmentWorkbenchMenu.PLAYER_INV_X + column * 18, y + EnchantmentWorkbenchMenu.HOTBAR_Y);
		}
	}

	private void drawInventorySlot(GuiGraphicsExtractor graphics, int x, int y) {
		graphics.fill(x - 1, y - 1, x + 18, y + 18, 0xFF111111);
		graphics.fill(x, y, x + 18, y + 18, 0xFF858585);
		graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF303030);
		graphics.horizontalLine(x, x + 17, y, 0xFF1B1B1B);
		graphics.verticalLine(x, y, y + 17, 0xFF1B1B1B);
		graphics.horizontalLine(x, x + 17, y + 17, 0xFFA8A8A8);
		graphics.verticalLine(x + 17, y, y + 17, 0xFFA8A8A8);
	}

	private String roman(int level) {
		return switch (level) {
			case 1 -> "I";
			case 2 -> "II";
			case 3 -> "III";
			case 4 -> "IV";
			case 5 -> "V";
			default -> Integer.toString(level);
		};
	}

	private String shortName(Identifier id) {
		String path = id.getPath().replace('_', ' ');
		String[] parts = path.split(" ");
		StringBuilder builder = new StringBuilder();
		for (String part : parts) {
			if (part.isEmpty()) {
				continue;
			}
			if (!builder.isEmpty()) {
				builder.append(' ');
			}
			builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
		}
		return this.font.plainSubstrByWidth(builder.toString(), 128);
	}

	private String costRange(Identifier id, int maxLevel) {
		if (maxLevel <= 1) {
			return Integer.toString(WorkbenchCostConfig.getXpCost(id, 1));
		}
		return WorkbenchCostConfig.getXpCost(id, 1) + "-" + WorkbenchCostConfig.getXpCost(id, maxLevel);
	}

	private String incompatibilityNote(Identifier id) {
		return switch (id.getPath()) {
			case "sharpness" -> "Smite, Bane of Arthropods";
			case "smite" -> "Sharpness, Bane of Arthropods";
			case "bane_of_arthropods" -> "Sharpness, Smite";
			case "silk_touch" -> "Fortune";
			case "fortune" -> "Silk Touch";
			case "mending" -> "Infinity";
			case "infinity" -> "Mending";
			case "loyalty" -> "Riptide";
			case "riptide" -> "Loyalty, Channeling";
			case "channeling" -> "Riptide";
			case "multishot" -> "Piercing";
			case "piercing" -> "Multishot";
			case "protection" -> "Fire, Blast, Projectile Protection";
			case "fire_protection", "blast_protection", "projectile_protection" -> "Protection and other protection types";
			case "breach" -> "Density";
			case "density" -> "Breach";
			default -> "";
		};
	}

	private String describeEnchantment(Identifier id) {
		return switch (id.getPath()) {
			case "protection" -> "Reduces most incoming damage on armor.";
			case "fire_protection" -> "Reduces fire damage and burn time.";
			case "feather_falling" -> "Reduces fall damage on boots.";
			case "blast_protection" -> "Reduces explosion damage and knockback.";
			case "projectile_protection" -> "Reduces damage from arrows and other projectiles.";
			case "respiration" -> "Extends underwater breathing time.";
			case "aqua_affinity" -> "Lets you mine faster while underwater.";
			case "thorns" -> "Damages attackers when they hit you.";
			case "depth_strider" -> "Increases underwater movement speed.";
			case "frost_walker" -> "Freezes water under your feet into ice.";
			case "binding_curse" -> "Prevents armor from being removed normally.";
			case "soul_speed" -> "Increases speed on soul sand and soul soil.";
			case "swift_sneak" -> "Increases movement speed while sneaking.";
			case "sharpness" -> "Increases melee damage against most targets.";
			case "smite" -> "Increases melee damage against undead mobs.";
			case "bane_of_arthropods" -> "Increases damage to arthropods and slows them.";
			case "knockback" -> "Pushes enemies farther away on hit.";
			case "fire_aspect" -> "Sets targets on fire when hit.";
			case "looting" -> "Increases mob drops from kills.";
			case "sweeping_edge" -> "Improves sword sweep attack damage.";
			case "efficiency" -> "Increases mining and tool use speed.";
			case "silk_touch" -> "Drops many blocks exactly as mined.";
			case "unbreaking" -> "Gives items a chance not to lose durability.";
			case "fortune" -> "Increases drops from many mined blocks.";
			case "power" -> "Increases bow damage.";
			case "punch" -> "Adds knockback to bow shots.";
			case "flame" -> "Sets arrows and targets on fire.";
			case "infinity" -> "Lets a bow fire without consuming normal arrows.";
			case "luck_of_the_sea" -> "Improves treasure odds while fishing.";
			case "lure" -> "Makes fish bite faster.";
			case "loyalty" -> "Makes thrown tridents return to you.";
			case "impaling" -> "Increases trident damage against aquatic targets.";
			case "riptide" -> "Launches you with a trident in water or rain.";
			case "channeling" -> "Summons lightning with tridents during storms.";
			case "multishot" -> "Fires multiple crossbow projectiles at once.";
			case "quick_charge" -> "Reloads crossbows faster.";
			case "piercing" -> "Crossbow shots pass through multiple targets.";
			case "density" -> "Increases mace smash damage from falling.";
			case "breach" -> "Reduces target armor effectiveness for mace hits.";
			case "wind_burst" -> "Launches you upward after a mace smash hit.";
			case "lunge" -> "Adds a forward lunge to spear attacks.";
			case "mending" -> "Repairs the item using experience orbs.";
			case "vanishing_curse" -> "Makes the item disappear when you die.";
			default -> "Custom enchantment. The server will validate compatibility and cost.";
		};
	}

	private record HoveredLevel(Holder.Reference<Enchantment> enchantment, int level) {
	}

	private enum Category {
		ALL("ALL", 28),
		COMBAT("COMBAT", 45),
		ARMOR("ARMOR", 39),
		TOOLS("TOOLS", 38),
		UTILITY("UTIL", 33),
		CURSES("CURSE", 37);

		private final String label;
		private final int width;

		Category(String label, int width) {
			this.label = label;
			this.width = width;
		}

		private boolean matches(Identifier id) {
			return this == bestFor(id);
		}

		private static Category bestFor(Identifier id) {
			String path = id.getPath();
			if (path.contains("curse")) {
				return CURSES;
			}
			return switch (path) {
				case "sharpness", "smite", "bane_of_arthropods", "knockback", "fire_aspect", "looting", "sweeping_edge", "power", "punch", "flame", "infinity", "loyalty", "impaling", "riptide", "channeling", "multishot", "quick_charge", "piercing", "density", "breach", "wind_burst", "lunge" -> COMBAT;
				case "protection", "fire_protection", "feather_falling", "blast_protection", "projectile_protection", "respiration", "aqua_affinity", "thorns", "depth_strider", "frost_walker", "soul_speed", "swift_sneak" -> ARMOR;
				case "efficiency", "silk_touch", "unbreaking", "fortune", "luck_of_the_sea", "lure" -> TOOLS;
				default -> UTILITY;
			};
		}
	}
}
