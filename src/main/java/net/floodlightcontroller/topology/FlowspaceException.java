/**
 *
 *  Author: Rajesh Gopidi
 */

package net.floodlightcontroller.topology;

public class FlowspaceException extrends Exception 
{

    private String message = null;

    public FlowspaceException()
    {
        super();
    }

    public FlowspaceException (String msg)
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
