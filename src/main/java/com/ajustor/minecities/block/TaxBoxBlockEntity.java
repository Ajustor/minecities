package com.ajustor.minecities.block;

import com.ajustor.minecities.registry.McBlockEntities;
import com.ajustor.minecities.town.Budget;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Ce que contient la boîte à impôts : une rangée, comme un petit coffre. */
public class TaxBoxBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
	public static final int SIZE = 9;
	private static final int[] SLOTS = java.util.stream.IntStream.range(0, SIZE).toArray();

	private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);

	public TaxBoxBlockEntity(BlockPos pos, BlockState state) {
		super(McBlockEntities.TAX_BOX, pos, state);
	}

	@Override
	protected Component getDefaultName() {
		return Component.translatable("container.minecities.tax_box");
	}

	@Override
	protected NonNullList<ItemStack> getItems() {
		return items;
	}

	@Override
	protected void setItems(NonNullList<ItemStack> items) {
		this.items = items;
	}

	@Override
	protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
		return new ChestMenu(MenuType.GENERIC_9x1, containerId, inventory, this, 1);
	}

	@Override
	public int getContainerSize() {
		return SIZE;
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, items);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, items);
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		super.preRemoveSideEffects(pos, state);
		if (level instanceof ServerLevel serverLevel) {
			Budget.taxBoxRemoved(serverLevel, pos);
		}
	}

	// Un entonnoir y verse, mais n'en retire rien : seuls le maire et ses adjoints y puisent.

	@Override
	public int[] getSlotsForFace(Direction side) {
		return SLOTS;
	}

	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
		return true;
	}

	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
		return false;
	}
}
