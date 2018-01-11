
package com.ibm.streamsx.watsoniot;

import java.util.HashMap;
import java.util.Properties;
import com.ibm.iotf.client.app.ApplicationClient;

////////////////////////////////////////////////////////////////////////////////////////////////////

public class WatsonIoTApplicationClient extends ApplicationClient {
	
  // this object contains instances of this class indexed by Watson IoT credentials
  private static HashMap<Integer, WatsonIoTApplicationClient> instances = new HashMap<Integer, WatsonIoTApplicationClient>();
  
  // create an instance of this object containing a Watson IoT application client
  private WatsonIoTApplicationClient(Properties credentials) throws Exception { super(credentials); }

  /**
   * This static method creates a new instance of this object or returns an existing instance,
   * depending upon whether we have already seen the specified credentials
   * @param credentials a Properties object containing Watson IoT application credentials
   * @throws Exception if the Watson IoT application client cannot be created
   */
  public static synchronized WatsonIoTApplicationClient getInstance(Properties credentials) throws Exception {
    
    // use the hashcode of the credentials as a hash table key
    Integer key = new Integer(credentials.hashCode());

    // if we have not seen these credentials before, create a new instance of
    // this object containing a new Watson IoT client
    if (!instances.containsKey(key)) instances.put(key, new WatsonIoTApplicationClient(credentials));

    // return the instance of this object corresponding to the specified credentials
    return instances.get(key); }
  
}

////////////////////////////////////////////////////////////////////////////////////////////////////
