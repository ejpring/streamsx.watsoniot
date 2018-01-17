// Copyright (C) 2017  International Business Machines Corporation
// All Rights Reserved

package com.ibm.streamsx.watsoniot.application;

import com.ibm.iotf.client.app.ApplicationClient;
import com.ibm.iotf.client.app.Command;
import com.ibm.iotf.client.app.Event;
import com.ibm.iotf.client.app.EventCallback;

import com.ibm.streams.operator.management.OperatorManagement;

import java.io.IOException;

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
 * This class handles events received from applications via the Watson IoT Platform by
 * enqueuing them for the 'process' thread below to emit as output tuples.
 */
class WatsonIoTApplicationCallback implements EventCallback {

  private final LinkedBlockingQueue<Event> queue; 
  public boolean enqueue;
  
  /**
   * This constructor saves the queue events will be put into as they are received.
   * @param queue the queue for events received from Watson IoT Platform
   */
  public WatsonIoTApplicationCallback(LinkedBlockingQueue<Event> queue) {
    this.queue = queue;
    this.enqueue = false;
  }
	
  /**
   * This method enqueues events received from applications via the Watson IoT Platform.
   * @param event event received from Watson IoT Platform 
   */
  @Override
    public void processEvent(Event event) {
    if (!enqueue) return;
    //System.out.println("WatsonIoTApplicationCallback.processEvent() enqueued " + event);
    try { queue.put(event); } catch (InterruptedException e) {}
  }

  /**
   * This method discards commands intended for devices that may be received from applications via the Watson IoT Platform.
   * @param command command received from Watson IoT Platform 
   */
  @Override
    public void processCommand(Command command) {}
}

////////////////////////////////////////////////////////////////////////////////////////////////////

public class WatsonIoTApplicationClient extends ApplicationClient implements WatsonIoTApplicationClientMBean, MBeanRegistration {
	
  private final ObjectName objectName;
  private final Logger logger;
  private final LinkedBlockingQueue<Event> queue;
  private final WatsonIoTApplicationCallback callback;

  /*
   * create ...
   * @throws Exception if ...
   */
  private WatsonIoTApplicationClient(ObjectName objectName, Properties credentials, Logger logger) throws Exception { 
    super(credentials); 
    this.objectName = objectName;
    this.logger = logger;
    this.queue = new LinkedBlockingQueue<Event>();
    this.callback = new WatsonIoTApplicationCallback(queue);

    super.setEventCallback(callback);
  }

  /**
   * This static method creates a new ...............
   * @param credentials a Properties object containing Watson IoT application credentials
   * @throws Exception if the Watson IoT application client cannot be created
   */
  public static synchronized WatsonIoTApplicationClientMBean getClient(Properties credentials, boolean events, Logger logger) throws Exception {
    
    logger.debug("WatsonIoTApplicationClient.getClient() started");

    MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();

    Hashtable<String,String> credentialsHashtable = new Hashtable<String,String>();
    for (String name: credentials.stringPropertyNames()) credentialsHashtable.put(name, credentials.getProperty(name));
    ObjectName mBeanName = new ObjectName("WatsonIoTApplicationClient", credentialsHashtable);

    if (!mBeanServer.isRegistered(mBeanName)) {
      try { mBeanServer.registerMBean( new WatsonIoTApplicationClient(mBeanName, credentials, logger), mBeanName ); }
      catch (InstanceAlreadyExistsException e) { logger.error("WatsonIoTApplicationClient.getClient() caught exception " + e); }
      logger.info("WatsonIoTApplicationClient created WatsonIotApplicationClientMBean");
    }

    WatsonIoTApplicationClientMBean mBeanProxy = JMX.newMBeanProxy(mBeanServer, mBeanName, WatsonIoTApplicationClientMBean.class);
    if (events) mBeanProxy.setEnqueueEvents(true);

    logger.debug("WatsonIoTApplicationClient.getClient() ended");
    return mBeanProxy;
  }



  /**
   * This method ...
   */
  public void setEnqueueEvents(boolean enqueue) {
    callback.enqueue = enqueue;
  }


  /**
   * This method ...
   */
  public Object takeEvent(Class<?> eventClass) { 

    try { 
      logger.debug("WatsonIoTApplicationClient.takeEvent() waiting on queue ...");
      Event event = queue.take();
      logger.debug("WatsonIoTApplicationClient.takeEvent() dequeued " + event);
      Constructor<?> constructor = eventClass.getDeclaredConstructor(String.class, String.class, String.class, String.class, byte[].class);
      constructor.setAccessible(true);
      Object object = constructor.newInstance(event.getEvent(), event.getFormat(), event.getDeviceType(), event.getDeviceId(), event.getRawPayload());
      logger.debug("WatsonIoTApplicationClient.takeEvent() returning object of type " + eventClass.getName() + " containing " + object);
      return object; 
    } 
    catch (Exception e) { logger.error("WatsonIoTApplicationClient.takeEvent() caught exception " + e); } 

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
            catch (MBeanRegistrationException e) { logger.error("WatsonIoTApplicationClient.postRegister() caught exception " + e); }
            catch (InstanceNotFoundException e) { logger.error("WatsonIoTApplicationClient.postRegister() caught exception " + e); }
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
