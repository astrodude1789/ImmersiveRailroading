package cam72cam.immersiverailroading.blocks;

import javax.annotation.Nonnull;

import cam72cam.immersiverailroading.items.ItemTabs;
import cam72cam.immersiverailroading.library.SwitchState;
import cam72cam.immersiverailroading.tile.TileRail;
import cam72cam.immersiverailroading.tile.TileRailBase;
import cam72cam.immersiverailroading.tile.TileRailGag;
import cam72cam.immersiverailroading.util.SwitchUtil;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;
import net.minecraftforge.common.property.PropertyFloat;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public abstract class BlockRailBase extends Block {
	
	public static final PropertyItemStack RAIL_BED = new PropertyItemStack("RAIL_BED");
	public static final PropertyFloat HEIGHT = new PropertyFloat("HEIGHT");
	public static final PropertyFloat SNOW = new PropertyFloat("SNOW");
	public static final PropertyFloat GAUGE = new PropertyFloat("GAUGE");
	
	public BlockRailBase(Material materialIn) {
		super(materialIn);
		setHardness(1.0F);
		setSoundType(SoundType.METAL);
		
		setCreativeTab(ItemTabs.MAIN_TAB);
	}
	
	@Override
	public void breakBlock(World world, BlockPos pos, IBlockState state) {
		TileRailBase te = TileRailBase.get(world, pos);
		if (te != null) {
			if (te instanceof TileRail) {
				((TileRail) te).spawnDrops();
			}
			
			breakParentIfExists(te);
		}
		super.breakBlock(world, pos, state);
	}
	
	public static void breakParentIfExists(TileRailBase te) {
		BlockPos parent = te.getParent();
		if (parent != null && !te.getWillBeReplaced()) {
			if (te.getWorld().getBlockState(parent).getBlock() instanceof BlockRail) {
				if (te.getParentTile() != null) {
					te.getParentTile().spawnDrops();
				}
				te.getWorld().setBlockToAir(parent);
			}
		}
	}
	
	@Override
    @Nonnull
    protected BlockStateContainer createBlockState()
    {
        return new ExtendedBlockState(this, new IProperty[0], new IUnlistedProperty<?>[] {
        	RAIL_BED,
        	HEIGHT,
        	SNOW,
        	GAUGE
        });
    }

	@Override
    public IBlockState getExtendedState(IBlockState origState, IBlockAccess world, BlockPos pos)
    {
    	IExtendedBlockState state = (IExtendedBlockState)origState;
    	TileRailBase te = TileRailBase.get(world, pos);
    	if (te != null) {
			if (te.getRenderRailBed() != null) {
				state = state.withProperty(RAIL_BED, te.getRenderRailBed());
				state = state.withProperty(HEIGHT, te.getHeight());
				state = state.withProperty(SNOW, (float)te.getSnowLayers());
				state = state.withProperty(GAUGE, (float)te.getTrackGauge());
			}
    	}
        return state;
    }
	
	@Override
	public void neighborChanged(IBlockState state, World worldIn, BlockPos pos, Block blockIn, BlockPos fromPos) {
		this.onNeighborChange(worldIn, pos, fromPos);
	}
	
	public static boolean tryBreakRail(IBlockAccess world, BlockPos pos) {
		TileRailBase rail = TileRailBase.get(world, pos);
		if (rail != null) {
			if (rail.getReplaced() != null) {
				// new object here is important
				TileRailGag newGag = new TileRailGag();
				newGag.readFromNBT(rail.getReplaced());
				
				// Only do replacement if parent still exists
				if (TileRailBase.get(world, newGag.getParent()) != null) {
					rail.getWorld().setTileEntity(pos, newGag);
					newGag.markDirty();
					breakParentIfExists(rail);
					return false;
				}
			}
		}
		return true;
	}

	@Override
	public void onNeighborChange(IBlockAccess world, BlockPos pos, BlockPos neighbor){
		TileRailBase tileEntity = TileRailBase.get(world, pos);
		if (tileEntity == null) {
			return;
		}
		if (tileEntity.getWorld().isRemote) {
			return;
		}
		boolean isOriginAir = tileEntity.getParentTile() == null || tileEntity.getParentTile().getParentTile() == null;
		boolean isOnRealBlock = world.isSideSolid(pos.down(), EnumFacing.UP, false);
		if (isOriginAir || !isOnRealBlock) {
			if (tryBreakRail(world, pos)) { 
				tileEntity.getWorld().destroyBlock(pos, true);
			}
			return;
		}
		
		IBlockState up = world.getBlockState(pos.up());
		if (up.getBlock() == Blocks.SNOW_LAYER) {
			if (tileEntity.handleSnowTick()) {
				tileEntity.getWorld().setBlockToAir(pos.up());
			}
		}
		if (tileEntity.getParentTile() != null && tileEntity.getParentTile().getParentTile() != null) {
			SwitchState state = SwitchUtil.getSwitchState(tileEntity.getParentTile());
			if (state != SwitchState.NONE) {
				tileEntity.getParentTile().setSwitchState(state);
			}
		}
	}

	@Override
	public boolean isOpaqueCube(IBlockState state) {
		return false;
	}
	
	@Override
	public boolean isFullCube(IBlockState state) {
		return false;
	}
	
	@Override
	public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
		TileRailBase te = TileRailBase.get(source, pos);
		if (te == null) {
	        return NULL_AABB;
		}
		return new AxisAlignedBB(0.0F, 0.0F, 0.0F, 1.0F, te.getFullHeight()+0.1, 1.0F);
	}

	public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
		TileRailBase te = TileRailBase.get(source, pos);
		if (te == null) {
	        return NULL_AABB;
		}
		return new AxisAlignedBB(0.0F, 0.0F, 0.0F, 1.0F, te.getFullHeight(), 1.0F);
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public AxisAlignedBB getSelectedBoundingBox(IBlockState state, World worldIn, BlockPos pos)
	{
		return  getCollisionBoundingBox(state, worldIn, pos).expand(0, 0.1, 0).offset(pos);
	}
	
	@Override
	public int getMetaFromState(IBlockState state) {
		return 0;
	}
	
	/*
	 * Fence, glass override
	 */
	@Override
    public boolean canBeConnectedTo(IBlockAccess world, BlockPos pos, EnumFacing facing) {
		return false;
	}
	@Deprecated
	@Override
    public BlockFaceShape getBlockFaceShape(IBlockAccess p_193383_1_, IBlockState p_193383_2_, BlockPos p_193383_3_, EnumFacing p_193383_4_)
    {
		if (p_193383_4_ == EnumFacing.UP) {
			// SNOW ONLY?
			return BlockFaceShape.SOLID;
		}
        return BlockFaceShape.UNDEFINED;
    }
	
	@Override
	public boolean onBlockActivated(World worldIn, BlockPos pos, IBlockState state, EntityPlayer playerIn, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
		ItemStack stack = playerIn.getHeldItem(hand);
		Block block = Block.getBlockFromItem(stack.getItem());
		TileRailBase te = TileRailBase.get(worldIn, pos);
		if (te != null) {
			if (block == Blocks.SNOW_LAYER) {
				if (!worldIn.isRemote) {
					te.handleSnowTick();
				}
				return true;
			}
			if (block == Blocks.SNOW) {
				if (!worldIn.isRemote) {
					for (int i = 0; i < 8; i ++) {
						((TileRailBase) te).handleSnowTick();
					}
				}
				return true;
			}
			if (stack.getItem().getToolClasses(stack).contains("shovel")) {
				if (!worldIn.isRemote) {
					te.cleanSnow();
					te.setSnowLayers(0);
					stack.damageItem(1, playerIn);
				}
			}
		}
		return super.onBlockActivated(worldIn, pos, state, playerIn, hand, facing, hitX, hitY, hitZ);
		
	}
}
