package xyz.liblnd.mrlp.events;

import com.koletar.jj.mineresetlite.Mine;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class MineUpdateEvent extends Event
{
    private final Mine mine;

    private final static HandlerList handlers = new HandlerList();

    public MineUpdateEvent(Mine mine)
    {
        this.mine = mine;
    }

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
