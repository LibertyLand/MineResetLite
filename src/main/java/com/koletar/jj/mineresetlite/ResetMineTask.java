package com.koletar.jj.mineresetlite;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class ResetMineTask extends BukkitRunnable
{
    private final Mine mine;

    public ResetMineTask(Mine mine)
    {
        this.mine = mine;
    }

    @Override
    public void run()
    {
        MineResetLite.getInstance().newChain()
                .asyncFirst(() -> mapComposition(mine.getComposition()))
                .async(this::mapBlocksToChange)
                .syncLast(this::setBlocks)
                .execute();
    }

    private List<CompositionEntry> mapComposition(Map<SerializableBlock, Double> compositionIn)
    {
        List<CompositionEntry> probabilityMap = new ArrayList<>();
        Map<SerializableBlock, Double> composition = new HashMap<>(compositionIn);
        double max = 0;
        for(Map.Entry<SerializableBlock, Double> entry : composition.entrySet())
            max += entry.getValue();

        //Pad the remaining percentages with air
        if(max < 1)
        {
            composition.put(new SerializableBlock(Material.AIR), 1 - max);
            max = 1;
        }

        double i = 0;
        for(Map.Entry<SerializableBlock, Double> entry : composition.entrySet())
        {
            double v = entry.getValue() / max;
            i += v;
            probabilityMap.add(new CompositionEntry(entry.getKey(), i));
        }

        return probabilityMap;
    }

    private List<BlockToChange> mapBlocksToChange(List<CompositionEntry> probability)
    {
        List<BlockToChange> list = new ArrayList<>();
        Random random = new Random();

        for(int x = mine.getMinX(); x <= mine.getMaxX(); ++x)
        {
            for(int y = mine.getMinY(); y <= mine.getMaxY(); ++y)
            {
                for(int z = mine.getMinZ(); z <= mine.getMaxZ(); ++z)
                {
                    double r = random.nextDouble();

                    for(CompositionEntry ce : probability)
                    {
                        if(r <= ce.getChance())
                        {
                            list.add(new BlockToChange(x, y, z, ce.getBlock().getType()));
                            break;
                        }
                    }
                }
            }
        }

        return list;
    }

    private void setBlocks(List<BlockToChange> blocks)
    {
        teleportPlayers();

        AtomicInteger count = new AtomicInteger();

        for(BlockToChange change : blocks)
        {
            if(count.get() == 1000)
            {
                count.set(0);
                Bukkit.getScheduler().runTaskLater(MineResetLite.getInstance(), () ->
                {
                    count.getAndIncrement();
                    mine.getWorld().getBlockAt(change.x, change.y, change.z).setType(change.material);
                }, 1);
            }
            else
            {
                count.getAndIncrement();
                mine.getWorld().getBlockAt(change.x, change.y, change.z).setType(change.material);
            }
        }
    }

    private void teleportPlayers()
    {
        for(Player p : Bukkit.getServer().getOnlinePlayers())
        {
            Location l = p.getLocation();

            if(mine.isInside(p))
            {
                if(mine.getTp().getY() > -Integer.MAX_VALUE)
                    p.teleport(mine.getTp());
                else
                {
                    // empty spawn location!
                    // find the safe landing location!
                    Location tp = new Location(mine.getWorld(), l.getX(), mine.getMaxY() + 1D, l.getZ());
                    Block block = tp.getBlock();

                    // check to make sure we don't suffocate player
                    if(block.getType() != Material.AIR || block.getRelative(BlockFace.UP).getType() != Material.AIR)
                    {
                        tp = new Location(mine.getWorld(), l.getX(), l.getWorld().getHighestBlockYAt(l.getBlockX(),
                                l.getBlockZ()), l.getZ());
                    }

                    p.teleportAsync(tp);
                }
            }
        }
    }

    private static class CompositionEntry
    {
        private SerializableBlock block;
        private double chance;

        CompositionEntry(SerializableBlock block, double chance)
        {
            this.block = block;
            this.chance = chance;
        }

        public SerializableBlock getBlock()
        {
            return block;
        }

        double getChance()
        {
            return chance;
        }
    }

    private static class BlockToChange
    {
        final int x, y, z;
        final Material material;

        private BlockToChange(int x, int y, int z, Material material)
        {
            this.x = x;
            this.y = y;
            this.z = z;
            this.material = material;
        }
    }
}
