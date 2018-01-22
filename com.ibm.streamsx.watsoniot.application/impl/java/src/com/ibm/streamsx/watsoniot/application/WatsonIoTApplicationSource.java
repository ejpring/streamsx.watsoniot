// Copyright (C) 2017  International Business Machines Corporation
// All Rights Reserved

package com.ibm.streamsx.watsoniot.application;

import com.ibm.iotf.client.app.ApplicationClient;

import com.ibm.streams.operator.AbstractOperator;
import com.ibm.streams.operator.OperatorContext;
import com.ibm.streams.operator.OutputTuple;
import com.ibm.streams.operator.StreamSchema;
import com.ibm.streams.operator.StreamingOutput;
import com.ibm.streams.operator.model.Libraries;
import com.ibm.streams.operator.model.OutputPortSet.WindowPunctuationOutputMode;
import com.ibm.streams.operator.model.OutputPortSet;
import com.ibm.streams.operator.model.OutputPorts;
import com.ibm.streams.operator.model.Parameter;
import com.ibm.streams.operator.model.PrimitiveOperator;

import java.io.File;

import java.util.Properties;

import org.apache.log4j.Logger;

////////////////////////////////////////////////////////////////////////////////////////////////////

class WatsonIoTApplicationSourceEvent {
  public final String name;
  public final String format;
  public final String deviceType;
  public final String deviceId;
  public final byte[] data;

  public WatsonIoTApplicationSourceEvent(String name, String format, String deviceType, String deviceId, byte[] data) {
    this.name = name;
    this.format = format;
    this.deviceType = deviceType;
    this.deviceId = deviceId;
    this.data = data;
  }

  public String toString() { return "event='" + name + "', format='" + format + "', deviceType='" + deviceType + "', deviceId='" + deviceId + "', data='" + new String(data) + "'"; }
} 

////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * This class ...
 */
class WatsonIoTApplicationSourceProcess implements Runnable {

  private final WatsonIoTApplicationSource operator;
  private final WatsonIoTApplicationClientMBean client;
  private final Logger logger;
  private boolean running = true;

  /**
   * This constructor ...
   * @param operator the Streams operator
   */
  public WatsonIoTApplicationSourceProcess(WatsonIoTApplicationSource operator, WatsonIoTApplicationClientMBean client, Logger logger) {	
    this.operator = operator;
    this.client = client;
    this.logger = logger;
    logger.debug("WatsonIoTApplicationSourceProcess constructor executed");
  }

  /**
   * This method dequeues and processes events from applications via the Watson IoT Platform. It copies fields from the event into an output tuple and sends it downstream.
   */
  @Override
    public void run() {
    logger.debug("WatsonIoTApplicationSourceProcess.run() started");
    
    while (running) {
      try { 
        logger.debug("WatsonIoTApplicationSourceProcess.run() waiting for event ...");
        WatsonIoTApplicationSourceEvent event = (WatsonIoTApplicationSourceEvent)client.takeEvent(); 
        if (event==null) continue;
        logger.debug("WatsonIoTApplicationSourceProcess.run() proceeding with " + event);
      
        StreamingOutput<OutputTuple> outputStream = operator.getOperatorContext().getStreamingOutputs().get(0);
        OutputTuple outputTuple = outputStream.newTuple();
        outputTuple.setString(operator.eventNameAttribute, event.name);
        if (operator.eventFormatAttribute!=null) outputTuple.setString(operator.eventFormatAttribute, event.format);
        if (operator.eventDeviceTypeAttribute!=null) outputTuple.setString(operator.eventDeviceTypeAttribute, event.deviceType);
        if (operator.eventDeviceIdAttribute!=null) outputTuple.setString(operator.eventDeviceIdAttribute, event.deviceId);
        outputTuple.setString(operator.eventDataAttribute, new String(event.data));
        outputStream.submit(outputTuple); 
        logger.debug("WatsonIoTApplicationSourceProcess.run() submitted " + outputTuple);
      }
      catch (Exception e) { logger.error("WatsonIoTApplicationSourceProcess.run() caught Exception: " + e); }	
    }
    
    logger.debug("WatsonIoTApplicationSourceProcess.run() ended");
  }
  
  /**
   * This method should be called by another thread to stop the thread running the run() method above..
   *
   */
  public void shutdown() { 
    logger.debug("WatsonIoTApplicationSourceProcess.shutdown() started");
    running = false; 
    logger.debug("WatsonIoTApplicationSourceProcess.shutdown() ended");
  }

}

////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * Class for WatsonIoTApplicationSource operator, which: 
 * <ul>
 * <li>recieves tuples from upstream operators and sends them as commands to devices via the Watson IoT Platform</li>
 * <li>receives events from devices via the Watson IoT Platform and sends them downstream as tuples to other operators.</li>
 * </ul>
 */

@PrimitiveOperator ( name="WatsonIoTApplicationSource", 
                     namespace="com.ibm.streamsx.watsoniot.application", 
                     description="The WatsonIoTApplicationSource operator connects an SPL graph to the Watson IoT Platform as an IoT 'application': it recieves 'events' from IoT devices and decodes them into output tuples. The operator will recieve all events matching the values specified by the 'subscription' parameters, that is, events matching any of the specified device types, device identifiers, event names, and event formats. The operator requires a file containing 'application credentials' issued by Watson IoT Platform. The credentials must be specified as shown in the 'Using a configuration file' section of the page at 'https://console.bluemix.net/docs/services/IoT/applications/libraries/java.html'. This operator may be used together with the WatsonIoTApplicationSink operator, which sends 'commands' to IoT devices. If so, the pair should specify the same credentials file, and should be fused into the same Streams PE.")

@OutputPorts ( {
	@OutputPortSet ( optional=false, 
                     cardinality=1, 
                     windowPunctuationOutputMode=WindowPunctuationOutputMode.Free,
                     description="The output port produces tuples decoded as 'events' received from IoT devices via the Watson IoT Platform. Output tuples must at least include attributes for the event name and event data. By default, the data is assumed to be formatted as a JSON-encoded string. Optionally, output tuples may include attributes for the device type, device identifier, and data format." )
      } )

@Libraries( { "opt/*" } )


public class WatsonIoTApplicationSource extends AbstractOperator {
	
  @Parameter ( name="applicationCredentials", 
               optional=false, 
               //cardinality=1, 
               description="the name of a file containing Watson IoT Platform application credentials" )
    public void setApplicationCredentials(String filename) { 
      this.applicationCredentialsFilename = filename;
      this.applicationCredentials = ApplicationClient.parsePropertiesFile(new File(filename)); }
    private String applicationCredentialsFilename;
    private Properties applicationCredentials;
  
  @Parameter ( name="subscriptionDeviceTypes", 
               optional=true, 
               description="output tuples will be produced from events received from these device types, defaulting to '+', meaning all device types" )
  public void setSubscriptionDeviceTypes(String[] subscriptions) { subscriptionDeviceTypes = subscriptions; }
  private String[] subscriptionDeviceTypes = { "+" };

  @Parameter ( name="subscriptionDeviceIds", 
               optional=true, 
               description="output tuples will be produced from events received from these device identifiers, defaulting to '+', meaning all devices" )
  public void setSubscriptionDeviceIds(String[] subscriptions) { subscriptionDeviceIds = subscriptions; }
  private String[] subscriptionDeviceIds = { "+" };

  @Parameter ( name="subscriptionEvents", 
               optional=true, 
               description="output tuples will be produced from events received with these names, defaulting to '+', meaning all names" )
  public void setSubscriptionEvents(String[] subscriptions) { subscriptionEvents = subscriptions; }
  private String[] subscriptionEvents = { "+" };

  @Parameter ( name="subscriptionFormats", 
               optional=true, 
               description="output tuples will be produced from events received in these data formats, defaulting to '+', meaning all formats" )
  public void setSubscriptionFormats(String[] subscriptions) { subscriptionFormats = subscriptions; }
  private String[] subscriptionFormats = { "+" };

	@Parameter ( name="eventName", 
                 optional=false, 
                 description="an output attribute of type 'rstring' for the name of the event recieved from a device via the Watson IoT Platform" )
	public void setEventName(String attribute) { this.eventNameAttribute = attribute; }
	public String eventNameAttribute;
	
	@Parameter ( name="eventData", 
                 optional=false, 
                 description="an output attribute of type 'rstring' for data recieved with an event from a device via the Watson IoT Platform" )
	public void setEventData(String attribute) { this.eventDataAttribute = attribute; }
	public String eventDataAttribute;
	
	@Parameter ( name="eventFormat", 
                 optional=true, 
                 description="optionally, an output attribute of type 'rstring' for the format of the data recieved with an event from a device via the Watson IoT Platform, with no default" )
	public void setEventFormat(String attribute) { this.eventFormatAttribute = attribute; }
	public String eventFormatAttribute = null;

	@Parameter ( name="eventDeviceType", 
                 optional=true, 
                 description="optionally, an output attribute of type 'rstring' for the type of the device that sent the event recieved via the Watson IoT Platform, with no default" )
	public void setEventDeviceType(String attribute) { this.eventDeviceTypeAttribute = attribute; }
	public String eventDeviceTypeAttribute = null;
	
	@Parameter ( name="eventDeviceId", 
                 optional=true, 
                 description="optionally, an output attribute of type 'rstring' for the identifier of the device that sent the event received from the Watson IoT Platform, with no default" )
	public void setEventDeviceId(String attribute) { this.eventDeviceIdAttribute = attribute; }
	public String eventDeviceIdAttribute = null;
	
  // internal state variables for this operator	
  private Logger logger;
  private WatsonIoTApplicationClientMBean client;
  private WatsonIoTApplicationSourceProcess process;
  private Thread thread;

    /**
     * Initialize this operator. Called once before any tuples are processed.
     * @param context OperatorContext for this operator.
     * @throws Exception Operator failure, will cause the enclosing PE to terminate.
     */
	@Override
	public synchronized void initialize(OperatorContext context) throws Exception {
    	
      super.initialize(context);

      logger = Logger.getLogger(this.getClass());
      logger.debug("WatsonIoTApplicationSource.initialize() started");
      
      StreamSchema schema = context.getStreamingOutputs().get(0).newTuple().getStreamSchema();
      if (schema.getAttribute(eventNameAttribute)==null) throw new Exception("sorry, no output attribute '" + eventNameAttribute + "' found for parameter 'eventName'");
      if (schema.getAttribute(eventDataAttribute)==null) throw new Exception("sorry, no output attribute '" + eventDataAttribute + "' found for parameter 'eventData'");
      if (eventFormatAttribute!=null && schema.getAttribute(eventFormatAttribute)==null) throw new Exception("sorry, no output attribute '" + eventFormatAttribute + "' found for parameter 'eventFormat'");
      if (eventDeviceTypeAttribute!=null && schema.getAttribute(eventDeviceTypeAttribute)==null) throw new Exception("sorry, no output attribute '" + eventDeviceTypeAttribute + "' found for parameter 'eventDeviceType'");
      if (eventDeviceIdAttribute!=null && schema.getAttribute(eventDeviceIdAttribute)==null) throw new Exception("sorry, no output attribute '" + eventDeviceIdAttribute + "' found for parameter 'eventDeviceId'");
      
      // get an instance of a Watson IoT application client, possibly shared with a WatsonIoTApplicationSink operator.
      client = WatsonIoTApplicationClient.getClient(applicationCredentials, logger);
      
      // configure the client to enqueue events to be processed in a separate thread
      client.setEnqueueEvents(WatsonIoTApplicationSourceEvent.class);

      // create a thread for processing events recieved from applications via 
      // Watson IoT Platform by sending them downstream as output tuples
      process = new WatsonIoTApplicationSourceProcess(this, client, logger);
      thread = getOperatorContext().getThreadFactory().newThread(process);
      thread.setDaemon(false);

      logger.debug("WatsonIoTApplicationSource.initialize() ended");
    }



    /**
     * Start this operator.
     * @throws Exception Operator failure, will cause the enclosing PE to terminate.
     */
    @Override
      public void allPortsReady() throws Exception {

        logger.debug("WatsonIoTApplicationSource.allPortsReady() started");

        thread.start();

        if (!client.isConnected()) {
          logger.info("WatsonIoTApplicationSource connecting to Watson IoT Platform with credentials from " + applicationCredentialsFilename);
          client.connect(); 
          if (!client.isConnected()) logger.error("WatsonIoTApplicationSource failed to connect"); }

        for (String deviceType: subscriptionDeviceTypes) {
          for (String deviceId: subscriptionDeviceIds) {
            for (String event: subscriptionEvents) {
              for (String format: subscriptionFormats) {
                logger.info("WatsonIoTApplicationSource subscribing to deviceType='" + deviceType + "', deviceId='" + deviceId + "', event='" + event + "', format='" + format + "'");
                client.subscribeToDeviceEvents(deviceType, deviceId, event, format); } } } }

        logger.debug("WatsonIoTApplicationSource.allPortsReady() ended");
    }




    /**
     * Shutdown this operator.
     * @throws Exception Operator failure, will cause the enclosing PE to terminate.
     */
    public synchronized void shutdown() throws Exception {
        logger.debug("WatsonIoTApplicationSource.shutdown() started");

        if (client.isConnected()) {
          logger.info("WatsonIoTApplicationSource disconnecting from Watson IoT Platform");
          client.disconnect(); }

        process.shutdown();
        thread.interrupt();

        super.shutdown();

        logger.debug("WatsonIoTApplicationSource.shutdown() ended");
    }

}

////////////////////////////////////////////////////////////////////////////////////////////////////
