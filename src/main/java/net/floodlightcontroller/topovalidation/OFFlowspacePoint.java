/*
 * Author: Rajesh Gopidi
 * Grad student at UNC Chapel Hill
 */

package net.floodlightcontroller.topovalidation;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.topovalidation.IOFFlowspace;

import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.jboss.netty.buffer.ChannelBuffer;
import org.openflow.protocol.serializers.OFFlowspacePointJSONSerializer;
import org.openflow.util.HexString;
import org.openflow.util.U16;
import org.openflow.util.U8;

public class OFFlowspacePoint implements Cloneable, IOFFlowspace
{
    protected int wildcards;
    protected byte[] dataLayerSource;
    protected byte[] dataLayerDestination;
    protected short dataLayerVirtualLan;
    protected byte dataLayerVirtualLanPriorityCodePoint;
    protected short dataLayerType;
    protected byte networkTypeOfService;
    protected byte networkProtocol;
    protected int networkSource;
    protected int networkDestination;
    protected short transportSource;
    protected short transportDestination;

    /**
     * By default, create a OFFlowspacePoint that verifies everything (mostly because it's
     * the least amount of work to make a valid OFFlowspacePoint)
     */
    public OFFlowspacePoint()
    {
        this.wildcards = OFPFW_ALL;
        this.dataLayerDestination = new byte[] { 0x0, 0x0, 0x0, 0x0, 0x0,
                                                0x0 };
        this.dataLayerSource = new byte[] { 0x0, 0x0, 0x0, 0x0, 0x0, 0x0 };
        this.dataLayerVirtualLan = Ethernet.VLAN_UNTAGGED;
        this.dataLayerVirtualLanPriorityCodePoint = 0;
        this.dataLayerType = 0;
        this.networkProtocol = 0;
        this.networkTypeOfService = 0;
        this.networkSource = 0;
        this.networkDestination = 0;
        this.transportDestination = 0;
        this.transportSource = 0;
    }

    /**
     * Get dl_dst
     * 
     * @return an arrays of bytes
     */
    public byte[] getDataLayerDestination() {
        return this.dataLayerDestination;
    }

    /**
     * Set dl_dst
     * 
     * @param dataLayerDestination
     */
    public OFFlowspacePoint setDataLayerDestination(byte[] dataLayerDestination) {
        this.dataLayerDestination = dataLayerDestination;
        return this;
    }

    /**
     * Set dl_dst, but first translate to byte[] using HexString
     * 
     * @param mac
     *            A colon separated string of 6 pairs of octets, e..g.,
     *            "00:17:42:EF:CD:8D"
     */
    public OFFlowspacePoint setDataLayerDestination(String mac) {
        byte bytes[] = HexString.fromHexString(mac);
        if (bytes.length != 6)
                              throw new IllegalArgumentException(
                                                                 "expected string with 6 octets, got '"
                                                                         + mac
                                                                         + "'");
        this.dataLayerDestination = bytes;
        return this;
    }

    /**
     * Get dl_src
     * 
     * @return an array of bytes
     */
    public byte[] getDataLayerSource() {
        return this.dataLayerSource;
    }

    /**
     * Set dl_src
     * 
     * @param dataLayerSource
     */
    public OFFlowspacePoint setDataLayerSource(byte[] dataLayerSource) {
        this.dataLayerSource = dataLayerSource;
        return this;
    }

    /**
     * Set dl_src, but first translate to byte[] using HexString
     * 
     * @param mac
     *            A colon separated string of 6 pairs of octets, e..g.,
     *            "00:17:42:EF:CD:8D"
     */
    public OFFlowspacePoint setDataLayerSource(String mac) {
        byte bytes[] = HexString.fromHexString(mac);
        if (bytes.length != 6)
                              throw new IllegalArgumentException(
                                                                 "expected string with 6 octets, got '"
                                                                         + mac
                                                                         + "'");
        this.dataLayerSource = bytes;
        return this;
    }

    /**
     * Get dl_type
     * 
     * @return ether_type
     */
    public short getDataLayerType() {
        return this.dataLayerType;
    }

    /**
     * Set dl_type
     * 
     * @param dataLayerType
     */
    public OFFlowspacePoint setDataLayerType(short dataLayerType) {
        this.dataLayerType = dataLayerType;
        return this;
    }

    /**
     * Get dl_vlan
     * 
     * @return vlan tag; VLAN_NONE == no tag
     */
    public short getDataLayerVirtualLan() {
        return this.dataLayerVirtualLan;
    }

    /**
     * Set dl_vlan
     * 
     * @param dataLayerVirtualLan
     */
    public OFFlowspacePoint setDataLayerVirtualLan(short dataLayerVirtualLan) {
        this.dataLayerVirtualLan = dataLayerVirtualLan;
        return this;
    }

    /**
     * Get dl_vlan_pcp
     * 
     * @return
     */
    public byte getDataLayerVirtualLanPriorityCodePoint() {
        return this.dataLayerVirtualLanPriorityCodePoint;
    }

    /**
     * Set dl_vlan_pcp
     * 
     * @param pcp
     */
    public OFFlowspacePoint setDataLayerVirtualLanPriorityCodePoint(byte pcp) {
        this.dataLayerVirtualLanPriorityCodePoint = pcp;
        return this;
    }

    /**
     * Get in_port
     * 
     * @return
     */
    public short getInputPort() {
        return this.inputPort;
    }

    /**
     * Set in_port
     * 
     * @param inputPort
     */
    public OFFlowspacePoint setInputPort(short inputPort) {
        this.inputPort = inputPort;
        return this;
    }

    /**
     * Get nw_dst
     * 
     * @return
     */
    public int getNetworkDestination() {
        return this.networkDestination;
    }

    /**
     * Set nw_dst
     * 
     * @param networkDestination
     */
    public OFFlowspacePoint setNetworkDestination(int networkDestination) {
        this.networkDestination = networkDestination;
        return this;
    }

    /**
     * Parse this match's wildcard fields and return the number of significant
     * bits in the IP destination field. NOTE: this returns the number of bits
     * that are fixed, i.e., like CIDR, not the number of bits that are free
     * like OpenFlow encodes.
     * 
     * @return a number between 0 (matches all IPs) and 63 ( 32>= implies exact
     *         match)
     */
    public int getNetworkDestinationMaskLen() {
        return Math.max(32 - ((wildcards & OFPFW_NW_DST_MASK) >> OFPFW_NW_DST_SHIFT),
                        0);
    }

    /**
     * Parse this match's wildcard fields and return the number of significant
     * bits in the IP destination field. NOTE: this returns the number of bits
     * that are fixed, i.e., like CIDR, not the number of bits that are free
     * like OpenFlow encodes.
     * 
     * @return a number between 0 (matches all IPs) and 32 (exact match)
     */
    public int getNetworkSourceMaskLen() {
        return Math.max(32 - ((wildcards & OFPFW_NW_SRC_MASK) >> OFPFW_NW_SRC_SHIFT),
                        0);
    }

    /**
     * Get nw_proto
     * 
     * @return
     */
    public byte getNetworkProtocol() {
        return this.networkProtocol;
    }

    /**
     * Set nw_proto
     * 
     * @param networkProtocol
     */
    public OFFlowspacePoint setNetworkProtocol(byte networkProtocol) {
        this.networkProtocol = networkProtocol;
        return this;
    }

    /**
     * Get nw_src
     * 
     * @return
     */
    public int getNetworkSource() {
        return this.networkSource;
    }

    /**
     * Set nw_src
     * 
     * @param networkSource
     */
    public OFFlowspacePoint setNetworkSource(int networkSource) {
        this.networkSource = networkSource;
        return this;
    }

    /**
     * Get nw_tos OFFlowspacePoint stores the ToS bits as top 6-bits, so right shift by 2
     * bits before returning the value
     * 
     * @return : 6-bit DSCP value (0-63)
     */
    public byte getNetworkTypeOfService() {
        return (byte) ((this.networkTypeOfService >> 2) & 0x3f);
    }

    /**
     * Set nw_tos OFFlowspacePoint stores the ToS bits as top 6-bits, so left shift by 2
     * bits before storing the value
     * 
     * @param networkTypeOfService
     *            : 6-bit DSCP value (0-63)
     */
    public OFFlowspacePoint setNetworkTypeOfService(byte networkTypeOfService) {
        this.networkTypeOfService = (byte) (networkTypeOfService << 2);
        return this;
    }

    /**
     * Get tp_dst
     * 
     * @return
     */
    public short getTransportDestination() {
        return this.transportDestination;
    }

    /**
     * Set tp_dst
     * 
     * @param transportDestination
     */
    public OFFlowspacePoint setTransportDestination(short transportDestination) {
        this.transportDestination = transportDestination;
        return this;
    }

    /**
     * Get tp_src
     * 
     * @return
     */
    public short getTransportSource() {
        return this.transportSource;
    }

    /**
     * Set tp_src
     * 
     * @param transportSource
     */
    public OFFlowspacePoint setTransportSource(short transportSource) {
        this.transportSource = transportSource;
        return this;
    }

    /**
     * Get wildcards
     * 
     * @return
     */
    public int getWildcards() {
        return this.wildcards;
    }

    /**
     * Get wildcards
     * 
     * @return
     */
    public Wildcards getWildcardObj() {
        return Wildcards.of(wildcards);
    }

    /**
     * Set wildcards
     * 
     * @param wildcards
     */
    public OFFlowspacePoint setWildcards(int wildcards) {
        this.wildcards = wildcards;
        return this;
    }

    /** set the wildcard using the Wildcards convenience object */
    public OFFlowspacePoint setWildcards(Wildcards wildcards) {
        this.wildcards = wildcards.getInt();
        return this;
    }

    @Override
    public int hashCode() {
        final int prime = 131;
        int result = 1;
        result = prime * result + Arrays.hashCode(dataLayerDestination);
        result = prime * result + Arrays.hashCode(dataLayerSource);
        result = prime * result + dataLayerType;
        result = prime * result + dataLayerVirtualLan;
        result = prime * result + dataLayerVirtualLanPriorityCodePoint;
        result = prime * result + inputPort;
        result = prime * result + networkDestination;
        result = prime * result + networkProtocol;
        result = prime * result + networkSource;
        result = prime * result + networkTypeOfService;
        result = prime * result + transportDestination;
        result = prime * result + transportSource;
        result = prime * result + wildcards;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof OFFlowspacePoint)) {
            return false;
        }
        OFFlowspacePoint other = (OFFlowspacePoint) obj;
        if (!Arrays.equals(dataLayerDestination, other.dataLayerDestination)) {
            return false;
        }
        if (!Arrays.equals(dataLayerSource, other.dataLayerSource)) {
            return false;
        }
        if (dataLayerType != other.dataLayerType) {
            return false;
        }
        if (dataLayerVirtualLan != other.dataLayerVirtualLan) {
            return false;
        }
        if (dataLayerVirtualLanPriorityCodePoint != other.dataLayerVirtualLanPriorityCodePoint) {
            return false;
        }
        if (inputPort != other.inputPort) {
            return false;
        }
        if (networkDestination != other.networkDestination) {
            return false;
        }
        if (networkProtocol != other.networkProtocol) {
            return false;
        }
        if (networkSource != other.networkSource) {
            return false;
        }
        if (networkTypeOfService != other.networkTypeOfService) {
            return false;
        }
        if (transportDestination != other.transportDestination) {
            return false;
        }
        if (transportSource != other.transportSource) {
            return false;
        }
        if ((wildcards & OFFlowspacePoint.OFPFW_ALL) != (other.wildcards & OFPFW_ALL)) { // only
            // consider
            // allocated
            // part
            // of
            // wildcards
            return false;
        }
        return true;
    }

    /**
     * Implement clonable interface
     */
    @Override
    public OFFlowspacePoint clone() {
        try {
            OFFlowspacePoint ret = (OFFlowspacePoint) super.clone();
            ret.dataLayerDestination = this.dataLayerDestination.clone();
            ret.dataLayerSource = this.dataLayerSource.clone();
            return ret;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Output a dpctl-styled string, i.e., only list the elements that are not
     * wildcarded A match-everything OFFlowspacePoint outputs "OFFlowspacePoint[]"
     * 
     * @return 
     *         "OFFlowspacePoint[dl_src:00:20:01:11:22:33,nw_src:192.168.0.0/24,tp_dst:80]"
     */
    @Override
    public String toString() {
        String str = "";

        // l1
        if ((wildcards & OFPFW_IN_PORT) == 0)
                                             str += "," + STR_IN_PORT + "="
                                                    + U16.f(this.inputPort);

        // l2
        if ((wildcards & OFPFW_DL_DST) == 0)
                                            str += ","
                                                   + STR_DL_DST
                                                   + "="
                                                   + HexString.toHexString(this.dataLayerDestination);
        if ((wildcards & OFPFW_DL_SRC) == 0)
                                            str += ","
                                                   + STR_DL_SRC
                                                   + "="
                                                   + HexString.toHexString(this.dataLayerSource);
        if ((wildcards & OFPFW_DL_TYPE) == 0)
                                             str += ","
                                                    + STR_DL_TYPE
                                                    + "=0x"
                                                    + Integer.toHexString(U16.f(this.dataLayerType));
        if ((wildcards & OFPFW_DL_VLAN) == 0)
                                             str += ","
                                                    + STR_DL_VLAN
                                                    + "=0x"
                                                    + Integer.toHexString(U16.f(this.dataLayerVirtualLan));
        if ((wildcards & OFPFW_DL_VLAN_PCP) == 0)
                                                 str += ","
                                                        + STR_DL_VLAN_PCP
                                                        + "="
                                                        + Integer.toHexString(U8.f(this.dataLayerVirtualLanPriorityCodePoint));

        // l3
        if (getNetworkDestinationMaskLen() > 0)
                                               str += ","
                                                      + STR_NW_DST
                                                      + "="
                                                      + cidrToString(networkDestination,
                                                                     getNetworkDestinationMaskLen());
        if (getNetworkSourceMaskLen() > 0)
                                          str += ","
                                                 + STR_NW_SRC
                                                 + "="
                                                 + cidrToString(networkSource,
                                                                getNetworkSourceMaskLen());
        if ((wildcards & OFPFW_NW_PROTO) == 0)
                                              str += "," + STR_NW_PROTO
                                                     + "="
                                                     + this.networkProtocol;
        if ((wildcards & OFPFW_NW_TOS) == 0)
                                            str += ","
                                                   + STR_NW_TOS
                                                   + "="
                                                   + this.getNetworkTypeOfService();

        // l4
        if ((wildcards & OFPFW_TP_DST) == 0)
                                            str += ","
                                                   + STR_TP_DST
                                                   + "="
                                                   + this.transportDestination;
        if ((wildcards & OFPFW_TP_SRC) == 0)
                                            str += "," + STR_TP_SRC + "="
                                                   + this.transportSource;
        if ((str.length() > 0) && (str.charAt(0) == ','))
                                                         str = str.substring(1); // trim
                                                                                 // the
                                                                                 // leading
                                                                                 // ","
        // done
        return "OFFlowspacePoint[" + str + "]";
    }

    /**
     * debug a set of wildcards
     */
    public static String debugWildCards(int wildcards) {
        String str = "";

        // l1
        if ((wildcards & OFPFW_IN_PORT) != 0) str += "|" + STR_IN_PORT;

        // l2
        if ((wildcards & OFPFW_DL_DST) != 0) str += "|" + STR_DL_DST;
        if ((wildcards & OFPFW_DL_SRC) != 0) str += "|" + STR_DL_SRC;
        if ((wildcards & OFPFW_DL_TYPE) != 0) str += "|" + STR_DL_TYPE;
        if ((wildcards & OFPFW_DL_VLAN) != 0) str += "|" + STR_DL_VLAN;
        if ((wildcards & OFPFW_DL_VLAN_PCP) != 0)
                                                 str += "|"
                                                        + STR_DL_VLAN_PCP;

        int nwDstMask = Math.max(32 - ((wildcards & OFPFW_NW_DST_MASK) >> OFPFW_NW_DST_SHIFT),
                                 0);
        int nwSrcMask = Math.max(32 - ((wildcards & OFPFW_NW_SRC_MASK) >> OFPFW_NW_SRC_SHIFT),
                                 0);

        // l3
        if (nwDstMask < 32)
                           str += "|" + STR_NW_DST + "(/" + nwDstMask + ")";

        if (nwSrcMask < 32)
                           str += "|" + STR_NW_SRC + "(/" + nwSrcMask + ")";

        if ((wildcards & OFPFW_NW_PROTO) != 0) str += "|" + STR_NW_PROTO;
        if ((wildcards & OFPFW_NW_TOS) != 0) str += "|" + STR_NW_TOS;

        // l4
        if ((wildcards & OFPFW_TP_DST) != 0) str += "|" + STR_TP_DST;
        if ((wildcards & OFPFW_TP_SRC) != 0) str += "|" + STR_TP_SRC;
        if ((str.length() > 0) && (str.charAt(0) == '|'))
                                                         str = str.substring(1); // trim
                                                                                 // the
                                                                                 // leading
                                                                                 // ","
        // done
        return str;
    }

    private String cidrToString(int ip, int prefix) {
        String str;
        if (prefix >= 32) {
            str = ipToString(ip);
        } else {
            // use the negation of mask to fake endian magic
            int mask = ~((1 << (32 - prefix)) - 1);
            str = ipToString(ip & mask) + "/" + prefix;
        }

        return str;
    }

    protected static String ipToString(int ip) {
        return Integer.toString(U8.f((byte) ((ip & 0xff000000) >> 24)))
               + "." + Integer.toString((ip & 0x00ff0000) >> 16) + "."
               + Integer.toString((ip & 0x0000ff00) >> 8) + "."
               + Integer.toString(ip & 0x000000ff);
    }
}
