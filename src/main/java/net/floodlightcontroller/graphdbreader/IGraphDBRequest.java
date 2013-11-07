/**
 *
 * Author: Rajesh Gopidi
 *
 */

package net.floodlightcontroller.graphdbreader;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

import net.floodlightcontroller.graphdbreader.IGraphDBRequest;
import net.floodlightcontroller.topology.IOFFlowspace;
import net.floodlightcontroller.topology.NodePortTuple;
import net.floodlightcontroller.util.DelegatedMAC;

import org.renci.doe.pharos.flow.Rules;

public interface IGraphDBRequest
{
	/*
    public Map <NodePortTuple, IOFFlowspace[]> getDomainFlowspace ();
	public Set<Long> getSwitches();
    public void setDomainFlowspace (Map <NodePortTuple, IOFFlowspace[]> flowspace);
	public void setSwitches(Set<Long> switches);	
	*/
	public List<DelegatedMAC> getBlockedSrcMACList();
	public void setBlockedSrcMACList (List<DelegatedMAC> list);
}
