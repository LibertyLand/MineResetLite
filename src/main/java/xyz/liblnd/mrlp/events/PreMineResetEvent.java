package xyz.liblnd.mrlp.events;

import com.koletar.jj.mineresetlite.Mine;
import org.bukkit.event.Cancellable;

/**
 * Event fired before a Mine is reset
 *
 * @author Artuto
 * @since 4.4.0-LL
 */

public class PreMineResetEvent extends MineEvent implements Cancellable
{
    private boolean cancelled = false;

    public PreMineResetEvent(Mine mine)
    {
        super(mine);
    }

    @Override
    public boolean isCancelled()
    {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel)
    {
        this.cancelled = cancel;
    }
}
