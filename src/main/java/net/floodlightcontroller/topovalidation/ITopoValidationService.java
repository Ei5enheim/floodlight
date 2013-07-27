/*
 * Author: Rajesh Gopidi
 * Grad student at UNC Chapel Hill
 */

package net.floodlightcontroller.topovalidation;

import java.util.List;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.topology.NodePortTuple;
import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.topovalidation.IOFFlowspace;

public interface ITopoValidationService extends IFloodlightService
{
    public boolean validateLinkFlowspace (Link link, IOFFlowspace flowspace,
					 boolean completeFlowspace);

    public boolean validateTopology (List<Link> links, List<> 
}
