/*
 * Author: Rajesh Gopidi
 * Grad student at UNC Chapel Hill
 */

package net.floodlightcontroller.topovalidation;

import java.util.List;
import java.util.HashMap;
import java.util.Map;


import org.renci.doe.pharos.flow.Rules;
import org.renci.doe.pharos.flow.Rule;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.topology.NodePortTuple;
import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.topology.IOFFlowspace;

public interface ITopoValidationService extends IFloodlightService
{
    public boolean validateLink (Link link,
                                 Rules ruleTransTable,
				 boolean completeFlowspace);

    public boolean validatePath (List<NodePortTuple> path,
                                 Map<Link, Rules> ruleTransTables, 
                                 boolean completeFlowspace);

    /*
    public boolean validateTopology (List<NodePortTuple> switchPorts,
                                     Map<Link, Rules> ruleTransTables,
                                     boolean completeFlowspace);
    */

    public boolean validateTopology (List<Link> links,
				     Map<Link, Rules> ruleTransTables,
                                     boolean completeFlowspace); 
}
