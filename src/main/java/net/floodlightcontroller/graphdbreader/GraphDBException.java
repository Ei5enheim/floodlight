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
        super();
        message = msg;
    }

    public String toString()
    {
        return (message);
    }

    public String getMsg()
    {
        return (message);
    }
}
