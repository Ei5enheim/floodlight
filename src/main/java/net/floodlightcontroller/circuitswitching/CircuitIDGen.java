/*
 * Author: Rajesh Gopidi
 * Grad student at UNC Chapel Hill
 */

package net.floodlightcontroller.circuitswitching;

public class CircuitIDGen
{
    protected long dpID;
	protected int startBit;
	protected int endBit;
    protected Object lock;
    protected long circuitCount;

    public CircuitIDGen()
    {
        dpID = -1;
        lock = new Object();
    }

    public CircuitIDGen(long dpID)
    {
        this.dpID = dpID;
        lock = new Object();
    }

    public long getDpID()
    {
        return (dpID);
    }

    public Object getLock()
    {
        return (lock);
    }

    public long getCircuitCount()
    {
        return (circuitCount);
    }

    public long getCircuitID()
    {
        synchronized (lock) {
            circuitCount++;
        } 
        return (circuitCount);
    }
}

