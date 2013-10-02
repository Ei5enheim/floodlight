/*
 * Author: Rajesh Gopidi
 * Grad student at UNC Chapel Hill
 */

import java.util.LinkedList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.io.IOException;


import org.flowvisor.api.FlowChange;
import org.flowvisor.api.FlowChange.FlowChangeOp;
import org.flowvisor.exceptions.MalformedFlowChange;

import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;

public class FlowvisorInterface extends FlowvisorDevice 
{

    private static final String PRIORITY_REGEX = "\\d+";
    private static final String PORTS_REGEX = "any|(\\s*\\d+\\s*|\\s*\\d+\\s*-\\s*\\d+\\s*)\\s*(\\s*,\\s*(\\d+|\\d+\\s*-\\s*\\d+)\\s*)*";
    private static final String DPID_REGEX = "([0-9a-fA-F][0-9a-fA-F])(:[0-9a-fA-F][0-9a-fA-F]){7}|all";

    public FlowvisorInterface ()
    {
        super("https://localhost:8080/xmlrpc", null, null);
    }

    public FlowvisorInterface (String url, String uid, String passwd)
    {
        super(url, uid, passwd);
    }

    public FlowvisorInterface (String url)
    {
        super(url, null, null);
    }
      
    public void setConfig (String name, String value) throws XmlRpcException
    {
        Object reply = this.client.execute("api.setConfig", new Object[] {
                                            name, value });
        if (reply == null) {
            System.err.println("Got 'null' as reply :-(");
            System.exit(-1);
        }
        if (!(reply instanceof Boolean)) {
            System.err.println("Didn't get boolean reply?; got" + reply);
            System.exit(-1);
        }
        boolean success = ((Boolean) reply).booleanValue();
        if (success) {
            System.out.println("success");
            System.exit(0);
        } else {
            System.out.println("failure");
            System.exit(-1);
        }
    }

    public void createSlice (String sliceName, String controller_url,
                                String passwd, String slice_email) throws
                                                IOException,
                                                XmlRpcException
    {
        Boolean reply = (Boolean) this.client
                                        .execute("api.createSlice", new Object[] {
                                                sliceName, passwd,
                                                controller_url, slice_email });
        if (reply == null) {
            System.err.println("Got 'null' as reply :-(");
            System.exit(-1);
        }
        if (reply)
            System.out.println("success!");
        else
            System.err.println("failed!");
    }

    public void createSlice (String sliceName, String passwd, String controller_url,
                            String drop_policy, String slice_email) throws
                                                        IOException,
                                                        XmlRpcException
    {
        Boolean reply = (Boolean) this.client
                                        .execute("api.createSlice",
                                                    new Object[] {sliceName,
                                                                  passwd,
                                                                  controller_url,
                                                                  drop_policy,
                                                                  slice_email});
        if (reply == null) {
            System.err.println("Got 'null' as reply :-(");
            System.exit(-1);
        }
        if (reply)
            System.out.println("success!");
        else
            System.err.println("failed!");
    }


    public void deleteSlice (String sliceName) throws XmlRpcException
    {
        Boolean reply = (Boolean) this.client.execute("api.deleteSlice",
                                                        new Object[] { sliceName });
        if (reply == null) {
            System.err.println("Got 'null' as reply :-(");
            System.exit(-1);
        }
        if (reply)
            System.out.println("success!");
        else
            System.err.println("failed!");
    }

    public void removeFlowSpace (String indexStr) throws XmlRpcException
    {
        FlowChange change = new FlowChange(FlowChangeOp.REMOVE,
                                            Integer.valueOf(indexStr));
        List<Map<String, String>> mapList = new LinkedList<Map<String, String>>();
        mapList.add(change.toMap());

        try {
            Object[] reply = (Object[]) this.client.execute("api.changeFlowSpace",
                                                            new Object[] { mapList });

            if (reply == null) {
                System.err.println("Got 'null' for reply :-(");
                System.exit(-1);
            }
            if (reply.length > 0)
                System.out.println("success: " + (String) reply[0]);
            else
                System.err.println("failed!");
        } catch (XmlRpcException e) {
            System.err.println("Failed: Flow Entry not found");
            System.exit(-1);
        }
    }

    public void addFlowSpace (String dpid, String priority, String match,
                                    String actions) throws
                                                        XmlRpcException,
                                                        MalformedFlowChange
    {
        do_flowSpaceChange(FlowChangeOp.ADD, dpid, null, priority, match,
                                    actions);
    }

    public void changeFlowSpace (String idStr, String dpid, String priority,
                                    String match, String actions) throws XmlRpcException,
                                                                        MalformedFlowChange
    {
        do_flowSpaceChange(FlowChangeOp.CHANGE, dpid, idStr, priority, match,
                            actions);
    }

    private void do_flowSpaceChange (FlowChangeOp op, String dpid, String idStr,
                                    String priority, String match,
                                    String actions) throws XmlRpcException
    {
        if (match.equals("") || match.equals("any") || match.equals("all"))
            match = "OFMatch[]";

        Map<String, String> map = FlowChange.makeMap(op, dpid, idStr, priority,
                                                        match, actions);

        try {
            FlowChange.fromMap(map);
        } catch (MalformedFlowChange e) {
            System.err.println("Local sanity check failed: " + e);
            return;
        }
        List<Map<String, String>> mapList = new LinkedList<Map<String, String>>();
        mapList.add(map);
        Object[] reply = (Object[]) this.client.execute("api.changeFlowSpace",
                                                        new Object[] { mapList });
        if (reply == null) {
            System.err.println("Got 'null' for reply :-(");
            System.exit(-1);
        }
        if (reply.length > 0)
            System.out.println("success: " + (String) reply[0]);
        else
            System.err.println("failed!");
    }

    /**
     * Create an IP based flow space
     * @param name
     * @param dpid
     * @param priority
     * @param srcIP
     * @param dstIP
     * @throws CommandException
     */

    public void addIPFlowSpace(String name, String dpid, String priority,
                                String srcIP, String dstIP) throws CommandException
    {
        List<Map<String, String>> mapList = new LinkedList<Map<String, String>>();

        if (!dpid.trim().matches(DPID_REGEX))
            throw new CommandException("addIPFlowSpace(): Invalid DPID format: " + dpid);
        dpid = dpid.trim();

        if (!priority.trim().matches(PRIORITY_REGEX))
            throw new CommandException("addIPFlowSpace(): Invalid priority string: " + priority);
        priority = priority.trim();

        String actions = "Slice:" + name.trim() + "=4";

        try {
            String match = "nw_src=" + srcIP + ",nw_dst=" + dstIP;
            Map<String, String> map_src_to_dst = FlowChange.makeMap(
                    FlowChangeOp.ADD, dpid, null, priority, match, actions);
            FlowChange.fromMap(map_src_to_dst);
            mapList.add(map_src_to_dst);

            match = "nw_src=" + dstIP + ",nw_dst=" + srcIP;
            Map<String, String> map_dst_to_src = FlowChange.makeMap(
                    FlowChangeOp.ADD, dpid, null, priority, match, actions);
            FlowChange.fromMap(map_dst_to_src);
            mapList.add(map_dst_to_src);
            execute("api.changeFlowSpace", new Object[] { mapList });
        } catch (Exception e) {
            throw new CommandException(e);
        }
    }

    /**
     * Create a VLAN based flow space on specific ports
     * @param name
     * @param dpid
     * @param priority
     * @param tag
     * @param ports - can be a combination of range and comma-separated list: a-b,c,d,e-f etc. It can also say 'any'
     * @throws CommandException
     */
    public void addVlanFlowSpace(String name, String dpid, String priority,
                                    int tag, String ports) throws CommandException
    {

        if ((name == null) || (dpid == null) || (priority == null) || (ports == null))
            throw new CommandException("addVlanFlowSpace(): Slice name, dpid, priority and Port list must be specified ");

        List<Map<String, String>> mapList = new LinkedList<Map<String, String>>();
        String actions = "Slice:" + name.trim() + "=4";

        if (!priority.trim().matches(PRIORITY_REGEX))
            throw new CommandException("addVlanFlowSpace(): Invalid priority string: " + priority);
        priority = priority.trim();

        if (!dpid.trim().matches(DPID_REGEX))
            throw new CommandException("addVlanFlowSpace(): Invalid DPID format: " + dpid);
        dpid = dpid.trim();

        // convert ports into a list
        if (!ports.matches(PORTS_REGEX))
            throw new CommandException("addVlanFlowSpace(): Invalid ports string: " + ports);
        ports = ports.trim();

        // to avoid repetitions
        Set<String> enumeratedPorts = new HashSet<String>();

        try {
            if (ports.matches("any")) {
                // insert one rule
                String match = "dl_vlan=" + tag;
                Map<String, String> mapVlan = FlowChange.makeMap(
                        FlowChangeOp.ADD, dpid, null, priority, match, actions);
                FlowChange.fromMap(mapVlan);
                mapList.add(mapVlan);
            } else {
                // insert a bunch of rules
                String[] groups = ports.trim().split(",");
                for (String group: groups) {
                    if (group.matches("\\s*\\d+\\s*-\\s*\\d+\\s*")) {
                        String[] maxMin = group.split("-");
                        Integer minP = Integer.decode(maxMin[0].trim());
                        Integer maxP = Integer.decode(maxMin[1].trim());
                        if (maxP < minP) {
                            // swap
                            minP += maxP;
                            maxP = minP - maxP;
                            minP = minP - maxP;
                        }
                        for(Integer i = minP; i <= maxP; i++) {
                            enumeratedPorts.add(i.toString());
                        }
                    } else {
                        enumeratedPorts.add(group.trim());
                    }
                }
                for(String port: enumeratedPorts) {
                    // create a rule per fvPort
                    String match = "in_port=" + port + ",dl_vlan=" + tag;
                    Map<String, String> mapVlan = FlowChange.makeMap(
                            FlowChangeOp.ADD, dpid, null, priority, match, actions);
                    FlowChange.fromMap(mapVlan);
                    mapList.add(mapVlan);
                }
            }
            // call flowvisor
            execute("api.changeFlowSpace", new Object[] { mapList });
        } catch (MalformedFlowChange mfce) {
            throw new CommandException("addVlanFlowSpace(): malformed flow change: " + mfce);
        }

    }

    public static void main (String[] args) throws Exception
    {
        //152.54.14.8
        FlowvisorInterface intrface = new FlowvisorInterface("https://152.54.14.8:8080/xmlrpc", "fvadmin", "flowvisor");
        intrface.connect();
        intrface.createSlice("rajesh1", "tcp:152.54.14.13:6633", "aaa", "geren@email.com");

    }
}
