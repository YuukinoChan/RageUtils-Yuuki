package wintahh.rageutils.module;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.item.Items;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;

import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;

import wintahh.rageutils.HudConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ClientSideBlast extends Module {

    public record PredictedBreak(BlockPos pos, BlockState oldState) {}

    public ClientSideBlast() {
        super("ClientSideBlast", "/yuuki csb");
    }

    public boolean shouldBlast(BlockPos pos) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return false;

        ItemStack held = mc.player.getMainHandStack();

        // if held isnt a tool that can have blast
        if (!held.isIn(ItemTags.PICKAXES) &&
            !held.isIn(ItemTags.AXES) &&
            !held.isIn(ItemTags.SHOVELS) &&
            !held.isIn(ItemTags.HOES) &&
            held.getItem() != Items.SHEARS) {
            return false;
        }

        // check for blast enchant in the lore
        LoreComponent lore = held.get(DataComponentTypes.LORE);
        if (lore == null) return false;
        boolean hasBlastEnchant = lore.lines().stream()
            .anyMatch(line -> line.getString().equals("Blast I"));
        if (!hasBlastEnchant) return false;

        // for some reason shears dont work with isSuitable so we're calling them early
        if (held.getItem() == Items.SHEARS) {
            if (mc.world.getBlockState(pos).isIn(BlockTags.WOOL) ||
                mc.world.getBlockState(pos).isIn(BlockTags.LEAVES)) return true;
        }

        // is it da right tool fo' da job??!?!!?!?
        BlockState state = mc.world.getBlockState(pos);
        if (!held.isSuitableFor(state)) return false;

        return true;
    }

    public List<PredictedBreak> planBreaks(BlockPos pos, Direction face) {
        if (!isEnabled()) return Collections.emptyList();
        if (!shouldBlast(pos)) return Collections.emptyList();

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return Collections.emptyList();

        ItemStack held = mc.player.getMainHandStack();

        Direction.Axis faceAxis = face.getAxis();
        Direction.Axis axisA = null;
        Direction.Axis axisB = null;
        for (Direction.Axis axis : Direction.Axis.VALUES) {
            if (axis == faceAxis) continue;
            if (axisA == null) axisA = axis;
            else axisB = axis;
        }

        List<PredictedBreak> breaks = new ArrayList<>(9);
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                // Skip the centre block — it is being broken by the normal mining path
                if (i == 0 && j == 0) continue;
                BlockPos target = offset(pos, axisA, i, axisB, j);
                BlockState old = mc.world.getBlockState(target);
                // Only predict neighbours the held tool can actually break, so unbreakable
                // blocks don't flash to air and pop back after the server correction.
                if (!canBlastBreak(held, old)) continue;
                breaks.add(new PredictedBreak(target.toImmutable(), old));
            }
        }
        return breaks;
    }

    // Mirrors the breakability rules in shouldBlast: shears clear wool/leaves, every other
    // tool relies on isSuitableFor. Air is never breakable.
    private boolean canBlastBreak(ItemStack held, BlockState old) {
        if (old == null || old.isAir()) return false;
        if (held.getItem() == Items.SHEARS) {
            return old.isIn(BlockTags.WOOL) || old.isIn(BlockTags.LEAVES);
        }
        return held.isSuitableFor(old);
    }

    public void applyPredictedBreaks(List<PredictedBreak> breaks) {
        if (!isEnabled() || breaks.isEmpty()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        boolean particles = HudConfig.INSTANCE.csbParticles;
        boolean sound = HudConfig.INSTANCE.csbSound;

        for (PredictedBreak pb : breaks) {
            BlockState old = pb.oldState();
            mc.world.setBlockState(pb.pos(), Blocks.AIR.getDefaultState());

            if (old == null || old.isAir()) continue;

            if (particles) {
                mc.world.addBlockBreakParticles(pb.pos(), old);
            }
            if (sound) {
                BlockSoundGroup group = old.getSoundGroup();
                SoundCompat.playClientSound(
                    mc,
                    pb.pos().getX() + 0.5,
                    pb.pos().getY() + 0.5,
                    pb.pos().getZ() + 0.5,
                    group.getBreakSound(),
                    SoundCategory.BLOCKS,
                    (group.getVolume() + 1.0F) / 2.0F,
                    group.getPitch() * 0.8F,
                    false
                );
            }
        }
    }

    private BlockPos offset(BlockPos origin, Direction.Axis axisA, int a, Direction.Axis axisB, int b) {
        int x = origin.getX();
        int y = origin.getY();
        int z = origin.getZ();

        if (axisA == Direction.Axis.X) x += a; else if (axisA == Direction.Axis.Y) y += a; else z += a;
        if (axisB == Direction.Axis.X) x += b; else if (axisB == Direction.Axis.Y) y += b; else z += b;

        return new BlockPos(x, y, z);
    }
}
