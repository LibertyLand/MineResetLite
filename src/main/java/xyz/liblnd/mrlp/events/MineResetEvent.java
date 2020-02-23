package xyz.liblnd.mrlp.events;

import com.koletar.jj.mineresetlite.Mine;

/**
 * Event fired when a Mine is reset
 * See {@link Cause} for a list of possible causes
 *
 * @author Artuto
 * @since 4.4.0-LL
 */

public class MineResetEvent extends MineEvent
{
    private final Cause cause;

    public MineResetEvent(Mine mine, Cause cause)
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
        COMMAND, PERCENTAGE, SERVER_START, AUTOMATIC
    }
}
