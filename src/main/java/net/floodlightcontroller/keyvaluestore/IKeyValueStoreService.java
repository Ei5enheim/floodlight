/*
 * Author: Rajesh Gopidi
 * Grad student at UNC Chapel Hill
 */

package net.floodlightcontroller.keyvaluestore;

import net.floodlightcontroller.core.module.IFloodlightService;
import java.util.Collection;

public interface IKeyValueStoreService extends IFloodlightService
{
    public boolean addRUpdate (String storeName, Object key, Object value);

    public boolean delete (String storeName, Object key);

    public Object get (String storeName, Object key);

    public String getDomain (String IP);
    
    public String getIP2MACTable();

    /*
     * public Collection<Object> getAll(String storeName);
     * Right now voldemort doesn't support 
     * dynamic addition of new stores
     * boolean createStore (String storeName);
     * boolean deleteStore (String storeName);
     * boolean saveStore (String storeName);
     */
} 
