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
    protected Map <Long, String> domainMapper;
    protected Map <NodePortTuple, IOFFlowspace[]>  flowspace;
    protected Map <Link, Rules> ruleTransTables;
    protected Set<Link> links;
	protected Set<Long> switches;
	protected DelegatedMAC mac;

    public GraphDBRequest ()
    {

    }

    public GraphDBRequest (Map <Long, String> domainMapper,
                            Map <NodePortTuple, IOFFlowspace[]>  flowspace,
                            Map <Link, Rules> ruleTransTables,
                            Set<Link> links,
							Set<Long> switches,
							DelegatedMAC mac)
    {
        this();
        this.domainMapper = domainMapper;
        this.flowspace = flowspace;
        this.ruleTransTables = ruleTransTables;
        this.links = links;
		this.switches = switches;
		this.mac = mac;
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

    public Set<Link> getLinks()
    {
        return this.links;    
    }

	public Set<Long> getSwitches()
	{
		return this.switches;
	}

    public void setDomainMapper (Map <Long, String> domainMapper)
    {
        this.domainMapper = domainMapper;
    }

    public void setDomainFlowspace (Map <NodePortTuple, IOFFlowspace[]> flowspace)
    {
        this.flowspace = flowspace;

    }

    public void setRuleTransTables(Map <Link, Rules> table)
    {
        this.ruleTransTables = table;
    }

    public void setLinks(Set<Link> links)
    {
        this.links = links;    
    }

	public void setSwitches(Set<Long> switches)
	{
		this.switches = switches;
	}

	public void setDelegatedMAC(DelegatedMAC mac)
	{
		this.mac = mac;
	}

	public DelegatedMAC getDelegatedMAC() 
	{
		return this.mac;
	}
}
