/**
 *
 * Author: Rajesh Gopidi
 *
 */

package net.floodlightcontroller.graphdbreader.internal;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

import net.floodlightcontroller.graphdbreader.IGraphDBRequest;
import net.floodlightcontroller.topology.IOFFlowspace;
import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.topology.NodePortTuple;
import net.floodlightcontroller.util.DelegatedMAC;

import org.renci.doe.pharos.flow.Rules;

public class GraphDBRequest implements IGraphDBRequest
{
    /*protected Map <NodePortTuple, IOFFlowspace[]>  flowspace;
	protected Set<Long> switches;*/
	
	List<DelegatedMAC> blockedList;

    public GraphDBRequest ()
    {

    }

    public GraphDBRequest (List<DelegatedMAC> blockedList)
    {
        this();
        this.blockedList = blockedList;
    }

	public List<DelegatedMAC> getBlockedSrcMACList()
	{
		return this.blockedList;
	}

	public void setBlockedSrcMACList (List<DelegatedMAC> list)
	{
		this.blockedList = list;
	}
	/*
    public Map <NodePortTuple, IOFFlowspace[]> getDomainFlowspace ()
    {
        return this.flowspace;

    }

	public Set<Long> getSwitches()
	{
		return this.switches;
	}

    public void setDomainFlowspace (Map <NodePortTuple, IOFFlowspace[]> flowspace)
    {
        this.flowspace = flowspace;

    }

	public void setSwitches(Set<Long> switches)
	{
		this.switches = switches;
	}*/

}
