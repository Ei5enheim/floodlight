/*
 * Author: Rajesh Gopidi
 * Grad student at UNC Chapel Hill
 */

package net.floodlightcontroller.topovalidation.internal;

import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.topology.NodePortTuple;

public class NodePortTuplePlusPkt
{
	protected IPacket pkt;
	protected NodePortTuple swPort;

    public NodePortTuplePlusPkt ()
    {
		
    }

	public NodePortTuplePlusPkt (NodePortTuple swPort, IPacket pkt)
	{
		this.pkt = pkt;
		this.swPort = swPort;
	}

	public NodePortTuple getSwPort()
	{
		return this.swPort;
	}

	public IPacket getPacket()
	{
		return this.pkt;
	}

	public int hashCode()
	{
		final int prime = 5557;
		int result = 1;
		//System.out.println("Calling hashCode from NPTPP");
		int swHash = swPort.hashCode();
		int pktHash =  pkt.hashCode();
		//System.out.println("switch Port hashcode:" + swHash);
		//System.out.println("pkt Hash code: " + pktHash);
		result = prime * result + swHash;
		result = prime * result + pktHash;
		return result;
	}

	public boolean equals(Object obj)
	{
		if (obj == null)
			return false;

		if (this == obj)
			return true;

		if (!(obj instanceof NodePortTuplePlusPkt))
			return false;

		NodePortTuplePlusPkt clone = (NodePortTuplePlusPkt) obj;

		if (!clone.getSwPort().equals(this.swPort))
			return false;

		if (!clone.getPacket().equals(this.pkt))
			return false;

		return true;
	}
}
