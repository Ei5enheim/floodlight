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

import org.renci.doe.pharos.flow.Rules;

public class GraphDBRequest implements IGraphDBRequest
{
    protected Map <Long, String> domainMapper;
    protected Map <NodePortTuple, IOFFlowspace[]>  flowspace;
    protected Map <Link, Rules> ruleTransTables;
    protected List<Link> links;
	protected Set<Long> switches;

    public GraphDBRequest ()
    {

    }

    public GraphDBRequest (Map <Long, String> domainMapper,
                            Map <NodePortTuple, IOFFlowspace[]>  flowspace,
                            Map <Link, Rules> ruleTransTables,
                            List<Link> links,
							Set<Long> switches)
    {
        super();
        this.domainMapper = domainMapper;
        this.flowspace = flowspace;
        this.ruleTransTables = ruleTransTables;
        this.links = links;
		this.switches = switches;
    }

    public Map <Long, String> getDomainMapper ()
    {
        return this.domainMapper;
    }

    public Map <NodePortTuple, IOFFlowspace[]> getDomainFlowspace ()
    {
        return this.flowspace;

    }

    public Map <Link, Rules> getRuleTransTables()
    {
        return this.ruleTransTables;
    }

    public List<Link> getLinks()
    {
        return this.links;    
    }

	public Set<Long> getSwitches()
	{
		return this.switches;
	}
}
