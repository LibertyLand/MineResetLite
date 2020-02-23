package xyz.liblnd.mrlp.events;

import com.koletar.jj.mineresetlite.Mine;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Parent event for all the MineResetLite events
 * @author Artuto
 * @since 4.4.0-LL
 */

public class MineEvent extends Event
{
    private final Mine mine;

    private final static HandlerList handlers = new HandlerList();

    MineEvent(Mine mine)
    {
        this.mine = mine;
    }

    /**
     * Gets the mine this even is about
     * @return the affected mine
     */
    public Mine getMine()
    {
        return mine;
    }

    public static HandlerList getHandlerList()
    {
        return handlers;
    }

    @Override
    public HandlerList getHandlers()
    {
        return handlers;
    }
}
