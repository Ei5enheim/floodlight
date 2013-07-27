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
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Hashtable;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.topovalidation.IOFFlowspace;
import net.floodlightcontroller.util.IPv4Address;
import net.floodlightcontroller.util.Vlan;

import org.openflow.util.HexString;

public class OFFlowspace implements Cloneable, IOFFlowspace
{
    // If any of the macs' are blocked then we can place them
    // into the hash table. False represents it is blocked while
    // true/null represents it is allowed. 
    protected Map<Long, Boolean> dataLayerSource;
    protected Map<Long, Boolean> dataLayerDestination;
    protected SortedMap<Vlan, Boolean> dataLayerVlan;
    // each bit position refers to the actual value
    protected byte dataLayerVlanPCP;
    protected Map<Short, Boolean> dataLayerType;
    //This is a 6 bit field, bit position refers to the value
    // zero is default. so setting it to one
    protected long networkTOS = 1;
    protected Map<Short, Boolean> networkProtocol;
    protected Map<IPv4Address, Boolean> networkSource;
    protected int networkSourceMask;
    protected Map<IPv4Address, Boolean> networkDestination;
    protected int networkDestinationMask;
    protected Map<Short, Boolean> transportSource;
    protected Map<Short, Boolean> transportDestination;
    private boolean isSparse = true;

    /**
     * default constructor
     *
     */

    public OFFlowspace()
    {

    }

    /**
     * By default, create a OFFlowspace that allows everything (mostly because it's
     * the least amount of work to make a valid OFFlowspace)
     */
    public OFFlowspace(boolean dummy)
    {
        this.dataLayerVlan = new TreeMap<Vlan, Boolean>();
        this.dataLayerVlan = Collections.synchronizedSortedMap(dataLayerVlan);
        this.dataLayerType = new Hashtable<Short, Boolean>();
        this.networkProtocol = new Hashtable<Short, Boolean>();
        this.networkSource = new HashMap<IPv4Address, Boolean>();
        this.networkDestination = new HashMap<IPv4Address, Boolean>();
        this.transportDestination = new Hashtable<Short, Boolean>();
        this.transportSource = new Hashtable<Short, Boolean>();
    }

    // utility method 
    private long byteArray2Long(byte[] array)
    {
        long value = 0;
        int LENGTH = array.length;

        for (int i = 0; i < LENGTH; i++)
        {
            value = (value << 8) | (array[i] & 0x00FF);
        }
        return (value);
    }

    public void setFlowSpaceType (boolean isSparse)
    {
        this.isSparse = isSparse;
    }

    /**
     * Get dl_dst
     * 
     * @return long
     */
    public Long getRandomDataLayerDst ()
    {
        Long mac = null;
        int MAX_TRIES = 5;
        int count = 0;
        if (dataLayerDestination != null) {
            /*
            if (dataLayerDestination.values.contains(Boolean.valueOf("false")))
                return (null);
            */
            int indx = 0;
            int boundary = dataLayerDestination.size();
            synchronized (dataLayerDestination) {
                Object[] set = dataLayerDestination.entrySet().toArray();
                indx = ThreadLocalRandom.current().nextInt(0, boundary);
                Boolean bool = ((Entry<Long, Boolean>)set[indx]).getValue();
                mac = ((Entry<Long, Boolean>)set[indx]).getKey();
                while ((!bool.booleanValue()) && (count < 5)) {
                    indx = ThreadLocalRandom.current().nextInt(0, boundary);
                    mac = ((Entry<Long, Boolean>)set[indx]).getKey();
                    bool = ((Entry<Long, Boolean>)set[indx]).getValue();
                    count++;
                }
                if (!bool.booleanValue())
                    mac = null;
            }
        }
        return (mac);
    }

    public boolean getDataLayerDst (long mac)
    {
        if (dataLayerDestination != null) {
            Boolean isValied = dataLayerDestination.get(mac);
            if (isValied != null)
                return (isValied.booleanValue());
        }
        return (true);
    }

    public OFFlowspace setDataLayerDst (Map<Long, Boolean> dataLayerDst)
    {
        this.dataLayerDestination = dataLayerDst;
        return this;
    }

    private void addDataLayerDst (long macAddress, Boolean bool)
    {
        if (dataLayerDestination == null)
            dataLayerDestination = new Hashtable<Long, Boolean>();

        dataLayerDestination.put(macAddress, bool);
    }

    /**
     * add dl_dst
     * 
     * @param dataLayerDestination
     */
    public OFFlowspace addDataLayerDst (long macAddress)
    {
        addDataLayerDst(macAddress, Boolean.valueOf(true));
        return (this);
    }

    public OFFlowspace addDataLayerDst (byte[] macAddress)
    {
        addDataLayerDst(byteArray2Long(macAddress),
                        Boolean.valueOf(true));
        return (this);
    }


    public OFFlowspace blockDataLayerDst (long macAddress)
    {
        addDataLayerDst(macAddress, Boolean.valueOf(false));
        return (this);
    }

    public OFFlowspace blockDataLayerDst(byte[] macAddress)
    {
        addDataLayerDst(byteArray2Long(macAddress),
                         Boolean.valueOf(false));
        return (this);
    }

    public OFFlowspace removeDataLayerDst (long macAddress)
    {
        if (dataLayerDestination != null) {
            dataLayerDestination.remove(macAddress);
        }
        return (this);
    }

    public OFFlowspace removeDataLayerDst (byte[] macAddress)
    {
        removeDataLayerDst (byteArray2Long(macAddress));
        return (this);
    }

    /**
     * add dl_dst, but first translate to byte[] using HexString
     * 
     * @param mac
     *            A colon separated string of 6 pairs of octets, e..g.,
     *            "00:17:42:EF:CD:8D"
     */
    public OFFlowspace addDataLayerDst(String mac)
    {
        byte bytes[] = HexString.fromHexString(mac);
        if (bytes.length != 6)
            throw new IllegalArgumentException(
                                     "expected string with 6 octets, got '"
                                     + mac
                                     + "'");
        addDataLayerDst(byteArray2Long(bytes), Boolean.valueOf(true));
        return this;
    }

    /**
     * Get Random dl_src
     * 
     * @return long
     */
    public Long getRandomDataLayerSrc()
    {
        Long mac = null;
        int MAX_TRIES = 5;
        int count = 0;
        if (dataLayerSource != null) {
            int indx = 0;
            int boundary = dataLayerSource.size();
            synchronized (dataLayerSource) {
                Object[] set = dataLayerSource.entrySet().toArray();
                indx = ThreadLocalRandom.current().nextInt(0, boundary);
                Boolean bool = ((Entry<Long, Boolean>)set[indx]).getValue();
                mac = ((Entry<Long, Boolean>)set[indx]).getKey();
                while ((!bool.booleanValue()) && (count < MAX_TRIES)) {
                    indx = ThreadLocalRandom.current().nextInt(0, boundary);
                    mac = ((Entry<Long, Boolean>)set[indx]).getKey();
                    bool = ((Entry<Long, Boolean>)set[indx]).getValue();
                    count++;
                }
                if (!bool.booleanValue())
                    mac = null;
            }
        }
        return (mac);
    }

    public boolean getDataLayerSrc (long mac)
    {
        if (dataLayerSource != null) {
            Boolean isValied = dataLayerSource.get(mac);
            if (isValied != null)
                return (isValied.booleanValue());
        }
        return (true);
    }

    /**
     * Set dl_src
     * 
     * @param dataLayerSource
     */
    public OFFlowspace setDataLayerSrc (Map<Long, Boolean> dataLayerSource)
    {
        this.dataLayerSource = dataLayerSource;
        return this;
    }

    private void addDataLayerSrc (long macAddress, Boolean bool)
    {
        if (this.dataLayerSource == null)
            this.dataLayerSource = new Hashtable<Long, Boolean>();

        this.dataLayerSource.put(macAddress, bool);
    }

    /**
     * add dl_src
     * 
     * @param dataLayerSource
     */
    public OFFlowspace addDataLayerSrc (long macAddress)
    {
        this.addDataLayerSrc(macAddress, Boolean.valueOf("true"));
        return this;
    }

    public OFFlowspace addDataLayerSrc (byte[] macAddress)
    {
        this.addDataLayerSrc(byteArray2Long(macAddress),
                                Boolean.valueOf("true"));
        return this;
    }

    public OFFlowspace blockDataLayerSrc (long macAddress)
    {
        this.addDataLayerSrc(macAddress, Boolean.valueOf("false"));
        return this;
    }

    public OFFlowspace blockDataLayerSrc (byte[] macAddress)
    {
        this.addDataLayerSrc(byteArray2Long(macAddress),
                                Boolean.valueOf("false"));
        return this;
    }

    public OFFlowspace removeDataLayerSrc (long macAddress)
    {
        if (dataLayerSource != null)
            dataLayerSource.remove (macAddress);
        return this;
    }

    public OFFlowspace removeDataLayerSrc (byte[] macAddress)
    {
        removeDataLayerSrc(byteArray2Long(macAddress));
        return this;
    }

    /**
     * add dl_src, but first translate to byte[] using HexString
     * 
     * @param mac
     *            A colon separated string of 6 pairs of octets, e..g.,
     *            "00:17:42:EF:CD:8D"
     */
    public OFFlowspace addDataLayerSrc (String mac)
    {
        byte bytes[] = HexString.fromHexString(mac);
        if (bytes.length != 6)
                        throw new IllegalArgumentException(
                                          "expected string with 6 octets, got '"
                                          + mac
                                          + "'");
        return (this.addDataLayerSrc(bytes));
    }

    /**
     * Get dl_type
     * 
     * @return boolean
     */
    public boolean getDataLayerType (short etherType)
    {
        if (dataLayerType != null) {
            Boolean isValied = dataLayerType.get(etherType); 
            if (isValied != null) { 
                return (isValied.booleanValue());
            }
        }
        return (isSparse);
    }

    public Short getRandomDataLayerType ()
    {
        Short etherType = null;
        int MAX_TRIES  = 5;
        int count = 0;

        if (dataLayerType != null) {
            int boundary = dataLayerType.size();
            int indx = ThreadLocalRandom.current().nextInt(0, boundary); 
            synchronized (dataLayerType) {
                Object[] etherTypes = dataLayerType.entrySet().toArray();
                etherType = ((Entry<Short, Boolean>) etherTypes[indx]).getKey();
                Boolean bool = ((Entry<Short, Boolean>)etherTypes[indx]).getValue();
                while ((!bool.booleanValue()) && (count < MAX_TRIES)) {
                    indx = ThreadLocalRandom.current().nextInt(0, boundary);
                    etherType = ((Entry<Short, Boolean>) etherTypes[indx]).getKey();
                    bool = ((Entry<Short, Boolean>)etherTypes[indx]).getValue();
                    count++;
                }
                if (!bool.booleanValue())
                    etherType = null;
            }
        }
        return (etherType);
    }

    /**
     * Set dl_type
     * 
     * @param dataLayerType
     */
    public OFFlowspace setDataLayerType (Map<Short, Boolean> dataLayerType)
    {
        this.dataLayerType = dataLayerType;
        return this;
    }

    private void addDataLayerType (short dataLayerType, Boolean bool)
    {
        if (this.dataLayerType == null)
            this.dataLayerType = new Hashtable<Short, Boolean>();
        
        this.dataLayerType.put(dataLayerType, bool);
    }

    /**
     * Add dl_type
     * 
     * @param dataLayerType
     */
    public OFFlowspace addDataLayerType (short dataLayerType)
    {
        this.addDataLayerType(dataLayerType, Boolean.valueOf("true"));
        return this;
    }

    /**
     * block dl_type
     * 
     * @param dataLayerType
     */
    public OFFlowspace blockDataLayerType (short dataLayerType)
    {
        addDataLayerType(dataLayerType, Boolean.valueOf("false"));
        return this;
    }

    public OFFlowspace removeDataLayerType (short dataLayerType)
    {
        if (this.dataLayerType != null)
            this.dataLayerType.remove(dataLayerType);
        return (this);
    }

    /**
     * Get random dl_vlan
     * 
     * @return vlan tag; VLAN_NONE == no tag
     */
    public Short getRandomDataLayerVlan ()
    {
        int MAX_TRIES  = 5;
        int count = 0;
        Short rv = VLAN_NONE;

        if (dataLayerVlan != null) {
            int boundary = dataLayerVlan.size();
            int indx = ThreadLocalRandom.current().nextInt(0, boundary);
            synchronized(dataLayerVlan) {
                Object[] vlans = dataLayerVlan.entrySet().toArray();
                rv = ((Entry<Short, Boolean>)vlans[indx]).getKey(); 
                Boolean bool = ((Entry<Short, Boolean>)vlans[indx])
                                                        .getValue();
                while ((!bool.booleanValue()) && (count < MAX_TRIES)) {
                    indx = ThreadLocalRandom.current().nextInt(0, boundary);
                    rv = ((Entry<Short, Boolean>) vlans[indx]).getKey();
                    bool = ((Entry<Short, Boolean>)vlans[indx]).getValue();
                    count++;
                }
                if (!bool.booleanValue())
                    rv = VLAN_NONE;
            }
        }
        return (rv);
    }

    /**
     * Get dl_vlan
     * 
     * @return true or false
     */
    public boolean getDataLayerVlan (short vlan)
    {
        if (dataLayerVlan != null) {
            Boolean isValied = dataLayerVlan.get(new Vlan(vlan));
            if (isValied != null)
                return (isValied.booleanValue());
        }
        return (isSparse);
    }

    /**
     * Set dl_vlan
     * 
     * @param dataLayerVlan
     */
    public OFFlowspace setDataLayerVlan (SortedMap <Vlan,
                                            Boolean> dataLayerVlan)
    {
        this.dataLayerVlan = dataLayerVlan;
        return this;
    }

    public Vlan[] split (Vlan prev, Vlan newV)
    {
        LinkedList<Vlan> list = new LinkedList<Vlan>();
        Vlan temp = null;
        
        if (newV.vlan > prev.vlan) {
            temp = new Vlan(prev.vlan, (short)(newV.vlan - prev.vlan - 1)); 
            list.add(temp);
        }    
        if ((prev.vlan + prev.range) > (newV.vlan + newV.range)) {
            temp = new Vlan((short)(newV.vlan + newV.range +1),
                            (short)(prev.vlan + prev.range -
                                        newV.vlan - newV.range-1));
            list.add(temp);
        }
        
        return ((Vlan[])(list.toArray()));
    }

    private  void addDataLayerVlan (Vlan vlan, Boolean bool)
    {
        if (dataLayerVlan == null) {
            this.dataLayerVlan = new TreeMap<Vlan, Boolean>();
            this.dataLayerVlan = Collections.synchronizedSortedMap(
                                            dataLayerVlan);
        } else {
            if (dataLayerVlan.containsKey(vlan)) {
                Object[] set = (dataLayerVlan.keySet().toArray());
                Vlan prevKey = null;
                Boolean prevValue = null;
                synchronized (dataLayerVlan) {
                    int indx = Arrays.binarySearch(set, vlan);
                    prevKey = (Vlan) set[indx];
                }
                if (!vlan.equals(prevKey)) {
                    prevValue = dataLayerVlan.get(prevKey);
                    dataLayerVlan.remove(prevKey);
                    Vlan[] array = split(prevKey, vlan);
                    for (Vlan v: array)
                        dataLayerVlan.put(v, prevValue);
                    // recursive call 
                    addDataLayerVlan(vlan, bool);
                    return;
                }
            }
        }
        dataLayerVlan.put(vlan, bool);
    }

    /**
     * add dl_vlan
     * 
     * @param dataLayerVlan
     */
    public OFFlowspace addDataLayerVlan (short dataLayerVlan)
    {
        Vlan vlan = new Vlan(dataLayerVlan);
        this.addDataLayerVlan(vlan, Boolean.valueOf(true));
        return this;
    }

    /**
     * add dl_vlan
     * 
     * @param dataLayerVlan, range of vlans
     */
    public OFFlowspace addDataLayerVlan (short dataLayerVlan, short range)
    {
        this.addDataLayerVlan(new Vlan(dataLayerVlan, range),
                                Boolean.valueOf(true));
        return this;
    }

    public OFFlowspace blockDataLayerVlan (short dataLayerVlan, short range)
    {
        this.addDataLayerVlan(new Vlan(dataLayerVlan, range),
                                Boolean.valueOf(false));
        return this;
    }

    public OFFlowspace blockDataLayerVlan (short dataLayerVlan)
    {
        this.addDataLayerVlan(new Vlan(dataLayerVlan),
                                Boolean.valueOf(false));
        return this;
    }

    public OFFlowspace removeDataLayerVlan (short dataLayerVlan, short range)
    {
        if (this.dataLayerVlan != null)
            this.dataLayerVlan.remove(new Vlan(dataLayerVlan, range));
        return this;
    }

    /**
     * Get random dl_vlan_pcp
     * 
     * @return
     */
    public byte getRandomVlanPCP()
    {
        byte b = 0;
        short temp = (short) (dataLayerVlanPCP & 0x00FF);
        temp = (short) ((temp ^ (temp-1)) >> 1);
        for (b = 0; temp > 0; b++)
            temp = (short) (temp >> 1);
        return (b);
    }

    /**
     * Get dl_vlan_pcp
     * 
     * @return
     */
    public boolean getVlanPCP (byte pcp)
    {
        byte b = (byte)1;
        b = (byte) (b << pcp);
        return (((b & dataLayerVlanPCP) & 0x00FF) > 0);
    }

    /**
     * Set dl_vlan_pcp
     * 
     * @param pcp
     */
    public OFFlowspace setVlanPCP(byte pcp)
    {
        this.dataLayerVlanPCP = pcp;
        return this;
    }

    /**
     * add dl_vlan_pcp
     * 
     * @param pcp
     */
    public OFFlowspace addVlanPCP(byte pcp)
    {
        byte b = (byte) (1 << pcp);
        dataLayerVlanPCP = (byte) (dataLayerVlanPCP | b);
        return this;
    }

    /**
     * remove dl_vlan_pcp
     * 
     * @param pcp
     */
    public OFFlowspace removeVlanPCP(byte pcp)
    {
        byte b = (byte) (1 << pcp);
        dataLayerVlanPCP = (byte) (dataLayerVlanPCP & (~b));
        return this;
    }

    private byte getMask (int bitMask)
    {
        byte b = 0;
        int noOfBits = 0;

        if (bitMask == 0)
            return (b);

        noOfBits = bitMask & (-bitMask);
        if (noOfBits < 0) {
            return ((byte) 32);
        } else {
            for (b = 0; noOfBits > 0; b++)
            {
                noOfBits >>= 1;
            }
        }
        return (b); 
    }

    private IPv4Address generateIP (IPv4Address ip)
    {
        int rndmIP = ThreadLocalRandom.current().nextInt(0, 0x0FFFFFFF);
        int rndmMask = 0xFFFFFFFF;
        int IP = ip.getIP();
        IPv4Address newIP = new IPv4Address();

        if (ip.getMask() == (byte) 32) {
            newIP.setIP(rndmIP);
        } else {
            rndmMask = rndmMask << ip.getMask();
            rndmMask = ~rndmMask;
            rndmIP = rndmIP & rndmMask;
            newIP.setIP(IP | rndmIP); 
        }
        return (newIP);
    }

    /**
     * Get random nw_dst
     * 
     * @return
     */
    public IPv4Address getRandomNetworkDestination()
    {
        int MAX_TRIES = 5;
        int count = 0;
        IPv4Address ip = null;

        if (networkDestination != null) {
            int boundary = networkDestination.size();
            int indx = ThreadLocalRandom.current().nextInt(0,
                                                    boundary);
            synchronized (networkDestination) {
                Object[] set = networkDestination.entrySet().toArray();
                Boolean bool = ((Entry<IPv4Address, Boolean>)set[indx])
                                                                .getValue();
                ip = ((Entry<IPv4Address, Boolean>)set[indx]).getKey();
                while (!bool.booleanValue() && (count < MAX_TRIES)) {
                    indx = ThreadLocalRandom.current().
                                        nextInt(0, boundary);
                    ip = ((Entry<IPv4Address, Boolean>)set[indx]).getKey();
                    bool = ((Entry<IPv4Address, Boolean>)set[indx]).getValue();
                    count++;
                }
                if (!bool.booleanValue())
                    ip = null;
                else
                    ip = generateIP(ip);
            }
        }
        return (ip);
    }
  
    public boolean getNetworkDestination (IPv4Address ip)
    {
        if (networkDestination != null)
            return (getNetworkDestination(ip, true));
        return (isSparse);
    }

    private boolean getNetworkDestination (IPv4Address ip, boolean dummy)
    {
        boolean[] int2Bool = {false, true};
        int mask = networkDestinationMask;
        int result = checkCompleteIP(ip.getIP(), true);
        
        if (result > -1)
            return (int2Bool[result]);
        IPv4Address temp = new IPv4Address(ip.getIP());
        Boolean bool = null;
        while (mask != 0) {
            temp.setMask(getMask(mask));
            bool = networkDestination.get(temp);
            if (bool != null)
                return (bool.booleanValue());
            mask = mask - (mask & (-mask));
        }
        return (isSparse);
    }

    private int checkCompleteIP (int IP, boolean isDst)
    {
        IPv4Address ip = new IPv4Address(IP, (byte) 0);
        Boolean bool = null;

        if (isDst) 
            bool = networkDestination.get(ip);
        else
            bool = networkSource.get(ip);

        if (bool != null) {
            if (bool.booleanValue() == true)
                return (1);
            return (0);
        }   
        return (-1); 
    }

    /**
     * Get nw_dst
     * 
     * @return
     */
    public boolean getNetworkDestination (int ip)
    {
        if (networkDestination != null) {
            IPv4Address ipv4 = new IPv4Address(ip);
            return (getNetworkDestination(ipv4, true));        
        }
        return (isSparse);
    }

    /**
     * Set nw_dst
     * 
     * @param networkDestination
     */
    public OFFlowspace setNetworkDestination (Map<IPv4Address,
                                            Boolean> networkDestination)
    {
        this.networkDestination = networkDestination;
        return this;
    }

    public OFFlowspace addNetworkDestination (IPv4Address ip,
                                              boolean bool)
    {
        if (networkDestination == null)
            this.networkDestination = new HashMap<IPv4Address, Boolean>();

        this.networkDestination.put(ip, Boolean.valueOf(bool));
        return (this);
    }

    /**
     * add nw_dst
     * 
     * @param networkDestination mask
     */
    public OFFlowspace addNetworkDestination (int networkAddress, byte mask)
    {   
        IPv4Address ip = new IPv4Address (networkAddress, mask);
        this.addNetworkDestination(ip, true);
        return this;
    }

    /**
     * add nw_dst
     * 
     * @param networkDestination
     */
    public OFFlowspace addNetworkDestination (IPv4Address ip)
    {
        addNetworkDestination(ip, true);
        return this;
    }

    /**
     * block nw_dst
     * 
     * @param networkDestination
     */
    public OFFlowspace blockNetworkDestination (int networkAddress, byte mask)
    {
        IPv4Address ip = new IPv4Address (networkAddress, mask);

        this.addNetworkDestination(ip, false);
        return this;
    }

    /**
     * block nw_dst
     * 
     * @param networkDestination
     */
    public OFFlowspace blockNetworkDestination (IPv4Address ip)
    {
        addNetworkDestination(ip, false);
        return this;
    }

    public OFFlowspace removeNetworkDestination (IPv4Address ip)
    {
        if (networkDestination != null) {
            networkDestination.remove(ip);
        } 
        return this;
    }

    public OFFlowspace removeNetworkDestination (int ip, byte mask)
    {
        IPv4Address ipv4 = new IPv4Address(ip, mask);
        removeNetworkDestination(ipv4);
        return this;
    }

    /**
     * Get Random nw_proto
     * 
     * @return
     */
    public short getRandomNetworkProtocol()
    {
        Short proto = null;
        int MAX_TRIES  = 5;
        int count = 0;

        if (networkProtocol != null) {
            int boundary = networkProtocol.size();
            int indx = ThreadLocalRandom.current().nextInt(0, boundary);
            synchronized (networkProtocol) {
                Object[] protoTypes = networkProtocol.entrySet().toArray();
                proto = ((Entry<Short, Boolean>) protoTypes[indx]).getKey();
                Boolean bool = ((Entry<Short, Boolean>) protoTypes[indx]).getValue();
                while ((!bool.booleanValue()) && (count < MAX_TRIES)) {
                    indx = ThreadLocalRandom.current().nextInt(0, boundary);
                    proto = ((Entry<Short, Boolean>) protoTypes[indx]).getKey();
                    bool = ((Entry<Short, Boolean>) protoTypes[indx]).getValue();
                    count++;
                }
                if (!bool.booleanValue())
                    proto = null;
            }
        }
        return (proto);
    }

    public boolean getNetworkProtocol (short protocol)
    {
        if (networkProtocol != null) {
            Boolean isValied = networkProtocol.get(protocol); 
            if (isValied != null) { 
                return (isValied.booleanValue());
            }
        }
        return (isSparse);
    }

    /**
     * Set nw_proto
     * 
     * @param networkProtocol
     */
    public OFFlowspace setNetworkProtocol (Map <Short, Boolean> networkProtocol)
    {
        this.networkProtocol = networkProtocol;
        return this;
    }

    private void addNetworkProtocol (short proto, Boolean bool)
    {
        if (this.networkProtocol == null)
            this.networkProtocol = new Hashtable<Short, Boolean>();
        
        this.networkProtocol.put(proto, bool);
    }

    /**
     * Add network protocol 
     * 
     * @param network protocol
     */
    public OFFlowspace addNetworkProtocol (short prtcl)
    {
        this.addNetworkProtocol(prtcl, Boolean.valueOf("true"));
        return this;
    }

    /**
     * block network protocol
     * 
     * @param network protocol 
     */
    public OFFlowspace blockNetworkProtocol (short prtcl)
    {
        addNetworkProtocol(prtcl, Boolean.valueOf("false"));
        return this;
    }

    /**
     * remove a network protocol 
     * 
     * @param network protocol 
     */
    public OFFlowspace removeNetworkProtocol (short prtcl)
    {
        if (this.networkProtocol != null)
            networkProtocol.remove(prtcl);
        return this;
    }

    /**
     * Get random nw_src
     * 
     * @return
     */
    public IPv4Address getRandomNetworkSource()
    {
        int MAX_TRIES = 5;
        int count = 0;
        IPv4Address ip = null;

        if (networkSource != null) {
            int boundary = networkSource.size();
            int indx = ThreadLocalRandom.current().nextInt(0,
                                                    boundary);
            synchronized (networkSource) {
                Object[] set = networkSource.entrySet().toArray();
                Boolean bool = ((Entry<IPv4Address, Boolean>)set[indx])
                                                                .getValue();
                ip = ((Entry<IPv4Address, Boolean>)set[indx]).getKey();
                while (!bool.booleanValue() && (count < MAX_TRIES)) {
                    indx = ThreadLocalRandom.current().
                                        nextInt(0, boundary);
                    ip = ((Entry<IPv4Address, Boolean>)set[indx]).getKey();
                    bool = ((Entry<IPv4Address, Boolean>)set[indx]).getValue();
                    count++;
                }
                if (!bool.booleanValue())
                    ip = null;
                else
                    ip = generateIP(ip);
            }
        }
        return (ip);
    }

    /**
     * Get nw_src
     * 
     * @return
     */
    public boolean getNetworkSource (IPv4Address ip)
    {
        if (networkSource != null)
            return (getNetworkSource(ip, true));
        return (isSparse);
    }

    private boolean getNetworkSource (IPv4Address ip, boolean dummy)
    {
        boolean[] int2Bool = {false, true};
        int mask = networkSourceMask;
        int result = checkCompleteIP(ip.getIP(), false);
        
        if (result > -1)
            return (int2Bool[result]);
        IPv4Address temp = new IPv4Address(ip.getIP());
        Boolean bool = null;
        while (mask != 0) {
            temp.setMask(getMask(mask));
            bool = networkSource.get(temp);
            if (bool != null)
                return (bool.booleanValue());
            mask = mask - (mask & (-mask));
        }
        return (isSparse);
    }

    /**
     * Get nw_src
     * 
     * @return
     */
    public boolean getNetworkSource (int ip)
    {
        if (networkSource != null) {
            IPv4Address ipv4 = new IPv4Address(ip);
            return (getNetworkSource(ipv4, true));        
        }
        return (isSparse);
    }

    /**
     * Set nw_src
     * 
     * @param networkSource
     */
    public OFFlowspace setNetworkSource (Map<IPv4Address,
                                                Boolean> networkSource)
    {
        this.networkSource = networkSource;
        return this;
    }

    public OFFlowspace addNetworkSource (IPv4Address ip, boolean bool)
    {
        if (networkSource == null)
            this.networkSource = new HashMap<IPv4Address, Boolean>();

        this.networkSource.put(ip, Boolean.valueOf(bool));
        return (this);
    }

    /**
     * add nw_src
     * 
     * @param networkSource, mask
     */
    public OFFlowspace addNetworkSource (int networkAddress, byte mask)
    {   
        IPv4Address ip = new IPv4Address (networkAddress, mask);

        this.addNetworkSource(ip, true);
        return this;
    }

    /**
     * add nw_src
     * 
     * @param networkSource
     */
    public OFFlowspace addNetworkSource (IPv4Address ip)
    {
        addNetworkSource(ip, true);
        return this;
    }

    /**
     * block nw_dst
     * 
     * @param networkSource mask
     */
    public OFFlowspace blockNetworkSource (int networkAddress, byte mask)
    {
        IPv4Address ip = new IPv4Address (networkAddress, mask);

        this.addNetworkSource(ip, false);
        return this;
    }

    /**
     * block nw_src
     * 
     * @param networkSource
     */
    public OFFlowspace blockNetworkSource (IPv4Address ip)
    {
        addNetworkSource(ip, false);
        return this;
    }

    public OFFlowspace removeNetworkSource (IPv4Address ip)
    {
        if (networkSource != null)
            this.networkSource.remove(ip);

        return (this);
    }

    public OFFlowspace removeNetworkSource (int ip, byte range)
    {
        IPv4Address ipv4 = new IPv4Address(ip, range);
        removeNetworkSource(ipv4);
        return (this);
    }

    /**
     * Get random_networkTOS
     * 
     * @return
     */
    public byte getRandomNetworkTOS ()
    {
        int MAX_TRIES = 5;
        int count = 0;
        byte b = (byte) ThreadLocalRandom.current().nextInt(0, 64);
        long temp = 1 << b;
        temp = temp & networkTOS;
        while ((temp == 0) && (count < MAX_TRIES)) {
            b = (byte) ThreadLocalRandom.current().nextInt(0, 64);
            temp = 1 << b;
            temp = temp & networkTOS;
            count++;
        }
        if (count == MAX_TRIES)
            b = 0;
        return (b); 
    }
    
    /**
     * Get nw_tos 
     * 
     * @return : boolean
     */
    public boolean getNetworkTOS (byte tos)
    {
        long temp = 1 << tos;
        if ((networkTOS & temp) != 0)
            return (true);
        
        return (false);
    }

    /**
     * Set nw_tos 
     * 
     * @param networkTOS
     *            : 64-bit DSCP space(0-63)
     */
    public OFFlowspace setNetworkTypeOfService(long networkTOS)
    {
        this.networkTOS = networkTOS;
        return this;
    }

    /**
     * add network TOS
     * 
     * @param tos value
     */
    public OFFlowspace addNetworkTOS (byte tos)
    {
        long temp = 1 << tos;
        networkTOS = networkTOS | temp;
        return this;
    }

    /**
     * remove network TOS
     * 
     * @param tos value
     */
    public OFFlowspace removeNetworkTOS (byte tos)
    {
        long temp = 1 << tos;
        networkTOS = networkTOS & (~temp);
        return this;
    }

    /**
     * Get tp_dst
     * 
     * @return
     */
    public boolean getTPDst (short dst)
    {
        if (transportDestination != null) {
            Boolean isValied = transportDestination.get(dst);
            if (isValied != null)
                return (isValied.booleanValue());
        }
        return (isSparse);
    }

    public Short getRandomTPDst ()
    {
        Short dst = null;
        int MAX_TRIES  = 5;
        int count = 0;

        if (transportDestination != null) {
            int boundary = transportDestination.size();
            int indx = ThreadLocalRandom.current().nextInt(0, boundary); 
            synchronized (transportDestination) {
                Object[] dstPorts = transportDestination.entrySet().toArray();
                dst = ((Entry<Short, Boolean>) dstPorts[indx]).getKey();
                Boolean bool = ((Entry<Short, Boolean>) dstPorts[indx]).getValue();
                while ((!bool.booleanValue()) && (count < MAX_TRIES)) {
                    indx = ThreadLocalRandom.current().nextInt(0, boundary);
                    dst = ((Entry<Short, Boolean>) dstPorts[indx]).getKey();
                    bool = ((Entry<Short, Boolean>) dstPorts[indx]).getValue();
                    count++;
                }
                if (!bool.booleanValue())
                    dst = null;
            }
        }
        return (dst);
    }

    /**
     * Set tp_dst
     * 
     * @param transportDestination
     */
    public OFFlowspace setTPDst (Map<Short, Boolean> transportDestination)
    {
        this.transportDestination = transportDestination;
        return this;
    }

    private void addTPDst (short dst, Boolean bool)
    {
        if (this.transportDestination == null)
            this.transportDestination = new Hashtable<Short, Boolean>();
        
        this.transportDestination.put(dst, bool);
    }

    /**
     * Add tp_dst
     * 
     * @param tp_dst 
     */
    public OFFlowspace addTPDst (short dst)
    {
        this.addTPDst(dst, Boolean.valueOf("true"));
        return this;
    }

    /**
     * block tp_dst 
     * 
     * @param tp_dst
     */
    public OFFlowspace blockTPDst (short dst)
    {
        addTPDst (dst, Boolean.valueOf("false"));
        return this;
    }

    /**
     * remove tp_dst 
     * 
     * @param tp_dst
     */
    public OFFlowspace removeTPDst (short dst)
    {
        if (this.transportDestination != null)
            transportDestination.remove(dst);
        return this;
    }

    /**
     * Get tp_src
     * 
     * @return boolean
     */
    public boolean getTPSrc (short src)
    {
        if (transportSource != null) {
            Boolean isValied = transportSource.get(src);
            if (isValied != null)
                return (isValied.booleanValue());
        }
        return (isSparse);
    }

    public Short getRandomTPSrc ()
    {
        Short src = null;
        int MAX_TRIES  = 5;
        int count = 0;

        if (transportSource != null) {
            int boundary = transportSource.size();
            int indx = ThreadLocalRandom.current().nextInt(0, boundary); 
            synchronized (transportSource) {
                Object[] srcPorts = transportSource.entrySet().toArray();
                src = ((Entry<Short, Boolean>) srcPorts[indx]).getKey();
                Boolean bool = ((Entry<Short, Boolean>) srcPorts[indx]).getValue();
                while ((!bool.booleanValue()) && (count < MAX_TRIES)) {
                    indx = ThreadLocalRandom.current().nextInt(0, boundary);
                    src = ((Entry<Short, Boolean>) srcPorts[indx]).getKey();
                    bool = ((Entry<Short, Boolean>) srcPorts[indx]).getValue();
                    count++;
                }
                if (!bool.booleanValue())
                    src = null;
            }
        }
        return (src);
    }

    /**
     * Set tp_src
     * 
     * @param transportSource
     */
    public OFFlowspace setTPSrc (Map<Short, Boolean> transportSource)
    {
        this.transportSource = transportSource;
        return this;
    }

    private void addTPSrc (short src, Boolean bool)
    {
        if (this.transportSource == null)
            this.transportSource = new Hashtable<Short, Boolean>();
        
        this.transportSource.put(src, bool);
    }

    /**
     * Add tp_src
     * 
     * @param tp_src 
     */
    public OFFlowspace addTPSrc (short src)
    {
        this.addTPSrc(src, Boolean.valueOf("true"));
        return this;
    }

    /**
     * block tp_src 
     * 
     * @param tp_src 
     */
    public OFFlowspace blockTPSrc (short src)
    {
        addTPSrc(src, Boolean.valueOf("false"));
        return this;
    }

    /**
     * remove tp_src 
     * 
     * @param tp_src 
     */
    public OFFlowspace removeTPSrc (short src)
    {
        if (transportSource != null)
            transportSource.remove(src);
        return this;
    }


    /**
     * Implement clonable interface
     */
    @Override
    public OFFlowspace clone()
    {
        /*
        try {
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }*/
        return (null);
    }
}
