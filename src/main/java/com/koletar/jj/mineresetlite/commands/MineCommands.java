package com.koletar.jj.mineresetlite.commands;

import com.koletar.jj.mineresetlite.Command;
import com.koletar.jj.mineresetlite.Mine;
import com.koletar.jj.mineresetlite.Mine.MineSelection;
import com.koletar.jj.mineresetlite.MineResetLite;
import com.koletar.jj.mineresetlite.Phrases;
import com.koletar.jj.mineresetlite.SerializableBlock;
import com.koletar.jj.mineresetlite.StringTools;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.regions.Region;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import xyz.liblnd.mrlp.events.MineResetEvent;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.koletar.jj.mineresetlite.Phrases.phrase;

/**
 * @author jjkoletar
 */
public class MineCommands
{
    private MineResetLite plugin;

    public MineCommands(MineResetLite plugin)
    {
        this.plugin = plugin;
    }

    @Command(aliases = {"list", "l"}, description = "List the names of all Mines",
            permissions = {"mineresetlite.mine.list"},
            help = {"List the names of all Mines currently created, across all worlds."},
            max = 0)
    public void listMines(CommandSender sender, String[] args)
    {
        sender.sendMessage(phrase("mineList", StringTools.buildList(plugin.mines, "&c", "&d, ")));
    }

    @SuppressWarnings("DuplicatedCode")
    @Command(aliases = {"create", "save"}, description = "Create a mine from either your WorldEdit selection or by " +
            "manually specifying the points",
            help = {"Provided you have a selection made via either WorldEdit or selecting the points using MRL,",
                    "an empty mine will be created. This mine will have no composition and default settings."},
            usage = "<mine name>", permissions = {"mineresetlite.mine.create"}, min = 1, onlyPlayers = true)
    public void createMine(CommandSender sender, String[] args)
    {
        Player player = (Player) sender;
        MineSelection selection = getMineSelection(player);
        if(selection == null)
            return;

        World world = selection.getWorld();
        Vector p1 = selection.getP1();
        Vector p2 = selection.getP2();

        //Construct mine name
        String name = StringTools.buildSpacedArgument(args);

        //Verify uniqueness of mine name
        Mine[] mines = plugin.matchMines(name);
        if(mines.length > 0)
        {
            player.sendMessage(phrase("nameInUse", name));
            return;
        }

        //Sort coordinates
        prepareCoordinates(p1, p2);

        //Create!
        Mine newMine = new Mine(p1.getBlockX(), p1.getBlockY(), p1.getBlockZ(), p2.getBlockX(),
                p2.getBlockY(), p2.getBlockZ(), name, world);
        plugin.mines.add(newMine);
        player.sendMessage(phrase("mineCreated", newMine));
        plugin.buffSave();
    }

    @Command(aliases = {"info", "i"}, description = "List information about a mine",
            usage = "<mine name>", permissions = {"mineresetlite.mine.info"}, min = 1)
    public void mineInfo(CommandSender sender, String[] args)
    {
        Mine[] mines = plugin.matchMines(StringTools.buildSpacedArgument(args));
        if(invalidMines(sender, mines))
            return;

        sender.sendMessage(phrase("mineInfoName", mines[0]));
        sender.sendMessage(phrase("mineInfoWorld", mines[0].getWorld()));

        //Build composition list
        StringBuilder csb = new StringBuilder();
        for(Map.Entry<SerializableBlock, Double> entry : mines[0].getComposition().entrySet())
        {
            csb.append(entry.getValue() * 100);
            csb.append("% ");
            csb.append(new ItemStack(entry.getKey().getType()).getI18NDisplayName());
            csb.append(", ");
        }

        if(csb.length() > 2)
            csb.delete(csb.length() - 2, csb.length() - 1);

        sender.sendMessage(phrase("mineInfoComposition", csb));
        if(mines[0].getResetDelay() != 0)
        {
            sender.sendMessage(phrase("mineInfoResetDelay", mines[0].getResetDelay()));
            sender.sendMessage(phrase("mineInfoTimeUntilReset", mines[0].getTimeUntilReset()));
        }

        sender.sendMessage(phrase("mineInfoSilence", mines[0].isSilent()));
        if(mines[0].getResetWarnings().size() > 0)
        {
            sender.sendMessage(phrase("mineInfoWarningTimes",
                    StringTools.buildList(mines[0].getResetWarnings(), "", ", ")));
        }

        if(mines[0].getSurface() != null)
            sender.sendMessage(phrase("mineInfoSurface", mines[0].getSurface()));

        if(mines[0].getFillMode())
            sender.sendMessage(phrase("mineInfoFillMode"));
    }

    private boolean invalidMines(CommandSender sender, Mine[] mines)
    {
        if(mines.length > 1)
        {
            sender.sendMessage(phrase("tooManyMines", plugin.toString(mines)));
            return true;
        }
        else if(mines.length == 0)
        {
            sender.sendMessage(phrase("noMinesMatched"));
            return true;
        }
        return false;
    }

    @Command(aliases = {"set", "add", "+"}, description = "Set the percentage of a block in the mine",
            help = {"This command will always overwrite the current percentage for the specified block,",
                    "if a percentage has already been set. You cannot set the percentage of any specific",
                    "block, such that the percentage would then total over 100%."},
            usage = "<mine name> <block> <percentage>%", permissions = {"mineresetlite.mine.composition"}, min = 3)
    public void setComposition(CommandSender sender, String[] args)
    {
        Mine[] mines = plugin.matchMines(StringTools.buildSpacedArgument(args, 2));
        if(invalidMines(sender, mines))
            return;

        //Match material
        Material m = matchMaterial(sender, args[2]);
        if(m == null)
            return;

        //Parse percentage
        String percentageS = args[2];
        if(!(percentageS.endsWith("%")))
        {
            sender.sendMessage(phrase("badPercentage"));
            return;
        }

        double percentage = parsePercentage(sender, args[2]);
        if(percentage > 100 || percentage <= 0)
        {
            sender.sendMessage(phrase("badPercentage"));
            return;
        }

        percentage = percentage / 100; //Make it a programmatic percentage
        SerializableBlock block = new SerializableBlock(m);

        double oldPercentage = mines[0].getComposition().getOrDefault(block, -1.0);
        double total = 0;

        for(Map.Entry<SerializableBlock, Double> entry : mines[0].getComposition().entrySet())
        {
            if(!(entry.getKey().equals(block)))
                total += entry.getValue();
            else
                block = entry.getKey();
        }

        total += percentage;
        if(total > 1)
        {
            sender.sendMessage(phrase("insaneCompositionChange", total, percentage));

            if(oldPercentage == -1.0)
                mines[0].getComposition().remove(block);
            else
                mines[0].getComposition().put(block, oldPercentage);

            return;
        }

        mines[0].getComposition().put(block, percentage);
        sender.sendMessage(phrase("mineCompositionSet", mines[0], Math.floor(percentage * 100), block,
                Math.round((1 - mines[0].getCompositionTotal()) * 100)));
        plugin.buffSave();
    }

    @Command(aliases = {"unset", "remove", "-"}, description = "Remove a block from the composition of a mine",
            usage = "<mine name> <block>:(data)", permissions = {"mineresetlite.mine.composition"}, min = 2)
    public void unsetComposition(CommandSender sender, String[] args)
    {
        Mine[] mines = plugin.matchMines(StringTools.buildSpacedArgument(args, 1));
        if(invalidMines(sender, mines))
            return;

        //Match material
        Material m = matchMaterial(sender, args[2]);
        if(m == null)
            return;

        //Does the mine contain this block?
        SerializableBlock block = new SerializableBlock(m);
        for(Map.Entry<SerializableBlock, Double> entry : mines[0].getComposition().entrySet())
        {
            if(entry.getKey().equals(block))
            {
                mines[0].getComposition().remove(entry.getKey());
                sender.sendMessage(phrase("blockRemovedFromMine",
                        mines[0], block, Math.round((1 - mines[0].getCompositionTotal()) * 100)));
                return;
            }
        }

        sender.sendMessage(phrase("blockNotInMine", mines[0], block));
        plugin.buffSave();
    }

    @Command(aliases = {"reset", "r"}, description = "Reset a mine",
            help = {"If you supply the -s argument, the mine will silently reset. Resets triggered via",
                    "this command will not show a 1 minute warning, unless this mine is flagged to always",
                    "have a warning. If the mine's composition doesn't equal 100%, the composition will be",
                    "padded with air until the total equals 100%."},
            usage = "<mine name> (-s)", permissions = {"mineresetlite.mine.reset"}, min = 1)
    public void resetMine(CommandSender sender, String[] args)
    {
        Mine[] mines = plugin.matchMines(StringTools.buildSpacedArgument(args).replace(" -s", ""));
        if(invalidMines(sender, mines))
            return;

        if(!(args[args.length - 1].equalsIgnoreCase("-s")))
            MineResetLite.broadcast(phrase("mineResetBroadcast", mines[0], sender), mines[0]);

        mines[0].reset(MineResetEvent.Cause.COMMAND);
    }

    @Command(aliases = {"flag", "f"}, description = "Set various properties of a mine, including automatic resets",
            help = {"Available flags:", "resetOnStart: Define if the mine should be reset on server start",
                    "resetPercent: A integer number (0 < x < 100) specifying the percentage of mined blocks triggering the reset. Set to -1 to disable automatic percent resets.",
                    "resetDelay: An integer number of minutes specifying the time between automatic resets. Set to 0 to disable automatic resets.",
                    "resetWarnings: A comma separated list of integer minutes to warn before the automatic reset. Warnings must be less than the reset delay.",
                    "surface: A block that will cover the entire top surface of the mine when reset, obscuring surface ores. Set surface to air to clear the value.",
                    "fillMode: An alternate reset algorithm that will only \"reset\" air blocks inside your mine. Set to true or false.",
                    "fillMode: An alternate reset algorithm that will only \"reset\" air blocks inside your mine. Set to true or false.",
                    "isSilent: A boolean (true or false) of whether or not this mine should broadcast a reset notification when it is reset *automatically*.",
                    "resetOnStart: A boolean (true or false) of whether the mine should be reset every time the server starts up."},
            usage = "<mine name> <setting> <value>", permissions = {"mineresetlite.mine.flag"}, min = 3)
    public void flag(CommandSender sender, String[] args)
    {
        Mine[] mines = plugin.matchMines(StringTools.buildSpacedArgument(args, 2));
        if(invalidMines(sender, mines))
            return;

        String setting = args[args.length - 2];
        String value = args[args.length - 1];

        switch(setting)
        {
            case "resetDelay":
            case "resetEvery":
            {
                int delay;

                try {delay = Integer.parseInt(value);}
                catch(NumberFormatException nfe)
                {
                    sender.sendMessage(phrase("badResetDelay"));
                    return;
                }

                if(delay < 0)
                {
                    sender.sendMessage(phrase("badResetDelay"));
                    return;
                }

                mines[0].setResetDelay(delay);
                if(delay == 0)
                    sender.sendMessage(phrase("resetDelayCleared", mines[0]));
                else
                    sender.sendMessage(phrase("resetDelaySet", mines[0], delay));

                plugin.buffSave();
                break;
            }

            case "resetWarnings":
            case "resetWarning":
            {
                String[] bits = value.split(",");
                List<Integer> warnings = mines[0].getResetWarnings();
                List<Integer> oldList = new LinkedList<>(warnings);
                warnings.clear();

                for(String bit : bits)
                {
                    try {warnings.add(Integer.valueOf(bit));}
                    catch(NumberFormatException nfe)
                    {
                        sender.sendMessage(phrase("badWarningList"));
                        return;
                    }
                }

                //Validate warnings
                for(int warning : warnings)
                {
                    if(warning >= mines[0].getResetDelay())
                    {
                        sender.sendMessage(phrase("badWarningList"));
                        mines[0].setResetWarnings(oldList);
                        return;
                    }
                }

                if(warnings.contains(0) && warnings.size() == 1)
                {
                    warnings.clear();
                    sender.sendMessage(phrase("warningListCleared", mines[0]));
                    return;
                }
                else if(warnings.contains(0))
                {
                    sender.sendMessage(phrase("badWarningList"));
                    mines[0].setResetWarnings(oldList);
                    return;
                }

                sender.sendMessage(phrase("warningListSet", mines[0]));
                plugin.buffSave();
                break;
            }

            case "surface":
            {
                //Match material
                Material m = matchMaterial(sender, value);
                if(m == null)
                    return;

                if(m == Material.AIR)
                {
                    mines[0].setSurface(null);
                    sender.sendMessage(phrase("surfaceBlockCleared", mines[0]));
                    plugin.buffSave();
                    return;
                }

                SerializableBlock block = new SerializableBlock(m);
                mines[0].setSurface(block);
                sender.sendMessage(phrase("surfaceBlockSet", mines[0]));
                plugin.buffSave();
                break;
            }

            case "fill":
            case "fillMode":
            {
                // Match true or false
                switch(value)
                {
                    case "true":
                    case "yes":
                    case "enabled":
                    {
                        mines[0].setFillMode(true);
                        sender.sendMessage(phrase("fillModeEnabled"));
                        plugin.buffSave();
                        break;
                    }

                    case "false":
                    case "no":
                    case "disabled":
                    {
                        mines[0].setFillMode(false);
                        sender.sendMessage(phrase("fillModeDisabled"));
                        plugin.buffSave();
                        break;
                    }

                    default:
                        sender.sendMessage(phrase("invalidFillMode"));
                }
            }

            case "isSilent":
            case "silent":
            case "silence":
            {
                switch(value)
                {
                    case "true":
                    case "yes":
                    case "enabled":
                    {
                        mines[0].setSilence(true);
                        sender.sendMessage(phrase("mineIsNowSilent", mines[0]));
                        plugin.buffSave();
                        break;
                    }

                    case "false":
                    case "no":
                    case "disabled":
                    {
                        mines[0].setSilence(false);
                        sender.sendMessage(phrase("mineIsNoLongerSilent", mines[0]));
                        plugin.buffSave();
                        break;
                    }

                    default:
                        sender.sendMessage(phrase("badBoolean"));
                }
            }

            case "resetPercent":
            {
                StringBuilder psb = new StringBuilder(value);
                psb.deleteCharAt(psb.length() - 1);
                double percentage = parsePercentage(sender, psb.toString());

                // < 0 is used to cancel percent reset setting.
                if(percentage > 100)
                {// || percentage <= 0) {
                    sender.sendMessage(phrase("badPercentage"));
                    return;
                }

                percentage = percentage / 100; //Make it a programmatic percentage
                mines[0].setResetPercent(percentage);

                if(percentage < 0)
                    sender.sendMessage(phrase("resetDelayCleared", mines[0]));
                else
                    sender.sendMessage(phrase("resetPercentageSet", mines[0], (int) (percentage * 100)));

                plugin.buffSave();
                break;
            }

            case "resetOnStart":
            case "resetAutomatically":
            {
                switch(value)
                {
                    case "true":
                    case "yes":
                    case "enabled":
                    {
                        mines[0].setResetOnStart(true);
                        sender.sendMessage(phrase("mineWillBeResetOnStart", mines[0]));
                        plugin.buffSave();
                        break;
                    }

                    case "false":
                    case "no":
                    case "disabled":
                    {
                        mines[0].setResetOnStart(false);
                        sender.sendMessage(phrase("mineWillNotBeResetOnStart", mines[0]));
                        plugin.buffSave();
                        break;
                    }

                    default:
                        sender.sendMessage(phrase("badBoolean"));
                }
            }

            default:
                sender.sendMessage(phrase("unknownFlag"));
        }
    }

    @Command(aliases = {"erase"}, description = "Completely erase a mine",
            help = {"Like most erasures of data, be sure you don't need to recover anything from this mine before you " +
                    "delete it."}, usage = "<mine name>", permissions = {"mineresetlite.mine.erase"}, min = 1)
    public void erase(CommandSender sender, String[] args)
    {
        Mine[] mines = plugin.matchMines(StringTools.buildSpacedArgument(args));
        if(invalidMines(sender, mines))
            return;

        plugin.eraseMine(mines[0]);
        sender.sendMessage(phrase("mineErased", mines[0]));
    }

    @Command(aliases = {"reschedule"}, description = "Synchronize all automatic mine resets",
            help = {"This command will set the 'start time' of the mine resets to the same point."},
            permissions = {"mineresetlite.mine.flag"}, max = 0)
    public void reschedule(CommandSender sender, String[] args)
    {
        for(Mine mine : plugin.mines)
            mine.setResetDelay(mine.getResetDelay());

        plugin.buffSave();
        sender.sendMessage(phrase("rescheduled"));
    }

    @Command(aliases = {"tp", "teleport"}, description = "Teleport to the specified mine",
            help = {"This command will teleport you to the center of the specified mine or at the teleport point if it " +
                    "is specified."}, usage = "<mine name>", permissions = {"mineresetlite.mine.tp"},
            min = 1, max = 2)
    public void teleport(CommandSender sender, String[] args)
    {
        Mine mine = null;
        Player player;

        for(Mine aMine : plugin.mines)
        {
            if(aMine.getName().equalsIgnoreCase(args[0]))
                mine = aMine;
        }

        if(mine == null)
        {
            sender.sendMessage(phrase("noMinesMatched"));
            return;
        }

        // Add handling for console
        if(!(sender instanceof Player))
        {
            if(!(args.length > 1))
            {
                sender.sendMessage(phrase("notAPlayer"));
                return;
            }

            player = Bukkit.getPlayer(args[1]);
        }
        else
            player = (Player) sender;

        if(player == null)
        {
            sender.sendMessage(phrase("playerNotFound"));
            return;
        }

        mine.teleport(player);
    }

    @Command(aliases = {"settp", "stp"}, description = "Sets the specified mine's spawn point",
            help = {"This command will set the specified mine's reset spawn point to where you're standing.",
                    "Use /mrl removetp <mine name> to remove the mine's spawn point."}, usage = "<mine name>",
            permissions = {"mineresetlite.mine.settp"}, min = 1, onlyPlayers = true)
    public void setTP(CommandSender sender, String[] args)
    {
        Player player = (Player) sender;
        Mine[] mines = plugin.matchMines(StringTools.buildSpacedArgument(args));
        if(invalidMines(sender, mines))
            return;

        mines[0].setTp(player.getLocation());
        plugin.buffSave();
        sender.sendMessage(phrase("tpSet", mines[0]));
    }

    @Command(aliases = {"removetp", "rtp"}, description = "Removes the specified mine's spawn point",
            help = {"This comamnd will remove the specified mine's reset spawn point.",
                    "Use /mrl removetp to remove the spawn point.",
                    "use /mrl settp to set it to where you're standing."}, usage = "<mine name>",
            permissions = {"mineresetlite.mine.removetp"}, min = 1, onlyPlayers = true)
    public void removeTP(CommandSender sender, String[] args)
    {
        Player player = (Player) sender;
        Mine[] mines = plugin.matchMines(StringTools.buildSpacedArgument(args));
        if(invalidMines(sender, mines))
            return;

        mines[0].setTp(new Location(player.getWorld(), 0, -Integer.MAX_VALUE, 0));
        plugin.buffSave();
        sender.sendMessage(phrase("tpRemove", mines[0]));
    }

    @SuppressWarnings("DuplicatedCode")
    @Command(aliases = {"redefine", "rd"}, description = "Redefine a mine's boundaries from either your WorldEdit " +
            "selection or by manually specifying the points", help = {"Provided you have a selection made via either " +
            "WorldEdit or selecting the points using MRL,"}, usage = "<mine name>",
            permissions = {"mineresetlite.mine.redefine"}, min = 1, onlyPlayers = true)
    public void redefineMine(CommandSender sender, String[] args)
    {
        Player player = (Player) sender;
        MineSelection selection = getMineSelection(player);
        if(selection == null)
            return;

        World world = selection.getWorld();
        Vector p1 = selection.getP1();
        Vector p2 = selection.getP2();

        //Construct mine name
        String name = StringTools.buildSpacedArgument(args);

        //Verify uniqueness of mine name
        Mine[] mines = plugin.matchMines(name);
        if(mines.length < 1)
        {
            player.sendMessage(phrase("noMinesMatched", name));
            return;
        }

        //Sort coordinates
        prepareCoordinates(p1, p2);

        // Make the change
        mines[0].redefine(p1.getBlockX(), p1.getBlockY(), p1.getBlockZ(), p2.getBlockX(), p2.getBlockY(),
                p2.getBlockZ(), world);
        player.sendMessage(Phrases.phrase("mineRedefined"));
        plugin.buffSave();
    }

    @Command(aliases = {"setstruct", "addstruct", "+"}, description = "Set the structure block for the mine",
            help = {"This command will add the specified block as the structure material types,",
                    "and it will not be replaced by the mine blocks."}, usage = "<mine name> <block>",
            permissions = {"mineresetlite.mine.composition"}, min = 2)
    public void setStructure(CommandSender sender, String[] args)
    {
        Mine[] mines = plugin.matchMines(StringTools.buildSpacedArgument(args, 2));
        if(invalidMines(sender, mines))
            return;

        //Match material
        Material m = matchMaterial(sender, args[args.length - 2]);
        if(m == null)
            return;

        SerializableBlock block = new SerializableBlock(m);
        mines[0].getStructure().add(block);
        sender.sendMessage(phrase("mineStructureSet", mines[0], block));
        plugin.buffSave();
    }

    @Command(aliases = {"unsetstruct", "removestruct", "-"}, description = "Remove a block from the strucutre material list.",
            usage = "<mine name> <block>", permissions = {"mineresetlite.mine.composition"}, min = 2)
    public void unsetStructureComposition(CommandSender sender, String[] args)
    {
        Mine[] mines = plugin.matchMines(StringTools.buildSpacedArgument(args, 1));
        if(invalidMines(sender, mines))
            return;

        //Match material
        Material m = matchMaterial(sender, args[args.length - 1]);
        if(m == null)
            return;

        //Does the mine contain this block?
        SerializableBlock block = new SerializableBlock(m);

        for(SerializableBlock entry : mines[0].getStructure())
        {
            if(entry.equals(block))
            {
                mines[0].getStructure().remove(entry);
                sender.sendMessage(phrase("blockRemovedFromStructure", mines[0], block));
                return;
            }
        }

        sender.sendMessage(phrase("blockNotInMine", mines[0], block));
        plugin.buffSave();
    }

    @Command(aliases = {"setlucky"}, description = "Sets the number of lucky blocks in the specified mine.",
            help = {"This command will set the number of lucky blocks in the specified mine."},
            usage = "<mine name> <a number of lucky blocks>", permissions = {"mineresetlite.mine.setlucky"},
            min = 1, onlyPlayers = true)
    public void setLucky(CommandSender sender, String[] args) /*throws InvalidCommandArgumentsException*/
    {
        sender.sendMessage("Disabled feature");

        /*Mine[] mines = plugin.matchMines(StringTools.buildSpacedArgument(args, 1));
        if(invalidMines(sender, mines))
            return;

        int num;
        try
        {
            num = Integer.parseInt(args[args.length - 1]);
            if(num > 0)
            {
                mines[0].setLuckyBlockNum(num);
                plugin.buffSave();
                sender.sendMessage(phrase("luckyBlocksSet", mines[0], args[args.length - 1]));
            }
        }
        catch(Throwable ignore)
        {
            //Args weren't empty or -feet, bad args
            throw new InvalidCommandArgumentsException();
        }*/
    }

    @Command(aliases = {"makelucky"}, description = "Makes the n-th mined block as a lucky block in the specified mine.",
            help = {"This command will make the n-th mined block in the specified mine as a lucky block."},
            usage = "<mine name> <a_number_of_lucky_block>", permissions = {"mineresetlite.mine.makelucky"},
            min = 1, onlyPlayers = true)
    public void makeLucky(CommandSender sender, String[] args) /*throws InvalidCommandArgumentsException*/
    {
        sender.sendMessage("Disabled feature");

        /*Mine[] mines = plugin.matchMines(StringTools.buildSpacedArgument(args, 1));
        if(invalidMines(sender, mines))
            return;

        int num;
        try
        {
            num = Integer.parseInt(args[args.length - 1]);
            if(num > 0)
            {
                mines[0].makeLucky(num);
            }
        }
        catch(Throwable ignore)
        {
            //Args weren't empty or -feet, bad args
            throw new InvalidCommandArgumentsException();
        }*/
    }

    private double parsePercentage(CommandSender sender, String arg)
    {
        StringBuilder psb = new StringBuilder(arg);
        psb.deleteCharAt(psb.length() - 1);

        try {return Double.parseDouble(psb.toString());}
        catch(NumberFormatException nfe)
        {
            sender.sendMessage(phrase("badPercentage"));
            return 0.0;
        }
    }

    private Material matchMaterial(CommandSender sender, String arg)
    {
        Material m = plugin.matchMaterial(arg);
        if(m == null)
        {
            sender.sendMessage(phrase("unknownBlock"));
            return null;
        }

        if(!(m.isBlock()))
        {
            sender.sendMessage(phrase("notABlock"));
            return null;
        }

        return m;
    }

    private MineSelection getMineSelection(Player player)
    {
        WorldEditPlugin worldEdit = plugin.getWorldEdit();
        Region selection;

        try
        {selection = worldEdit.getSession(player).getSelection(BukkitAdapter.adapt(player.getWorld()));}
        catch(IncompleteRegionException ignored)
        {
            player.sendMessage(phrase("emptySelection"));
            return null;
        }

        World world = BukkitAdapter.adapt(Objects.requireNonNull(selection.getWorld()));
        Vector p1 = BukkitAdapter.adapt(world, selection.getMinimumPoint()).toVector();
        Vector p2 = BukkitAdapter.adapt(world, selection.getMaximumPoint()).toVector();
        return new MineSelection(world, p1, p2);
    }

    private void prepareCoordinates(Vector p1, Vector p2)
    {
        if(p1.getX() > p2.getX())
        {
            //Swap
            double x = p1.getX();
            p1.setX(p2.getX());
            p2.setX(x);
        }

        if(p1.getY() > p2.getY())
        {
            double y = p1.getY();
            p1.setY(p2.getY());
            p2.setY(y);
        }

        if(p1.getZ() > p2.getZ())
        {
            double z = p1.getZ();
            p1.setZ(p2.getZ());
            p2.setZ(z);
        }
    }

    // Removed in favor of WorldEdit selections
    /*@Command(aliases = {"pos1", "p1"}, description = "Change your first selection point",
            help = {"Run this command to set your first selection point to the block you are looking at.",
                    "Use /mrl pos1 -feet to set your first point to the location you are standing on."},
            usage = "(-feet)", permissions = {"mineresetlite.mine.create", "mineresetlite.mine.redefine"},
            max = 1, onlyPlayers = true)
    public void setPoint1(CommandSender sender, String[] args) throws InvalidCommandArgumentsException
    {
        Player player = (Player) sender;
        if(args.length == 0)
        {
            //Use block being looked at
            point1.put(player, player.getTargetBlock(null, 100).getLocation());
            player.sendMessage(phrase("firstPointSet"));
            return;
        }
        else if(args[0].equalsIgnoreCase("-feet"))
        {
            //Use block being stood on
            point1.put(player, player.getLocation());
            player.sendMessage(phrase("firstPointSet"));
            return;
        }

        //Args weren't empty or -feet, bad args
        throw new InvalidCommandArgumentsException();
    }

    @Command(aliases = {"pos2", "p2"}, description = "Change your first selection point",
            help = {"Run this command to set your second selection point to the block you are looking at.",
                    "Use /mrl pos2 -feet to set your second point to the location you are standing on."},
            usage = "(-feet)", permissions = {"mineresetlite.mine.create", "mineresetlite.mine.redefine"},
            max = 1, onlyPlayers = true)
    public void setPoint2(CommandSender sender, String[] args) throws InvalidCommandArgumentsException
    {
        Player player = (Player) sender;
        if(args.length == 0)
        {
            //Use block being looked at
            point2.put(player, player.getTargetBlock(null, 100).getLocation());
            player.sendMessage(phrase("secondPointSet"));
            return;
        }
        else if(args[0].equalsIgnoreCase("-feet"))
        {
            //Use block being stood on
            point2.put(player, player.getLocation());
            player.sendMessage(phrase("secondPointSet"));
            return;
        }

        //Args weren't empty or -feet, bad args
        throw new InvalidCommandArgumentsException();
    }*/
}
