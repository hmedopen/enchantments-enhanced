package com.hmed.enchantmentworkbench.screen;

import com.hmed.enchantmentworkbench.EnchantmentWorkbenchMod;
import com.hmed.enchantmentworkbench.service.EnchantingRules;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public class EnchantmentWorkbenchMenu extends AbstractContainerMenu {
	public static final int INPUT_SLOT = 0;
	public static final int LAPIS_SLOT = 1;
	public static final int WORKBENCH_SLOT_COUNT = 2;
	public static final int INVENTORY_START = WORKBENCH_SLOT_COUNT;
	public static final int INVENTORY_END = INVENTORY_START + 27;
	public static final int HOTBAR_START = INVENTORY_END;
	public static final int HOTBAR_END = HOTBAR_START + 9;
	public static final int INPUT_X = 24;
	public static final int INPUT_Y = 53;
	public static final int LAPIS_X = 20;
	public static final int LAPIS_Y = 103;
	public static final int PLAYER_INV_X = 206;
	public static final int PLAYER_INV_Y = 184;
	public static final int HOTBAR_Y = 238;

	private final ContainerLevelAccess access;
	private final Container workbench = new SimpleContainer(WORKBENCH_SLOT_COUNT) {
		@Override
		public void setChanged() {
			super.setChanged();
			EnchantmentWorkbenchMenu.this.slotsChanged(this);
		}
	};

	public EnchantmentWorkbenchMenu(int syncId, Inventory inventory) {
		this(syncId, inventory, ContainerLevelAccess.NULL);
	}

	public EnchantmentWorkbenchMenu(int syncId, Inventory inventory, ContainerLevelAccess access) {
		super(EnchantmentWorkbenchMod.MENU_TYPE.get(), syncId);
		this.access = access;

		this.addSlot(new Slot(this.workbench, INPUT_SLOT, INPUT_X, INPUT_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return EnchantingRules.acceptsInput(stack);
			}

			@Override
			public int getMaxStackSize() {
				return 1;
			}
		});

		this.addSlot(new Slot(this.workbench, LAPIS_SLOT, LAPIS_X, LAPIS_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.getItem() == Items.LAPIS_LAZULI;
			}
		});

		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) {
				this.addSlot(new Slot(inventory, column + row * 9 + 9, PLAYER_INV_X + column * 18, PLAYER_INV_Y + row * 18));
			}
		}

		for (int column = 0; column < 9; column++) {
			this.addSlot(new Slot(inventory, column, PLAYER_INV_X + column * 18, HOTBAR_Y));
		}
	}

	public Container getWorkbench() {
		return this.workbench;
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(this.access, player, Blocks.ENCHANTING_TABLE);
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		this.access.execute((level, pos) -> this.clearContainer(player, this.workbench));
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		ItemStack original = ItemStack.EMPTY;
		Slot slot = this.slots.get(slotIndex);
		if (slot == null || !slot.hasItem()) {
			return ItemStack.EMPTY;
		}

		ItemStack moving = slot.getItem();
		original = moving.copy();

		if (slotIndex < WORKBENCH_SLOT_COUNT) {
			if (!this.moveItemStackTo(moving, INVENTORY_START, HOTBAR_END, true)) {
				return ItemStack.EMPTY;
			}
		} else if (moving.getItem() == Items.LAPIS_LAZULI) {
			if (!this.moveItemStackTo(moving, LAPIS_SLOT, LAPIS_SLOT + 1, false)) {
				return ItemStack.EMPTY;
			}
		} else if (EnchantingRules.acceptsInput(moving)) {
			if (!this.moveItemStackTo(moving, INPUT_SLOT, INPUT_SLOT + 1, false)) {
				return ItemStack.EMPTY;
			}
		} else if (slotIndex >= INVENTORY_START && slotIndex < INVENTORY_END) {
			if (!this.moveItemStackTo(moving, HOTBAR_START, HOTBAR_END, false)) {
				return ItemStack.EMPTY;
			}
		} else if (slotIndex >= HOTBAR_START && slotIndex < HOTBAR_END && !this.moveItemStackTo(moving, INVENTORY_START, INVENTORY_END, false)) {
			return ItemStack.EMPTY;
		}

		if (moving.isEmpty()) {
			slot.set(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}

		if (moving.getCount() == original.getCount()) {
			return ItemStack.EMPTY;
		}

		slot.onTake(player, moving);
		return original;
	}
}
