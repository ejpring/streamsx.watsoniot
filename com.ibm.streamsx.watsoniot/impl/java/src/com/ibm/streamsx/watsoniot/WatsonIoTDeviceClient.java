
package com.ibm.streamsx.watsoniot;

import com.ibm.iotf.client.device.DeviceClient;

import java.lang.management.ManagementFactory;
import org.apache.log4j.Logger;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.JMX;
import javax.management.MBeanRegistration;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MBeanServerDelegate;
import javax.management.MBeanServerNotification;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.relation.MBeanServerNotificationFilter;

import java.util.Properties;
import java.util.Hashtable;

import com.ibm.streams.operator.management.OperatorManagement;

////////////////////////////////////////////////////////////////////////////////////////////////////

public class WatsonIoTDeviceClient extends DeviceClient implements WatsonIoTDeviceClientMBean, MBeanRegistration {
	
  private final ObjectName objectName;

  /*
   * create ...
   * @throws Exception if ...
   */
  private WatsonIoTDeviceClient(ObjectName objectName, Properties credentials) throws Exception { 
    super(credentials); 
    this.objectName = objectName;
  }

  /**
   * This static method creates a new ...............
   * @param credentials a Properties object containing Watson IoT device credentials
   * @throws Exception if the Watson IoT device client cannot be created
   */
  public static synchronized WatsonIoTDeviceClientMBean getClient(Properties credentials, Logger logger) throws Exception {
    
    logger.info("WatsonIoTDeviceClient getClient() started");

    MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();

    Hashtable<String,String> credentialsHashtable = new Hashtable<String,String>();
    for (String name: credentials.stringPropertyNames()) credentialsHashtable.put(name, credentials.getProperty(name));
    ObjectName mBeanName = new ObjectName("WatsonIoTDeviceClient", credentialsHashtable);

    if (!mBeanServer.isRegistered(mBeanName)) {
      try { mBeanServer.registerMBean( new WatsonIoTDeviceClient(mBeanName, credentials), mBeanName ); }
      catch (InstanceAlreadyExistsException e) {}
      logger.info("WatsonIoTDeviceClient getClient() created WatsonIotDeviceClientMBean");
    }

    WatsonIoTDeviceClientMBean mBeanProxy = JMX.newMBeanProxy(mBeanServer, mBeanName, WatsonIoTDeviceClientMBean.class);

    logger.info("WatsonIoTDeviceClient getClient() ended");
    return mBeanProxy;
  }


  // implement MBeanRegistration methods ..................

  @Override
    public ObjectName preRegister(MBeanServer server, ObjectName name) throws Exception { return null; }

  @Override
    public void postRegister(Boolean registrationDone) {
    
    MBeanServerNotificationFilter mBeanServerNotificationFilter = new MBeanServerNotificationFilter();
    mBeanServerNotificationFilter.disableAllTypes();
    mBeanServerNotificationFilter.enableObjectName(OperatorManagement.getPEName());
    mBeanServerNotificationFilter.enableType(MBeanServerNotification.UNREGISTRATION_NOTIFICATION);
    
    try {
      ManagementFactory.getPlatformMBeanServer().addNotificationListener( 
        MBeanServerDelegate.DELEGATE_NAME, 
        new NotificationListener() {
          @Override
            public void handleNotification(Notification notification, Object handback) {
            try { ManagementFactory.getPlatformMBeanServer().unregisterMBean(objectName); }
            catch (MBeanRegistrationException e) {}
            catch (InstanceNotFoundException e) {}
          }
        }, 
        mBeanServerNotificationFilter, 
        null); } 
    catch (InstanceNotFoundException e) { throw new RuntimeException(e); }
  }
  
  @Override
    public void preDeregister() throws Exception {}
  
  @Override
    public void postDeregister() {}

}

////////////////////////////////////////////////////////////////////////////////////////////////////
