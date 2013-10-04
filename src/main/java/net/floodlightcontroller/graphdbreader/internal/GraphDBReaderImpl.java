/**
 *
 * Author: Rajesh Gopidi
 *
 */

package net.floodlightcontroller.graphdbreader;

import net.floodlightcontroller.graphdbreader.IGraphDBReaderService;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.topovalidation.ITopoValidationService;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.topology.IOFFlowspace;
import net.floodlightcontroller.topology.OFFlowspace;
import net.floodlightcontroller.topology.FlowspaceException;
import net.floodlightcontroller.topology.NodePortTuple;
import net.floodlightcontroller.routing.Link;

import org.renci.doe.pharos.flow.Rules;
import org.renci.doe.pharos.flow.Rule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashSet;
import java.io.File;
import java.io.FileInputStream;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.FileNotFoundException;

import com.tinkerpop.blueprints.Graph;
import com.tinkerpop.blueprints.impls.dex.DexGraph;
import com.tinkerpop.blueprints.util.io.graphml.GraphMLReader;
import com.tinkerpop.blueprints.util.io.graphml.GraphMLWriter;
import com.tinkerpop.gremlin.java.GremlinPipeline;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.pipes.PipeFunction;
import com.tinkerpop.blueprints.impls.tg.TinkerGraph;
import com.tinkerpop.pipes.filter.*;
import com.tinkerpop.blueprints.Contains;
import com.tinkerpop.blueprints.Direction;


public class GraphDBReaderImpl implements IGraphDBReaderService,
                                            IFloodlightModule
{
    // IFloodlightModule routines
    public Collection<Class<? extends IFloodlightService>> getModuleServices()
    {
        return null;
    }

    public Map<Class<? extends IFloodlightService>,
               IFloodlightService> getServiceImpls()

    {
        return null;
    }

    public Collection<Class<? extends IFloodlightService>> getModuleDependencies()
    {
        Collection<Class <? extends IFloodlightService>> collection =
                                    new ArrayList<Class<? extends IFloodlightService>>();
        collection.add(IFloodlightProviderService.class);
        collection.add(ITopoValidationService.class);
        collection.add(ILinkDiscoveryService.class);

        return (collection);
    }

    public void init(FloodlightModuleContext context) throws FloodlightModuleException
    {
        floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        logger = LoggerFactory.getLogger(GraphDBReaderImpl.class);
        linkManager = context.getServiceImpl(ILinkDiscoveryService.class);
        topoValidator = context.getServiceImpl(ITopoValidationService.class);

        domainMapper = new HashMap <Long, String> ();
        flowspace = new ConcurrentHashMap <NodePortTuple, IOFFlowspace[]>();
        ruleTransTables = new ConcurrentHashMap <Link, Rules> ();
    }

    public void startUp(FloodlightModuleContext context)
    {
        logger.info("\n ****Starting the Graphdb reader module****\n");
    }

    // internal utility methods
    public void readGraph ()
    {
        HashSet <Long> nodes = new HashSet <Long> ();
        HashSet <Vertex> vertices = new HashSet <Vertex>();
        String fileName = "./db/doe_pharos_domain_A.dex";
        File file = new File(fileName);
        List pruneList = new ArrayList<Edge>();
        BufferedInputStream stream = null;

        try {
            stream = new BufferedInputStream(
                                        new FileInputStream("doe_pharos_domain_A.xml"));
        } catch (FileNotFoundException fnf) {
    
        }

        String localDomain = null;
        flowspace = new ConcurrentHashMap <NodePortTuple, IOFFlowspace[]>();
        ruleTransTables = new ConcurrentHashMap <Link, Rules>();
        links = new ArrayList<Link>();
        domainMapper = new HashMap<Long, String>();

        if (file.exists())
            file.delete();

        Graph graph = new DexGraph(fileName);
        try {
            GraphMLReader.inputGraph(graph, stream);
        } catch (IOException io) {

        }
        for (Edge e: graph.getEdges()) {
            try {
                processEdge(e, vertices, nodes, pruneList, localDomain);
            } catch (FlowspaceException t) {
    
            }
        }

        if (!pruneList.isEmpty()) {
            Edge[] array = new Edge[pruneList.size()];
            for (Edge e: array) {
                try {
                    processEdge(e, vertices, nodes, pruneList, localDomain);
                } catch (FlowspaceException t) {
                
                }
            }
        }
        linkManager.blockLinkDiscovery();
        floodlightProvider.addFlowspace(flowspace);
        floodlightProvider.addRuleTransTables(ruleTransTables);
        floodlightProvider.addDomainMapper(domainMapper);
        topoValidator.validateTopology(links, ruleTransTables, false);
        //Invoke the topovalidator here using the topolock here.
        // Need to deal with the combination part and leaving out the
        // single node from a seperate domain and the lock part.
        linkManager.enableLinkDiscovery();
        linkManager.addLinks((Link[]) links.toArray());

    }

    private void processEdge (Edge e, HashSet <Vertex> vertices,
                              HashSet <Long> nodes,
                              List<Edge> pruneList,
                              String localDomain) throws FlowspaceException
    {
        long dstDpid = 0;
        short dstPort = 0;
        Vertex node = null, outNode = null;
        boolean isPhysEdge = false;
        NodePortTuple switchPort = null;
        Link link = null;

        node = e.getVertex(Direction.IN);
        outNode = e.getVertex(Direction.OUT);

        if (!node.getProperty("domain").equals(outNode.getProperty("domain"))){
            if (localDomain == null) {
                pruneList.add(e);
                return;
            }
        } else {
            localDomain = node.getProperty("domain");
        }

        isPhysEdge = e.getLabel().equals(wantedLabel);

        if (!vertices.contains(node) &&
            node.getProperty("domain").equals(localDomain)) {
            Long dpid = Long.valueOf((String)node.getProperty("DPID"));
            vertices.add(node);
            if (!nodes.contains(dpid)) {
                nodes.add(dpid);
                System.out.println("Vertex is " + node.getProperty("DPID") + ", " + node.getId());
                domainMapper.put(dpid, (String)node.getProperty("domain"));
            }
            switchPort = new NodePortTuple(dpid,
                                    Short.valueOf((String)node.getProperty("Port")));
            if (flowspace.get(switchPort) == null)
                flowspace.put(switchPort, new IOFFlowspace[2]);
            if (isPhysEdge) {
                dstDpid = dpid;
                dstPort = switchPort.getPortId();
                System.out.println("Domain: "+node.getProperty("domain")+" Node: "+ node.getProperty("DPID")+" Port: "+ node.getProperty("Port")+"[Ingress] Flowspace: "+node.getProperty("Flowspace"));
                flowspace.get(switchPort)[INGRESS] = OFFlowspace.parseFlowspaceString(
                                                      (String)node.getProperty("Flowspace"));
            } else {
                System.out.println("Domain: "+node.getProperty("domain")+" Node: "+ node.getProperty("DPID")+" Port: "+ node.getProperty("Port")+"[Egress] Flowspace: "+node.getProperty("Flowspace"));
                flowspace.get(switchPort)[EGRESS] = OFFlowspace.parseFlowspaceString(
                                                              (String)node.getProperty("Flowspace"));
            }
        }
        node = outNode;
        if (!vertices.contains(node)&&
            node.getProperty("domain").equals(localDomain)) {
            Long dpid = Long.valueOf((String)node.getProperty("DPID"));
            vertices.add(node);
            if (!nodes.contains(dpid)) {
                nodes.add(dpid);
                domainMapper.put(dpid, (String)node.getProperty("domain"));
                System.out.println("Vertex is " + node.getProperty("DPID") + ", " + node.getId());
            }
            switchPort = new NodePortTuple(dpid,
                            Short.valueOf((String)node.getProperty("Port")));
            if (flowspace.get(switchPort) == null)
                flowspace.put(switchPort, new IOFFlowspace[2]);
            if (isPhysEdge) {
                link = new Link (dpid, switchPort.getPortId(),
                                dstDpid, dstPort);
                links.add(link);
                flowspace.get(switchPort)[EGRESS] = OFFlowspace.parseFlowspaceString(
                                                     (String)node.getProperty("Flowspace"));
                ruleTransTables.put(link, new Rules((String)e.getProperty("Rules")));
                System.out.println("Domain: "+node.getProperty("domain")+" Node: "+ node.getProperty("DPID")+" Port: "+ node.getProperty("Port")+"[Egress] Flowspace: "+node.getProperty("Flowspace"));
            } else {
                flowspace.get(switchPort)[INGRESS] = OFFlowspace.parseFlowspaceString(
                                                           (String)node.getProperty("Flowspace"));;
                System.out.println("Domain: "+node.getProperty("domain")+" Node: "+ node.getProperty("DPID")+" Port: "+ node.getProperty("Port")+"[Ingress] Flowspace: "+node.getProperty("Flowspace"));
            }
        }
    }

    protected IFloodlightProviderService floodlightProvider;
    protected ILinkDiscoveryService linkManager;
    protected ITopoValidationService topoValidator;
    protected static Logger logger;
    protected HashMap <Long, String> domainMapper;
    protected ConcurrentMap <NodePortTuple, IOFFlowspace[]>  flowspace;
    protected ConcurrentMap <Link, Rules> ruleTransTables;
	protected List<Link> links;
    private String wantedLabel = "Is connected to";
    private int FLOWSPACE_SIZE = 2, INGRESS = 0, EGRESS = 1;
}
