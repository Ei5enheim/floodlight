/*
 * Author: Rajesh Gopidi
 * Grad student at UNC Chapel Hill
 */

package net.floodlightcontroller.keyvaluestore;

import net.floodlightcontroller.core.module.IFloodlightService;
import java.util.Collection;

public interface IKeyValueStoreService <K, V> extends IFloodlightService
{
    public boolean addRUpdate (K key, V value);

    public boolean delete (K key);

    public V get (K key);

    public Collection<V> getAll();

    /*
     * boolean createStore(String storeName);
     * boolean deleteStore(String storeName);
     *
     */
} 
