/*
 * Author: Rajesh Gopidi
 * Grad student at UNC Chapel Hill
 */

package net.floodlightcontroller.circuitswitching;

public class CircuitIDGen
{
    protected long baseAddress;
	protected int startBit;
	protected int endBit;
    protected Object lock;
    protected long circuitCount;

    public CircuitIDGen()
    {
        baseAddress = -1L;
        lock = new Object();
    }

    public CircuitIDGen(long baseAddress)
    {
        this.baseAddress = baseAddress;
        lock = new Object();
    }

	public CircuitIDGen(long baseAddress, int start, int end)
    {
        this.baseAddress = baseAddress;
		this.startBit = start;
		this.endBit = end;
        lock = new Object();
    }


    public long getBaseAddress()
    {
        return (baseAddress);
    }

	public CircuitIDGen updateBaseAddress(long baseAddress)
	{
		synchronized (lock) {
			this.baseAddress = baseAddress;
			circuitCount = 0;
		}
		return this;
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

    public long getCircuitCount()
    {
        return (circuitCount);
    }

    public long getCircuitID()
    {
		long circuitID = 0L;
		long count = 0;
        synchronized (lock) {
            circuitCount++;
			count = circuitCount;
			if (circuitCount >= (1L << (endBit-startBit + 1)));
				//throw an Exception
        } 
		circuitID = (baseAddress & (~((0x1L << endBit+1) - 0x1L)
									| ((0x1L << startBit) - 0x1L))) | count;

        return (circuitID);
    }
}

