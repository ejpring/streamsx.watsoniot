// Copyright (C) 2017  International Business Machines Corporation
// All Rights Reserved

package com.ibm.streamsx.watsoniot.device;

import com.ibm.iotf.client.device.Command;
import com.ibm.iotf.client.device.CommandCallback;
import com.ibm.iotf.client.device.DeviceClient;

import com.ibm.streams.operator.management.OperatorManagement;

import java.io.IOException;

import java.lang.management.ManagementFactory;
import java.lang.management.ManagementFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;

import java.util.Hashtable;
import java.util.Properties;
import java.util.concurrent.LinkedBlockingQueue;

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

import org.apache.log4j.Logger;

////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * This class handles events received from devices via the Watson IoT Platform by
 * enqueuing them for the 'process' thread below to emit as output tuples.
 */
class WatsonIoTDeviceCallback implements CommandCallback {

  private final LinkedBlockingQueue<Command> queue; 
  public boolean enqueue;
  
  /**
   * This constructor saves the queue events will be put into as they are received.
   * @param queue the queue for events received from Watson IoT Platform
   */
  public WatsonIoTDeviceCallback(LinkedBlockingQueue<Command> queue) {
    this.queue = queue;
    this.enqueue = false; 
  }
	
  /**
   * This method enqueues events received from devices via the Watson IoT Platform.
   * @param event event received from Watson IoT Platform 
   */
  @Override
    public void processCommand(Command command) {
    if (!enqueue) return;
    //System.out.println("WatsonIoTDeviceCallback.processCommand) enqueued " + command);
    try { queue.put(command); } catch (InterruptedException e) {}
  }

}

////////////////////////////////////////////////////////////////////////////////////////////////////

public class WatsonIoTDeviceClient extends DeviceClient implements WatsonIoTDeviceClientMBean, MBeanRegistration {
	
  private final ObjectName objectName;
  private final Logger logger;
  private final LinkedBlockingQueue<Command> queue;
  private final WatsonIoTDeviceCallback callback;

  /*
   * create ...
   * @throws Exception if ...
   */
  private WatsonIoTDeviceClient(ObjectName objectName, Properties credentials, Logger logger) throws Exception { 
    super(credentials); 
    this.objectName = objectName;
    this.logger = logger;
    this.queue = new LinkedBlockingQueue<Command>();
    this.callback = new WatsonIoTDeviceCallback(queue);

    super.setCommandCallback(callback);
  }

  /**
   * This static method creates a new ...............
   * @param credentials a Properties object containing Watson IoT device credentials
   * @throws Exception if the Watson IoT device client cannot be created
   */
  public static synchronized WatsonIoTDeviceClientMBean getClient(Properties credentials, boolean commands, Logger logger) throws Exception {
    
    logger.debug("WatsonIoTDeviceClient.getClient() started");

    MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();

    Hashtable<String,String> credentialsHashtable = new Hashtable<String,String>();
    for (String name: credentials.stringPropertyNames()) credentialsHashtable.put(name, credentials.getProperty(name));
    ObjectName mBeanName = new ObjectName("WatsonIoTDeviceClient", credentialsHashtable);

    if (!mBeanServer.isRegistered(mBeanName)) {
      try { mBeanServer.registerMBean( new WatsonIoTDeviceClient(mBeanName, credentials, logger), mBeanName ); }
      catch (InstanceAlreadyExistsException e) { logger.error("WatsonIoTApplicationClient.getClient() caught exception " + e); }
      logger.info("WatsonIoTDeviceClient created WatsonIotDeviceClientMBean");
    }

    WatsonIoTDeviceClientMBean mBeanProxy = JMX.newMBeanProxy(mBeanServer, mBeanName, WatsonIoTDeviceClientMBean.class);
    if (commands) mBeanProxy.setEnqueueCommands(true);

    logger.debug("WatsonIoTDeviceClient.getClient() ended");
    return mBeanProxy;
  }



  /**
   * This method ...
   */
  public void setEnqueueCommands(boolean enqueue) {
    callback.enqueue = enqueue;
  }


  /**
   * This method ...
   */
  public Object takeCommand(Class<?> commandClass) { 

    try { 
      logger.debug("WatsonIoTDeviceClient.takeCommand() waiting on queue ...");
      Command command = queue.take();
      logger.debug("WatsonIoTDeviceClient.takeCommand() dequeued " + command);
      Constructor<?> constructor = commandClass.getDeclaredConstructor(String.class, String.class, byte[].class);
      constructor.setAccessible(true);
      Object object = constructor.newInstance(command.getCommand(), command.getFormat(), command.getRawPayload());
      logger.debug("WatsonIoTDeviceClient.takeCommand() returning object of type " + commandClass.getName() + " containing " + object);
      return object; 
    } 
    catch (Exception e) { logger.error("WatsonIoTDeviceClient.takeCommand() caught exception " + e); } 

    return null;
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
