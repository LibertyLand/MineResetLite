package com.koletar.jj.mineresetlite;

import com.koletar.jj.mineresetlite.commands.MineCommands;
import com.koletar.jj.mineresetlite.commands.PluginCommands;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * @author jjkoletar
 * @author Artuto
 */

@SuppressWarnings("ResultOfMethodCallIgnored")
public class MineResetLite extends JavaPlugin
{
    public List<Mine> mines;
    private Logger logger;
    private CommandManager commandManager;
    private WorldEditPlugin worldEdit = null;
    private int saveTaskId = -1;
    private int resetTaskId = -1;

    static
    {
        ConfigurationSerialization.registerClass(Mine.class);
    }

    private static class IsMineFile implements FilenameFilter
    {
        public boolean accept(File file, String s)
        {
            return s.contains(".mine.yml");
        }
    }

    private static MineResetLite INSTANCE;

    static MineResetLite getInstance()
    {
        return INSTANCE;
    }

    public void onEnable()
    {
        INSTANCE = this;
        mines = new ArrayList<>();
        logger = getLogger();

        if(!(setupConfig()))
        {
            logger.severe("Since I couldn't setup config files properly, I guess this is goodbye. ");
            logger.severe("Plugin Loading Aborted!");
            return;
        }

        commandManager = new CommandManager();
        commandManager.register(MineCommands.class, new MineCommands(this));
        commandManager.register(CommandManager.class, commandManager);
        commandManager.register(PluginCommands.class, new PluginCommands(this));
        Locale locale = new Locale(Config.getLocale());
        Phrases.getInstance().initialize(locale);
        File overrides = new File(getDataFolder(), "phrases.properties");

        if(overrides.exists())
        {
            Properties overridesProps = new Properties();
            try
            {
                overridesProps.load(new FileInputStream(overrides));
            }
            catch(IOException e)
            {
                e.printStackTrace();
            }

            Phrases.getInstance().overrides(overridesProps);
        }

        //Look for worldedit
        if(getServer().getPluginManager().isPluginEnabled("WorldEdit"))
            worldEdit = (WorldEditPlugin) getServer().getPluginManager().getPlugin("WorldEdit");

        //Load mines
        File[] mineFiles = new File(getDataFolder(), "mines").listFiles(new IsMineFile());
        for(File file : Objects.requireNonNull(mineFiles))
        {
            logger.info("Loading mine from file '" + file.getName() + "'...");
            FileConfiguration fileConf = YamlConfiguration.loadConfiguration(file);
            try
            {
                Object o = fileConf.get("mine");
                if(!(o instanceof Mine))
                {
                    logger.severe("Mine wasn't a mine object! Something is off with serialization!");
                    continue;
                }

                Mine mine = (Mine) o;
                mines.add(mine);
                // TODO flag
                //mine.reset();
            }
            catch(Throwable t)
            {
                logger.severe("Unable to load mine!");
            }
        }

        resetTaskId = getServer().getScheduler().scheduleSyncRepeatingTask(this, () ->
        {
            for(Mine mine : mines)
            {
                mine.cron();
            }
        }, 60 * 20L, 60 * 20L);

        logger.info("MineResetLite version " + getDescription().getVersion() + " enabled!");
    }

    public void onDisable()
    {
        getServer().getScheduler().cancelTask(resetTaskId);
        getServer().getScheduler().cancelTask(saveTaskId);
        HandlerList.unregisterAll(this);

        logger.info("MineResetLite disabled");
    }

    public Material matchMaterial(String name)
    {
        // If anyone can think of a more elegant way to serve this function, let me know. ~jj
        // Done ~Artuto
        switch(name)
        {
            case "diamondore":
                return Material.DIAMOND_ORE;
            case "diamondblock":
                return Material.DIAMOND_BLOCK;
            case "ironore":
                return Material.IRON_ORE;
            case "ironblock":
                return Material.IRON_BLOCK;
            case "goldore":
                return Material.GOLD_ORE;
            case "goldblock":
                return Material.GOLD_BLOCK;
            case "coalore":
                return Material.COAL_ORE;
            case "cake":
            case "cakeblock":
                return Material.CAKE;
            case "emeraldore":
                return Material.EMERALD_ORE;
            case "emeraldblock":
                return Material.EMERALD_BLOCK;
            case "lapisore":
                return Material.LAPIS_ORE;
            case "lapisblock":
                return Material.LAPIS_BLOCK;
            case "snowblock": //I've never seen a mine with snowFALL in it.
            case "snow": //Maybe I'll be proven wrong, but it helps 99% of admins.
                return Material.SNOW_BLOCK;
            case "redstoneore":
                return Material.REDSTONE_ORE;
            default:
                return Material.matchMaterial(name);
        }
    }

    public Mine[] matchMines(String in)
    {
        List<Mine> matches = new LinkedList<>();
        boolean wildcard = in.contains("*");
        in = in.replace("*", "").toLowerCase();
        for(Mine mine : mines)
        {
            if(wildcard)
            {
                if(mine.getName().toLowerCase().contains(in))
                    matches.add(mine);
            }
            else
            {
                if(mine.getName().equalsIgnoreCase(in))
                    matches.add(mine);
            }
        }

        return matches.toArray(new Mine[0]);
    }

    public String toString(Mine[] mines)
    {
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < mines.length; i++)
        {
            if(i > 0)
            {
                sb.append(", ");
            }
            Mine mine = mines[i];
            sb.append(mine.getName());
        }
        return sb.toString();
    }

    /**
     * Alert the plugin that changes have been made to mines, but wait 60 seconds before we save.
     * This process saves on disk I/O by waiting until a long string of changes have finished before writing to disk.
     */
    public void buffSave()
    {
        BukkitScheduler scheduler = getServer().getScheduler();
        if(!(saveTaskId == -1))
        {
            //Cancel old task
            scheduler.cancelTask(saveTaskId);
        }

        //Schedule save
        final MineResetLite plugin = this;
        scheduler.scheduleSyncDelayedTask(this, plugin::save, 60 * 20L);
    }

    private void save()
    {
        for(Mine mine : mines)
        {
            File mineFile = getMineFile(mine);
            FileConfiguration mineConf = YamlConfiguration.loadConfiguration(mineFile);
            mineConf.set("mine", mine);
            try
            {
                mineConf.save(mineFile);
            }
            catch(IOException e)
            {
                logger.severe("Unable to serialize mine!");
                e.printStackTrace();
            }
        }
    }

    private File getMineFile(Mine mine)
    {
        return new File(new File(getDataFolder(), "mines"), mine.getName().replace(" ", "") + ".mine.yml");
    }

    public void eraseMine(Mine mine)
    {
        mines.remove(mine);
        getMineFile(mine).delete();
    }

    public WorldEditPlugin getWorldEdit()
    {
        return worldEdit;
    }

    private boolean setupConfig()
    {
        File pluginFolder = getDataFolder();
        if(!pluginFolder.exists() && !pluginFolder.mkdir())
        {
            logger.severe("Could not make plugin folder! This won't end well...");
            return false;
        }

        File mineFolder = new File(getDataFolder(), "mines");
        if(!mineFolder.exists() && !mineFolder.mkdir())
        {
            logger.severe("Could not make mine folder! Abort! Abort!");
            return false;
        }

        try
        {
            Config.initConfig(getDataFolder());
        }
        catch(IOException e)
        {
            logger.severe("Could not make config file!");
            e.printStackTrace();
            return false;
        }

        return true;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
    {
        if(command.getName().equalsIgnoreCase("mineresetlite"))
        {
            if(args.length == 0)
            {
                String[] helpArgs = new String[0];
                commandManager.callCommand("help", sender, helpArgs);
                return true;
            }

            //Spoof args array to account for the initial subcommand specification
            String[] spoofedArgs = new String[args.length - 1];
            System.arraycopy(args, 1, spoofedArgs, 0, args.length - 1);
            commandManager.callCommand(args[0], sender, spoofedArgs);
            return true;
        }

        return false; //Fallthrough
    }

    public static void broadcast(String message, Mine mine)
    {
        if(Config.getBroadcastNearbyOnly())
        {
            for(Player p : mine.getWorld().getPlayers())
            {
                if(mine.isInside(p))
                    p.sendMessage(message);
            }

            Bukkit.getLogger().info(message);
        }
        else if(Config.getBroadcastInWorldOnly())
        {
            for(Player p : mine.getWorld().getPlayers())
                p.sendMessage(message);

            Bukkit.getLogger().info(message);
        }
        else
            Bukkit.getServer().broadcastMessage(message);
    }
}
