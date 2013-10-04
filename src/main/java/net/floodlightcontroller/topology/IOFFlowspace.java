/*
 * Author: Rajesh Gopidi
 * Grad student at UNC Chapel Hill
 */

package net.floodlightcontroller.topology;

import java.util.Map;
import java.util.SortedMap;

import net.floodlightcontroller.util.Vlan;
import net.floodlightcontroller.util.IPv4Address;

public interface IOFFlowspace
{
    final public static int VlanPCP_LEN = 8;
    final public static int VLAN_NONE = (short)0xffff;
    final public static int OFPFW_ALL = ((1 << 22) - 1);
    final public static int OFPFW_IN_PORT = 1 << 0; /* Switch input port. */
    final public static int OFPFW_DL_VLAN = 1 << 1; /* VLAN id. */
    final public static int OFPFW_DL_SRC = 1 << 2; /* Ethernet source address. */
    final public static int OFPFW_DL_DST = 1 << 3; /*
                                                    * Ethernet destination
                                                    * address.
                                                    */
    final public static int OFPFW_DL_TYPE = 1 << 4; /* Ethernet frame type. */
    final public static int OFPFW_NW_PROTO = 1 << 5; /* IP protocol. */
    final public static int OFPFW_TP_SRC = 1 << 6; /* TCP/UDP source port. */
    final public static int OFPFW_TP_DST = 1 << 7; /* TCP/UDP destination port. */

    /*
     * IP source address wildcard bit count. 0 is exact match, 1 ignores the
     * LSB, 2 ignores the 2 least-significant bits, ..., 32 and higher wildcard
     * the entire field. This is the *opposite* of the usual convention where
     * e.g. /24 indicates that 8 bits (not 24 bits) are wildcarded.
     */
    final public static int OFPFW_NW_SRC_SHIFT = 8;
    final public static int OFPFW_NW_SRC_BITS = 6;
    final public static int OFPFW_NW_SRC_MASK = ((1 << OFPFW_NW_SRC_BITS) - 1) << OFPFW_NW_SRC_SHIFT;
    final public static int OFPFW_NW_SRC_ALL = 32 << OFPFW_NW_SRC_SHIFT;

    /* IP destination address wildcard bit count. Same format as source. */
    final public static int OFPFW_NW_DST_SHIFT = 14;
    final public static int OFPFW_NW_DST_BITS = 6;
    final public static int OFPFW_NW_DST_MASK = ((1 << OFPFW_NW_DST_BITS) - 1) << OFPFW_NW_DST_SHIFT;
    final public static int OFPFW_NW_DST_ALL = 32 << OFPFW_NW_DST_SHIFT;

    final public static int OFPFW_DL_VLAN_PCP = 1 << 20; /* VLAN priority. */
    final public static int OFPFW_NW_TOS = 1 << 21; /*
                                                     * IP ToS (DSCP field, 6
                                                     * bits).
                                                     */

    final public static int OFPFW_ALL_SANITIZED = (((1 << 22) - 1)
                                                   & ~OFPFW_NW_SRC_MASK & ~OFPFW_NW_DST_MASK)
                                                  | OFPFW_NW_SRC_ALL
                                                  | OFPFW_NW_DST_ALL;

    /* List of Strings for marshalling and unmarshalling to human readable forms */
    final public static String STR_IN_PORT = "in_port";
    final public static String STR_DL_DST = "dl_dst";
    final public static String STR_DL_SRC = "dl_src";
    final public static String STR_DL_TYPE = "dl_type";
    final public static String STR_DL_VLAN = "dl_vlan";
    final public static String STR_DL_VLAN_PCP = "dl_vlan_pcp";
    final public static String STR_NW_DST = "nw_dst";
    final public static String STR_NW_SRC = "nw_src";
    final public static String STR_NW_PROTO = "nw_proto";
    final public static String STR_NW_TOS = "nw_tos";
    final public static String STR_TP_DST = "tp_dst";
    final public static String STR_TP_SRC = "tp_src";

    public void setFlowSpaceType (boolean isSparse);
    public Long getRandomDataLayerDst ();
    public boolean verifyDataLayerDst (long mac);
    public IOFFlowspace setDataLayerDst (Map<Long, Boolean> dataLayerDst);
    public IOFFlowspace addDataLayerDst (long macAddress);
    public IOFFlowspace addDataLayerDst (byte[] macAddress);
    public IOFFlowspace blockDataLayerDst (long macAddress);
    public IOFFlowspace blockDataLayerDst(byte[] macAddress);
    public IOFFlowspace removeDataLayerDst (long macAddress);
    public IOFFlowspace removeDataLayerDst (byte[] macAddress);
    public IOFFlowspace addDataLayerDst(String mac);
    
    public Long getRandomDataLayerSrc();
    public boolean verifyDataLayerSrc (long mac);
    public IOFFlowspace setDataLayerSrc (Map<Long, Boolean> dataLayerSource);
    public IOFFlowspace addDataLayerSrc (long macAddress);
    public IOFFlowspace addDataLayerSrc (byte[] macAddress);
    public IOFFlowspace blockDataLayerSrc (long macAddress);
    public IOFFlowspace blockDataLayerSrc (byte[] macAddress);
    public IOFFlowspace removeDataLayerSrc (long macAddress);
    public IOFFlowspace removeDataLayerSrc (byte[] macAddress);
    public IOFFlowspace addDataLayerSrc (String mac);

    public boolean verifyDataLayerType (short etherType);
    public Short getRandomDataLayerType ();
    public IOFFlowspace setDataLayerType (Map<Short, Boolean> dataLayerType);
    public IOFFlowspace addDataLayerType (short dataLayerType);
    public IOFFlowspace blockDataLayerType (short dataLayerType);
    public IOFFlowspace removeDataLayerType (short dataLayerType);

    public Short getRandomDataLayerVlan ();
    public boolean verifyDataLayerVlan (short vlan);
    public IOFFlowspace setDataLayerVlan (SortedMap <Vlan,
                                    Boolean> dataLayerVlan);
    public IOFFlowspace addDataLayerVlan (short dataLayerVlan);
    public IOFFlowspace addDataLayerVlan (short dataLayerVlan,
                                          short range);
    public IOFFlowspace blockDataLayerVlan (short dataLayerVlan,
                                            short range);
    public IOFFlowspace blockDataLayerVlan (short dataLayerVlan);
    public IOFFlowspace removeDataLayerVlan (short dataLayerVlan,
                                            short range);

    public byte getRandomVlanPCP();
    public boolean verifyVlanPCP (byte pcp);
    public IOFFlowspace setVlanPCP(byte pcp);
    public IOFFlowspace addVlanPCP(byte pcp);
    public IOFFlowspace removeVlanPCP(byte pcp);

    public IPv4Address getRandomNetworkDestination();
    public boolean verifyNetworkDestination (IPv4Address ip);
    public boolean verifyNetworkDestination (int ip);
    public IOFFlowspace setNetworkDestination (Map<IPv4Address,
                                    Boolean> networkDestination);
    public IOFFlowspace addNetworkDestination (IPv4Address ip,
                                              boolean bool);
    public IOFFlowspace addNetworkDestination (int networkAddress,
                                                byte mask);
    public IOFFlowspace addNetworkDestination (IPv4Address ip);
    public IOFFlowspace blockNetworkDestination (int networkAddress,
                                                byte mask);
    public IOFFlowspace blockNetworkDestination (IPv4Address ip);
    public IOFFlowspace removeNetworkDestination (IPv4Address ip);
    public IOFFlowspace removeNetworkDestination (int ip, byte mask);

    public short getRandomNetworkProtocol();
    public boolean verifyNetworkProtocol (short protocol);
    public IOFFlowspace setNetworkProtocol (Map <Short, Boolean> networkProtocol);
    public IOFFlowspace addNetworkProtocol (short prtcl);
    public IOFFlowspace blockNetworkProtocol (short prtcl);
    public IOFFlowspace removeNetworkProtocol (short prtcl);

    public IPv4Address getRandomNetworkSource();
    public boolean verifyNetworkSource (IPv4Address ip);
    public boolean verifyNetworkSource (int ip);
    public IOFFlowspace setNetworkSource (Map<IPv4Address,
                                    Boolean> networkSource);
    public IOFFlowspace addNetworkSource (IPv4Address ip,
                                            boolean bool);
    public IOFFlowspace addNetworkSource (int networkAddress,
                                           byte mask);
    public IOFFlowspace addNetworkSource (IPv4Address ip);
    public IOFFlowspace blockNetworkSource (int networkAddress,
                                            byte mask);
    public IOFFlowspace blockNetworkSource (IPv4Address ip);
    public IOFFlowspace removeNetworkSource (IPv4Address ip);
    public IOFFlowspace removeNetworkSource (int ip, byte range);
    public byte getRandomNetworkTOS ();
    public boolean verifyNetworkTOS (byte tos);
    public IOFFlowspace setNetworkTOS (long networkTOS);
    public IOFFlowspace addNetworkTOS (byte tos);
    public IOFFlowspace removeNetworkTOS (byte tos);

    public boolean verifyTPDst (short dst);
    public Short getRandomTPDst ();
    public IOFFlowspace setTPDst (Map<Short, Boolean> transportDestination);
    public IOFFlowspace addTPDst (short dst);
    public IOFFlowspace blockTPDst (short dst);
    public IOFFlowspace removeTPDst (short dst);

    public boolean verifyTPSrc (short src);
    public Short getRandomTPSrc ();
    public IOFFlowspace setTPSrc (Map<Short, Boolean> transportSource);
    public IOFFlowspace addTPSrc (short src);
    public IOFFlowspace blockTPSrc (short src);
    public IOFFlowspace removeTPSrc (short src);
}
