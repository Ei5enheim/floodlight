/**
 *
 *  Author: Rajesh Gopidi
 */

package net.floodlightcontroller.graphdbreader;

public class GraphDBException extends Exception 
{

    private String message = null;

    public GraphDBException ()
    {
        super();
    }

    public GraphDBException (String msg)
    {
        this();
        message = msg;
    }

    public String toString()
    {
        return (message);
    }

    public String getMessage()
    {
        return (message);
    }
}
