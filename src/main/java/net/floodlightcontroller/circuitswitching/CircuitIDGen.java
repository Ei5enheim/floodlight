/*
 * Author: Rajesh Gopidi
 * Grad student at UNC Chapel Hill
 */

package net.floodlightcontroller.circuitswitching;

public class CircuitIDGen
{
	protected int startBit;
	protected int endBit;
    protected Object lock;
    protected long circuitCount;

    public CircuitIDGen()
    {
        lock = new Object();
        startBit = 0;
        endBit = 39;
    }

	public CircuitIDGen(int start, int end)
    {
		this.startBit = start;
		this.endBit = end;
        lock = new Object();
    }

	public CircuitIDGen setStartBit (int startBit)
	{
		this.startBit = startBit;
		return this;
	}

	public CircuitIDGen setEndBit (int endBit)
	{
		this.endBit = endBit;
		return this;
	}

    public Object getLock()
    {
        return (lock);
    }

    public long setCircuitID(long count)
    {
        synchronized (lock) {
            this.circuitCount = count;
        }
        return this.circuitCount;
    }

    public long getCircuitID() throws Exception
    {
		long count = 0;
        synchronized (lock) {
            circuitCount++;
			count = circuitCount;
			if (circuitCount >= (1L << (endBit-startBit + 1)))
				throw new Exception ("Ran out of available MAC addresses");
        } 
        return (count);
    }
}

