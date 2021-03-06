package com.koletar.jj.mineresetlite;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import xyz.liblnd.mrlp.events.MineResetEvent;
import xyz.liblnd.mrlp.events.MineUpdateEvent;
import xyz.liblnd.mrlp.events.PreMineResetEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * @author jjkoletar
 * @author Artuto
 */

@SuppressWarnings("unused")
public class Mine implements ConfigurationSerializable
{
    private int minX;
    private int minY;
    private int minZ;
    private int maxX;
    private int maxY;
    private int maxZ;
    private World world;
    private Map<SerializableBlock, Double> composition;
    private Set<SerializableBlock> structure; // structure material defining the mine walls, radder, etc.
    private int resetDelay;
    private List<Integer> resetWarnings;
    private String name;
    private SerializableBlock surface;
    private boolean fillMode;
    private int resetClock;
    private boolean isSilent;
    private boolean resetOnStart; // LibertyLand
    private int tpX = 0;
    private int tpY = -Integer.MAX_VALUE;
    private int tpZ = 0;
    private int tpYaw = 0;
    private int tpPitch = 0;

    // from MineResetLitePlus
    private double resetPercent = -1.0;
    private transient int maxCount = 0;
    private transient int currentBroken = 0;

    /*private List<PotionEffect> potions = new ArrayList<>();

    int luckyBlocks = 0;
    List<Integer> luckyNum;
    List<String> luckyCommands;*/

    public Mine(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, String name, World world)
    {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
        this.name = name;
        this.world = world;
        composition = new HashMap<>();
        resetWarnings = new LinkedList<>();
        structure = new HashSet<>();
        /*luckyNum = new ArrayList<>();
        luckyCommands = new ArrayList<>();*/

        setMaxCount();
    }

    @SuppressWarnings("unchecked")
    public Mine(Map<String, Object> me)
    {
        try
        {
            minX = (Integer) me.get("minX");
            minY = (Integer) me.get("minY");
            minZ = (Integer) me.get("minZ");
            maxX = (Integer) me.get("maxX");
            maxY = (Integer) me.get("maxY");
            maxZ = (Integer) me.get("maxZ");

            setMaxCount();
        }
        catch(Throwable t)
        {
            throw new IllegalArgumentException("Error deserializing coordinate pairs");
        }

        try
        {
            world = Bukkit.getServer().getWorld((String) me.get("world"));
        }
        catch(Throwable t)
        {
            throw new IllegalArgumentException("Error finding world");
        }

        if(world == null)
        {
            Logger l = Bukkit.getLogger();
            l.severe("[MineResetLite] Unable to find a world! Please include these logger lines along with the stack trace when reporting this bug!");
            l.severe("[MineResetLite] Attempted to load world named: " + me.get("world"));
            l.severe("[MineResetLite] Worlds listed: " + StringTools.buildList(Bukkit.getWorlds(), "", ", "));
            throw new IllegalArgumentException("World was null!");
        }

        try
        {
            Map<String, Double> sComposition = (Map<String, Double>) me.get("composition");
            composition = new HashMap<>();
            for(Map.Entry<String, Double> entry : sComposition.entrySet())
            {
                composition.put(new SerializableBlock(entry.getKey()), entry.getValue());
            }
        }
        catch(Throwable t)
        {
            throw new IllegalArgumentException("Error deserializing composition");
        }

        try
        {
            List<String> sStructure = (List<String>) me.get("structure");
            structure = new HashSet<>();
            for(String entry : sStructure)
            {
                structure.add(new SerializableBlock(entry));
            }
        }
        catch(Throwable t)
        {
            throw new IllegalArgumentException("Error deserializing structure");
        }

        name = (String) me.get("name");
        resetDelay = (Integer) me.get("resetDelay");
        List<String> warnings = (List<String>) me.get("resetWarnings");
        resetWarnings = new LinkedList<>();
        for(String warning : warnings)
        {
            try
            {
                resetWarnings.add(Integer.valueOf(warning));
            }
            catch(NumberFormatException nfe)
            {
                throw new IllegalArgumentException("Non-numeric reset warnings supplied");
            }
        }

        if(me.containsKey("surface"))
        {
            if(!(me.get("surface").equals("")))
                surface = new SerializableBlock((String) me.get("surface"));
        }

        if(me.containsKey("fillMode"))
            fillMode = (Boolean) me.get("fillMode");

        if(me.containsKey("resetClock"))
            resetClock = (Integer) me.get("resetClock");

        //Compat for the clock
        if(resetDelay > 0 && resetClock == 0)
            resetClock = resetDelay;

        if(me.containsKey("isSilent"))
            isSilent = (Boolean) me.get("isSilent");

        if(me.containsKey("resetOnStart"))
            resetOnStart = (boolean) me.get("resetOnStart");

        if(me.containsKey("tpY"))
        {
            // Should contain all three if it contains this one
            tpX = (int) me.get("tpX");
            tpY = (int) me.get("tpY");
            tpZ = (int) me.get("tpZ");
        }

        if(me.containsKey("tpYaw"))
        {
            tpYaw = (int) me.get("tpYaw");
            tpPitch = (int) me.get("tpPitch");
        }

        if(me.containsKey("resetPercent"))
            resetPercent = (double) me.get("resetPercent");

        if(me.containsKey("currentBroken"))
            currentBroken = (int) me.get("currentBroken");

        /*if(me.containsKey("potions"))
        {
            potions = new ArrayList<>();
            Map<String, Integer> potionpairs = (Map<String, Integer>) me.get("potions");
            for(Map.Entry<String, Integer> entry : potionpairs.entrySet())
            {
                String name = entry.getKey();
                int amp = entry.getValue();
                PotionEffect pot = new PotionEffect(PotionEffectType.getByName(name), Integer.MAX_VALUE, amp);
                potions.add(pot);
            }
        }

        if(me.containsKey("lucky_blocks"))
        {
            setLuckyBlockNum((int) me.get("lucky_blocks"));
        }
        if(me.containsKey("lucky_commands"))
        {
            setLuckyCommands((List<String>) me.get("lucky_commands"));
        }*/
    }

    @Override
    public Map<String, Object> serialize()
    {
        Map<String, Object> me = new HashMap<>();
        me.put("minX", minX);
        me.put("minY", minY);
        me.put("minZ", minZ);
        me.put("maxX", maxX);
        me.put("maxY", maxY);
        me.put("maxZ", maxZ);
        me.put("world", world.getName());
        //Make string form of composition
        Map<String, Double> sComposition = new HashMap<>();
        for(Map.Entry<SerializableBlock, Double> entry : composition.entrySet())
            sComposition.put(entry.getKey().toString(), entry.getValue());

        me.put("composition", sComposition);
        List<String> sStructure = new ArrayList<>();
        for(SerializableBlock entry : structure)
            sStructure.add(entry.toString());

        me.put("structure", sStructure);
        me.put("name", name);
        me.put("resetDelay", resetDelay);
        List<String> warnings = new LinkedList<>();
        for(Integer warning : resetWarnings)
            warnings.add(warning.toString());

        me.put("resetWarnings", warnings);
        if(!(surface == null))
            me.put("surface", surface.toString());
        else
            me.put("surface", "");

        me.put("fillMode", fillMode);
        me.put("resetClock", resetClock);
        me.put("isSilent", isSilent);
        me.put("resetOnStart", resetOnStart);
        me.put("tpX", tpX);
        me.put("tpY", tpY);
        me.put("tpZ", tpZ);
        me.put("tpYaw", tpYaw);
        me.put("tpPitch", tpPitch);
        me.put("resetPercent", resetPercent);
        me.put("currentBroken", currentBroken);

        /*Map<String, Integer> potionpairs = new HashMap<>();
        for(PotionEffect pe : this.potions)
        {
            potionpairs.put(pe.getType().getName(), pe.getAmplifier());
        }
        me.put("potions", potionpairs);

        me.put("lucky_blocks", luckyBlocks);
        me.put("lucky_commands", luckyCommands);*/

        return me;
    }

    public boolean getFillMode()
    {
        return fillMode;
    }

    public void setFillMode(boolean fillMode)
    {
        this.fillMode = fillMode;
    }

    public void setResetDelay(int minutes)
    {
        resetDelay = minutes;
        resetClock = minutes;
    }

    public void setResetWarnings(List<Integer> warnings)
    {
        resetWarnings = warnings;
    }

    public List<Integer> getResetWarnings()
    {
        return resetWarnings;
    }

    public int getResetDelay()
    {
        return resetDelay;
    }

    /**
     * Return the length of time until the next automatic reset.
     * The actual length of time is anywhere between n and n-1 minutes.
     *
     * @return clock ticks left until reset
     */
    public int getTimeUntilReset()
    {
        return resetClock;
    }

    public SerializableBlock getSurface()
    {
        return surface;
    }

    public void setSurface(SerializableBlock surface)
    {
        this.surface = surface;
    }

    public World getWorld()
    {
        return world;
    }

    public String getName()
    {
        return name;
    }

    public Map<SerializableBlock, Double> getComposition()
    {
        return composition;
    }

    public Set<SerializableBlock> getStructure()
    {
        return structure;
    }

    public boolean isSilent()
    {
        return isSilent;
    }

    public void setSilence(boolean isSilent)
    {
        this.isSilent = isSilent;
    }

    public boolean isResetOnStart()
    {
        return resetOnStart;
    }

    public void setResetOnStart(boolean resetOnStart)
    {
        this.resetOnStart = resetOnStart;
    }

    public double getCompositionTotal()
    {
        double total = 0;
        for(Double d : composition.values())
            total += d;

        return total;
    }

    public boolean isInside(Player p)
    {
        return isInside(p.getLocation());
    }

    public boolean isInside(Location l)
    {
        return l.getWorld().equals(world) && (l.getBlockX() >= minX && l.getBlockX() <= maxX) && (l.getBlockY() >= minY && l.getBlockY() <= maxY) && (l.getBlockZ() >= minZ && l.getBlockZ() <= maxZ);
    }

    public void setTp(Location l)
    {
        tpX = l.getBlockX();
        tpY = l.getBlockY();
        tpZ = l.getBlockZ();
        tpYaw = (int) l.getYaw();
        tpPitch = (int) l.getPitch();
    }

    public Location getTp()
    {
        return new Location(getWorld(), tpX, tpY, tpZ, tpYaw, tpPitch);
    }

    public void reset(MineResetEvent.Cause cause)
    {
        PreMineResetEvent pmre = new PreMineResetEvent(this);
        Bukkit.getServer().getPluginManager().callEvent(pmre);
        if(pmre.isCancelled())
            return;

        new ResetMineTask(this).run();
        resetMRLP(cause);
    }

    private transient Set<Material> mineMaterials;
    private transient Set<Material> exceptions;  // these material won't be replaced.

    private void setMineMaterials()
    {
        if(mineMaterials == null)
            mineMaterials = new HashSet<>();

        for(SerializableBlock sb : this.composition.keySet())
            mineMaterials.add(sb.getType());
    }

    private void setStructureMaterials()
    {
        if(exceptions == null)
            exceptions = new HashSet<>();

        for(SerializableBlock sb : this.structure)
            exceptions.add(sb.getType());
    }

    private boolean shouldBeFilled(Material mat)
    {
        if(mineMaterials == null || mineMaterials.size() == 0)
            setMineMaterials();
        if(exceptions == null)
            setStructureMaterials();

        return (mat == Material.AIR || (!mineMaterials.contains(mat) && !exceptions.contains(mat)));
    }

    void cron()
    {
        if(resetDelay == 0)
            return;
        if(resetClock > 0)
            resetClock--; //Tick down to the reset
        if(resetClock == 0)
        {
            if(!isSilent)
                MineResetLite.broadcast(Phrases.phrase("mineAutoResetBroadcast", this), this);

            reset(MineResetEvent.Cause.AUTOMATIC);
            resetClock = resetDelay;
            return;
        }

        //if (!isSilent) {
        for(Integer warning : resetWarnings)
        {
            if(warning == resetClock)
                MineResetLite.broadcast(Phrases.phrase("mineWarningBroadcast", this, warning), this);
        }
        //}
    }

    public void teleport(Player player)
    {
        Location location;

        if(!getTp().equals(new Location(world, 0, -Integer.MAX_VALUE, 0)))
            location = getTp();
        else
        {
            Location max = new Location(world, Math.max(this.maxX, this.minX), this.maxY, Math.max(this.maxZ, this.minZ));
            Location min = new Location(world, Math.min(this.maxX, this.minX), this.minY, Math.min(this.maxZ, this.minZ));

            location = max.add(min).multiply(0.5);
            Block block = location.getBlock();

            if(block.getType() != Material.AIR || block.getRelative(BlockFace.UP).getType() != Material.AIR)
                location = new Location(world, location.getX(), location.getWorld().getHighestBlockYAt(location.getBlockX(), location.getBlockZ()), location.getZ());
        }

        player.teleportAsync(location);
    }

    private void setMaxCount()
    {
        int dx = maxX - minX + 1;
        int dy = maxY - minY + 1;
        int dz = maxZ - minZ + 1;

        this.maxCount = dx * dy * dz;
    }

    public int getMaxCount()
    {
        return this.maxCount;
    }

    public void setResetPercent(double per)
    {
        this.resetPercent = per;
    }

    public double getResetPercent()
    {
        return this.resetPercent;
    }

    public void setBrokenBlocks(int broken)
    {
        this.currentBroken = broken;

        // send mine changed event
        MineUpdateEvent mue = new MineUpdateEvent(this, MineUpdateEvent.Cause.BROKEN_BLOCK);
        Bukkit.getServer().getPluginManager().callEvent(mue);
        final Mine thisMine = this;
        final boolean silent = this.isSilent;
        if(this.resetPercent > 0 && this.currentBroken >= (this.maxCount * (1.0 - this.resetPercent)))
        {
            new BukkitRunnable()
            {
                @Override
                public void run()
                {
                    reset(MineResetEvent.Cause.PERCENTAGE);
                    if(!(silent))
                        MineResetLite.broadcast(Phrases.phrase("mineAutoResetBroadcast", thisMine), thisMine);
                }
            }.runTask(MineResetLite.getInstance());
        }
    }

    public int getBrokenBlocks()
    {
        return this.currentBroken;
    }

    private void resetMRLP(MineResetEvent.Cause cause)
    {
        this.currentBroken = 0;
        MineResetEvent mre = new MineResetEvent(this, cause);
        Bukkit.getServer().getPluginManager().callEvent(mre);
        //resetLuckyNumbers();
    }

    public void redefine(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, World world)
    {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
        this.world = world;
    }

    public static class MineSelection
    {
        private final World world;
        private final Vector p1, p2;

        public MineSelection(World world, Vector p1, Vector p2)
        {
            this.world = world;
            this.p1 = p1;
            this.p2 = p2;
        }

        public World getWorld()
        {
            return world;
        }

        public Vector getP1()
        {
            return p1;
        }

        public Vector getP2()
        {
            return p2;
        }
    }

    public int getMinX()
    {
        return minX;
    }

    public int getMinY()
    {
        return minY;
    }

    public int getMinZ()
    {
        return minZ;
    }

    public int getMaxX()
    {
        return maxX;
    }

    public int getMaxY()
    {
        return maxY;
    }

    public int getMaxZ()
    {
        return maxZ;
    }

    /*public List<PotionEffect> getPotions()
    {
        return this.potions;
    }

    public PotionEffect addPotion(String potstr)
    {
        PotionEffect pot = null;
        String[] tokens = potstr.split(":");
        int amp = 1;
        try
        {
            if(tokens.length > 1)
            {
                amp = Integer.valueOf(tokens[1]);
            }
        }
        catch(Throwable ignore)
        {

        }
        //System.out.println("token[0]" + tokens[0]);
        //System.out.println("token[1]" + tokens[1]);
        try
        {
            removePotion(tokens[0]);
            pot = new PotionEffect(PotionEffectType.getByName(tokens[0]), Integer.MAX_VALUE, amp);
            potions.add(pot);
        }
        catch(Throwable ignore)
        {

        }

        return pot;
    }

    public void removePotion(String pot)
    {
        PotionEffect found = null;
        for(PotionEffect pe : potions)
        {
            if(pe.getType().getName().equalsIgnoreCase(pot))
            {
                found = pe;
                break;
            }
        }
        if(found != null)
            potions.remove(found);
    }

    public void setLuckyBlockNum(int num)
    {
        this.luckyBlocks = num;
        resetLuckyNumbers();
    }

    private void resetLuckyNumbers()
    {
        if(this.luckyNum == null)
        {
            this.luckyNum = new ArrayList<>();
        }
        this.luckyNum.clear();
        for(int i = 0; i < this.luckyBlocks; i++)
        {
            int num = RAND.nextInt(this.maxCount) + 1;
            this.luckyNum.add(num);
            //System.out.println("Mine: " + mine + " : lucky block = " + num);
        }
    }

    private boolean isLuckyNumber(int num)
    {
        return this.luckyNum.contains(num);
    }

    public boolean executeLuckyCommand(Player p)
    {
        if(isLuckyNumber(this.currentBroken) && this.luckyCommands.size() > 0)
        {
            int id = RAND.nextInt(this.luckyCommands.size());
            String cmd = this.luckyCommands.get(id).replace("%player%", p.getName());
            String[] cmds = cmd.split(";");
            try
            {
                for(String c : cmds)
                {
                    Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), c.trim());
                }
                // play effect
                if(Config.getLuckyEffect() != null)
                    p.getWorld().playEffect(p.getLocation(), Config.getLuckyEffect(), 0, 20);
                if(Config.getLuckySound() != null)
                    p.getWorld().playSound(p.getLocation(), Config.getLuckySound(), 1F, 1F);
                return true;
            }
            catch(Exception e)
            {
                e.printStackTrace();
            }
        }
        return false;
    }

    void setLuckyCommands(List<String> list)
    {
        this.luckyCommands = list;
    }

    public void makeLucky(int num)
    {
        this.luckyNum.set(0, num);
    }*/
}
