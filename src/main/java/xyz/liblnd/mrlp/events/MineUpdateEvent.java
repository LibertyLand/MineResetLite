package xyz.liblnd.mrlp.events;

import com.koletar.jj.mineresetlite.Mine;

/**
 * Event fired when a mine is updated
 * See {@link Cause} for a list of possible causes
 *
 * @author Artuto
 * @since 4.4.0-LL
 */

public class MineUpdateEvent extends MineEvent
{
    private final Cause cause;

    public MineUpdateEvent(Mine mine, Cause cause)
    {
        super(mine);
        this.cause = cause;
    }

    public Cause getCause()
    {
        return cause;
    }

    public enum Cause
    {
        BROKEN_BLOCK
    }
}
