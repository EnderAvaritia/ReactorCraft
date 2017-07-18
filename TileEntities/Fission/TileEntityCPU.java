/*******************************************************************************
 * @author Reika Kalseki
 * 
 * Copyright 2016
 * 
 * All rights reserved.
 * Distribution of the software in any form is only allowed with
 * explicit, prior permission from the owner.
 ******************************************************************************/
package Reika.ReactorCraft.TileEntities.Fission;

import java.util.Collection;

import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.ForgeDirection;
import Reika.DragonAPI.DragonAPICore;
import Reika.DragonAPI.Instantiable.Data.BlockStruct.BlockArray;
import Reika.DragonAPI.Instantiable.Data.Immutable.Coordinate;
import Reika.DragonAPI.Libraries.World.ReikaWorldHelper;
import Reika.ReactorCraft.Auxiliary.NeutronTile;
import Reika.ReactorCraft.Auxiliary.ReactorBlock;
import Reika.ReactorCraft.Auxiliary.ReactorControlLayout;
import Reika.ReactorCraft.Auxiliary.ReactorPowerReceiver;
import Reika.ReactorCraft.Auxiliary.Temperatured;
import Reika.ReactorCraft.Base.TileEntityReactorBase;
import Reika.ReactorCraft.Entities.EntityNeutron;
import Reika.ReactorCraft.Event.ScramEvent;
import Reika.ReactorCraft.Registry.ReactorAchievements;
import Reika.ReactorCraft.Registry.ReactorBlocks;
import Reika.ReactorCraft.Registry.ReactorSounds;
import Reika.ReactorCraft.Registry.ReactorTiles;
import Reika.ReactorCraft.TileEntities.Fission.TileEntityWaterCell.LiquidStates;
import Reika.RotaryCraft.API.Power.PowerTransferHelper;

public class TileEntityCPU extends TileEntityReactorBase implements ReactorPowerReceiver, Temperatured, ReactorBlock, NeutronTile {

	private ReactorControlLayout layout;
	private final BlockArray reactor = new BlockArray();

	public static final int POWERPERROD = 1024;

	private int omega;
	private int torque;
	private long power;

	private int redstoneUpdate = 200;

	@Override
	public void updateEntity(World world, int x, int y, int z, int meta) {
		thermalTicker.update();
		if (thermalTicker.checkCap())
			this.updateTemperature(world, x, y, z);
		if (!world.isRemote) {

			if (world.getTotalWorldTime()%64 == 0)
				reactor.clear();

			if (reactor.isEmpty()) {
				layout.clear();
				int r = 12;
				Block id = ReactorBlocks.REACTOR.getBlockInstance();
				Block id2 = ReactorBlocks.MODELREACTOR.getBlockInstance();
				for (int i = 2; i < 6; i++)
					reactor.recursiveMultiAddWithBounds(world, x+dirs[i].offsetX, y, z+dirs[i].offsetZ, x-r, y-4, z-r, x+r, y+4, z+r, id, id2);
				for (int i = 0; i < reactor.getSize(); i++) {
					Coordinate c = reactor.getNthBlock(i);
					int dx = c.xCoord;
					int dy = c.yCoord;
					int dz = c.zCoord;
					Block idx = world.getBlock(dx, dy, dz);
					int metax = world.getBlockMetadata(dx, dy, dz);
					if (idx == ReactorTiles.CONTROL.getBlock() && metax == ReactorTiles.CONTROL.getBlockMetadata()) {
						TileEntityControlRod rod = (TileEntityControlRod)world.getTileEntity(dx, dy, dz);
						layout.addControlRod(rod);
					}
				}
				this.syncAllData(true);
			}
		}

		//TileEntity te = this.getAdjacentTileEntity(ForgeDirection.DOWN);
		//if (te instanceof TileEntityCPU) {
		//	power = ((TileEntityCPU)te).power;
		//}
		if (DragonAPICore.debugtest) {
			omega = 1024;
			torque = 1024;
			power = omega*torque;
		}
		else if (!PowerTransferHelper.checkPowerFromAllSides(this, true)) {
			this.noInputMachine();
		}

		if (world.isRemote)
			return;

		if (power < this.getMinPower())
			this.SCRAM();

		if (layout.getNumberRods() > 0) {
			if ((temperature > this.getMaxTemperature() || layout.getRandomRod(rand).getTemperature() > this.getMaxTemperature()) && power >= this.getMinPower()*4) {
				ReactorAchievements.SCRAM.triggerAchievement(this.getPlacer());
				this.SCRAM();
			}
		}

		if (redstoneUpdate > 0) {
			redstoneUpdate--;
			if (redstoneUpdate <= 0) {
				world.markBlockForUpdate(x, y, z);
				ReikaWorldHelper.causeAdjacentUpdates(world, x, y, z);
			}
		}

	}

	@Override
	protected void onFirstTick(World world, int x, int y, int z) {
		layout = new ReactorControlLayout(this);
	}

	public void SCRAM() {
		MinecraftForge.EVENT_BUS.post(new ScramEvent(this, temperature));
		layout.SCRAM();
		if (redstoneUpdate == 0)
			redstoneUpdate = 7;
		//TileEntity te = this.getAdjacentTileEntity(ForgeDirection.UP);
		//if (te instanceof TileEntityCPU)
		//	((TileEntityCPU)te).SCRAM();
	}

	@Override
	protected void animateWithTick(World world, int x, int y, int z) {

	}

	@Override
	public int getIndex() {
		return ReactorTiles.CPU.ordinal();
	}

	public ReactorControlLayout getLayout() {
		return layout;
	}

	@Override
	public int getOmega() {
		return omega;
	}

	@Override
	public int getTorque() {
		return torque;
	}

	@Override
	public long getPower() {
		return power;
	}

	@Override
	public int getIORenderAlpha() {
		return 0;
	}

	@Override
	public void setIORenderAlpha(int io) {}

	@Override
	public void setOmega(int omega) {
		this.omega = omega;
	}

	@Override
	public void setTorque(int torque) {
		this.torque = torque;
	}

	@Override
	public void setPower(long power) {
		this.power = power;
	}

	@Override
	public boolean canReadFrom(ForgeDirection dir) {
		return true;
	}

	@Override
	public boolean isReceiving() {
		return true;
	}

	@Override
	public void noInputMachine() {
		torque = omega = 0;
		power = 0;
	}

	@Override
	public int getTemperature() {
		return temperature;
	}

	@Override
	public void setTemperature(int T) {
		temperature = T;
	}

	@Override
	public int getMaxTemperature() {
		return 800;
	}

	@Override
	public boolean canDumpHeatInto(LiquidStates liq) {
		return liq != LiquidStates.EMPTY;
	}

	public void lowerAllRods() {
		Collection<TileEntityControlRod> li = layout.getAllRods();
		for (TileEntityControlRod te : li) {
			te.setActive(true, false);
		}
		ReactorSounds.CONTROL.playSoundAtBlock(worldObj, xCoord, yCoord, zCoord, 1, 1.3F);
		redstoneUpdate = 30;
	}

	public void raiseAllRods() {
		Collection<TileEntityControlRod> li = layout.getAllRods();
		for (TileEntityControlRod te : li) {
			te.setActive(false, false);
		}
		ReactorSounds.CONTROL.playSoundAtBlock(worldObj, xCoord, yCoord, zCoord, 1, 1.3F);
		redstoneUpdate = 30;
	}

	@Override
	public int getMinTorque(int available) {
		return 1;
	}

	@Override
	public int getMinTorque() {
		return 1;
	}

	@Override
	public int getMinSpeed() {
		return 1;
	}

	@Override
	public long getMinPower() {
		long base = layout != null ? layout.getMinPower() : 0;
		//TileEntity te = this.getAdjacentTileEntity(ForgeDirection.UP);
		//if (te instanceof TileEntityCPU)
		//	base += ((TileEntityCPU)te).getMinPower();
		return base;
	}

	@Override
	public int getRedstoneOverride() {
		return layout.isEmpty() ? 0 : 15*layout.countLoweredRods()/layout.getNumberRods();
	}

	@Override
	protected void readSyncTag(NBTTagCompound NBT)
	{
		super.readSyncTag(NBT);

		omega = NBT.getInteger("speed");
		torque = NBT.getInteger("trq");
		power = NBT.getLong("pwr");

		redstoneUpdate = NBT.getInteger("redsu");

		if (layout != null)
			layout.readFromNBT(NBT);
	}

	@Override
	protected void writeSyncTag(NBTTagCompound NBT)
	{
		super.writeSyncTag(NBT);

		NBT.setInteger("speed", omega);
		NBT.setInteger("trq", torque);
		NBT.setLong("pwr", power);

		NBT.setInteger("redsu", redstoneUpdate);

		if (layout != null)
			layout.writeToNBT(NBT);
	}

	@Override
	public void writeToNBT(NBTTagCompound NBT) {
		super.writeToNBT(NBT);

		NBTTagCompound tag = new NBTTagCompound();
		NBT.setTag("rods", tag);
	}

	@Override
	public void readFromNBT(NBTTagCompound NBT) {
		super.readFromNBT(NBT);

		NBTTagCompound tag = NBT.getCompoundTag("rods");
	}

	@Override
	public boolean onNeutron(EntityNeutron e, World world, int x, int y, int z) {
		return false;
	}

}
