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
import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.topology.NodePortTuple;

import org.renci.doe.pharos.flow.Rules;

public interface IGraphDBRequest
{
    public Map <Long, String> getDomainMapper ();
    public Map <NodePortTuple, IOFFlowspace[]> getDomainFlowspace ();
    public Map <Link, Rules> getRuleTransTables();
    public List<Link> getLinks();
	public Set<Long> getSwitches();
}
