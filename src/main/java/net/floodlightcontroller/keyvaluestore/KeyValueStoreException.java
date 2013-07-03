/*                                     
 * Author: Rajesh Gopidi               
 * Grad student at UNC Chapel Hill
 */             
                
package net.floodlightcontroller.keyvaluestore.internal;

import java.lang.Exception;

public class KeyValueStoreException extends Exception
{

    public KeyValueStoreException()
    {
        super();
    }

    public KeyValueStoreException(String msg)
    {
        super(msg);
    }
}
