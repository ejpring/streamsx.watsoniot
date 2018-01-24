// Copyright (C) 2017  International Business Machines Corporation
// All Rights Reserved

package com.ibm.streamsx.watsoniot.device;

import java.io.StringReader;

import java.util.Properties;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.log4j.Logger;

import com.ibm.iotf.client.device.Command;
import com.ibm.iotf.client.device.CommandCallback;
import com.ibm.iotf.client.device.DeviceClient;

import com.ibm.streams.operator.AbstractOperator;
import com.ibm.streams.operator.OperatorContext;
import com.ibm.streams.operator.StreamSchema;
import com.ibm.streams.operator.StreamingInput;
import com.ibm.streams.operator.Tuple;
import com.ibm.streams.operator.TupleAttribute;
import com.ibm.streams.operator.Type;
import com.ibm.streams.operator.OutputTuple;
import com.ibm.streams.operator.StreamingOutput;
import com.ibm.streams.operator.model.InputPortSet.WindowMode;
import com.ibm.streams.operator.model.InputPortSet.WindowPunctuationInputMode;
import com.ibm.streams.operator.model.InputPortSet;
import com.ibm.streams.operator.model.InputPorts;
import com.ibm.streams.operator.model.Libraries;
import com.ibm.streams.operator.model.OutputPortSet.WindowPunctuationOutputMode;
import com.ibm.streams.operator.model.OutputPortSet;
import com.ibm.streams.operator.model.OutputPorts;
import com.ibm.streams.operator.model.Parameter;
import com.ibm.streams.operator.model.PrimitiveOperator;

////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * This class handles commands received from applications via the Watson IoT Platform by
 * enqueuing them for the 'process' thread below to emit as output tuples.
 */
class WatsonIoTDeviceConnectorCallback implements CommandCallback {

  private final LinkedBlockingQueue<Command> queue; 
  
  /**
   * This constructor saves the queue commands will be put into as they are received.
   * @param queue the queue for commands received from Watson IoT Platform
   */
  public WatsonIoTDeviceConnectorCallback(LinkedBlockingQueue<Command> queue) {
    this.queue = queue;
  }
	
  /**
   * This method enqueues commands received from applications via the Watson IoT Platform.
   * @param command command received from Watson IoT Platform 
   */
  @Override
    public void processCommand(Command command) {
    try { queue.put(command); } catch (InterruptedException e) {}
  }
}

////////////////////////////////////////////////////////////////////////////////////////////////////


/**
 * This class ...
 */
class WatsonIoTDeviceConnectorProcess implements Runnable {

  private final WatsonIoTDeviceConnector operator;
  private final LinkedBlockingQueue<Command> queue; 
  private final Logger logger;
  private boolean running = true;

  /**
   * This constructor ...
   * @param operator the Streams operator
   */
  public WatsonIoTDeviceConnectorProcess(WatsonIoTDeviceConnector operator, LinkedBlockingQueue<Command> queue, Logger logger) {
	
    this.operator = operator;
    this.queue = queue;
    this.logger = logger;
    logger.debug("WatsonIoTDeviceConnectorProcess constructor executed");
  }

  /**
   * This method dequeues and processes commands from applications via the Watson IoT Platform. It copies fields from the command into an output tuple and sends it downstream.
   */
  @Override
    public void run() {
    logger.debug("WatsonIoTDeviceConnectorProcess run() started");
    
    while (running) {
      try {
        Command command = queue.take();
        if (command==null) continue;

        String name = command.getCommand();
        String format = command.getFormat();
        String data = new String(command.getRawPayload());
        logger.debug("WatsonIoTDeviceConnectorProcess received command=" + name + ", format=" + format + ", data=" + data);          ;
        
        StreamingOutput<OutputTuple> outputStream = operator.getOperatorContext().getStreamingOutputs().get(0);
        OutputTuple outputTuple = outputStream.newTuple();
        outputTuple.setString(operator.commandNameAttribute, name);
        outputTuple.setString(operator.commandDataAttribute, data);
        if (operator.commandFormatAttribute!=null) outputTuple.setString(operator.commandFormatAttribute, format);
        outputStream.submit(outputTuple);
      } 
      catch (InterruptedException e) {} // ignore this exception
      catch (Exception e) { logger.error("WatsonIoTDeviceConnectorProcess caught Exception: " + e); }	
    }
    
    logger.debug("WatsonIoTDeviceConnectorProcess run() ended");
  }
  
  /**
   * This method should be called by another thread to stop the thread running the run() method above..
   *
   */
  public void shutdown() { 
    logger.debug("WatsonIoTDeviceConnectorProcess shutdown() started");
    running = false; 
    logger.debug("WatsonIoTDeviceConnectorProcess shutdown() ended");
  }

}

////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * Class for WatsonIoTDeviceConnector operator, which: 
 * <ul>
 * <li>receives tuples from upstream operators and sends them as events to applications via the Watson IoT Platform</li>
 * <li>receives commands from applications via the Watson IoT Platform and sends them downstream as tuples to other operators.</li>
 * </ul>
 */

@PrimitiveOperator ( name="WatsonIoTDeviceConnector", 
                     namespace="com.ibm.streamsx.watsoniot.device", 
                     description="The WatsonIoTDeviceConnector operator connects an SPL graph to the Watson IoT Platform as an IoT 'device': it encodes input tuples as 'events' and sends them to IoT applications; concurrently, it receives 'commands' from IoT applications and decodes them into output tuples. The operator requires a file containing 'device credentials' issued by Watson IoT Platform. The credentials must be specified as shown in the 'Using a configuration file' section of the page at 'https://console.bluemix.net/docs/services/IoT/devices/libraries/java.html'.")

@InputPorts ( { 
	@InputPortSet ( optional=false, 
                    cardinality=1, 
                    windowingMode=WindowMode.NonWindowed, 
                    windowPunctuationInputMode=WindowPunctuationInputMode.Oblivious,
                    description="The input port consumes tuples encoded as 'events' and sends them to IoT applications via the Watson IoT Platform. Input tuples must at least include attributes for the event name and event data. By default, the data should be formatted as a JSON-encoded string. Optionally, input tuples may include an attribute for the data format." )
      } )

@OutputPorts ( {
	@OutputPortSet ( optional=false, 
                     cardinality=1, 
                     windowPunctuationOutputMode=WindowPunctuationOutputMode.Free,
                     description="The output port produces tuples decoded as 'commands' received from IoT applications via the Watson IoT Platform. Output tuples must at least include attributes for the command name and command data. By default, the data is assumed to be formatted as a JSON-encoded string. Optionally, output tuples may include an attribute for the data format." )
      } )

@Libraries( { "opt/*" } )

public class WatsonIoTDeviceConnector extends AbstractOperator {
	
    @Parameter ( name="deviceCredentials", 
               optional=false, 
               description="the contents of a Watson IoT Platform devicecredentials file (that is, a Java Properties file containing 'key = value' pairs), with newlines replaced by commas" )
    public void setDeviceCredentials(String credentials) throws Exception { 
      this.deviceCredentials = new Properties(); 
      deviceCredentials.load(new StringReader(credentials.replace(',', '\n'))); }
    private Properties deviceCredentials;

	@Parameter ( name="eventName", 
                 optional=false, 
                 description="an input attribute that will be sent to the Watson IoT Platform as the event name" )
	public void setEventName(TupleAttribute<Tuple,String> attribute) { this.eventNameAttribute = attribute; }
	private TupleAttribute<Tuple,String> eventNameAttribute; 

	@Parameter ( name="eventData", 
                 optional=false, 
                 description="an input attribute of type 'rstring' that will be sent to the Watson IoT Platform as event data" )
	public void setEventData(TupleAttribute<Tuple,String> attribute) { this.eventDataAttribute = attribute; }
	private TupleAttribute<Tuple,String> eventDataAttribute; 

	@Parameter ( name="eventFormat", 
                 optional=true, 
                 description="an input attribute of type 'rstring' that specifies the format of the data sent to the Watson IoT Platform, defaulting to 'json' if not specified" )
	public void setEventFormat(TupleAttribute<Tuple,String> attribute) { this.eventFormatAttribute = attribute; }
	private TupleAttribute<Tuple,String> eventFormatAttribute = null;
	
	@Parameter ( name="eventQOS", 
                 optional=true, 
                 description="the 'quality of service' for events sent to the Watson IoT Platform, either '0' or '1' or '2', defaulting to '0' if not specified" ) 
	public void setEventQOS(int value) { this.eventQOS = value; }
	private int eventQOS = 0;
	
	@Parameter ( name="commandName", 
                 optional=false, 
                 description="an output attribute of type 'rstring' for the name of the command received from the Watson IoT Platform" )
	public void setCommandName(String attribute) { this.commandNameAttribute = attribute; }
	public String commandNameAttribute;
	
	@Parameter ( name="commandData", 
                 optional=false, 
                 description="an output attribute of type 'rstring' or 'blob' for data received from the Watson IoT Platform with a command" )
	public void setCommandData(String attribute) { this.commandDataAttribute = attribute; }
	public String commandDataAttribute;
	
	@Parameter ( name="comandFormat", 
                 optional=true, 
                 description="optionally, an output attribute of type 'rstring' for the format of the data received from the Watson IoT Platform with a command, with no default" )
	public void setCommandFormat(String attribute) { this.commandFormatAttribute = attribute; }
	public String commandFormatAttribute = null;
	
	
  // internal state variables or this operator
  private Logger logger;
  private DeviceClient client;
  private WatsonIoTDeviceConnectorCallback callback;
  private WatsonIoTDeviceConnectorProcess process;
  private Thread thread;
  private LinkedBlockingQueue<Command> queue; 
	
  /**
   * Initialize this operator. Called once before any tuples are processed.
   * @param context OperatorContext for this operator.
   * @throws Exception Operator failure, will cause the enclosing PE to terminate.
   */
  @Override
	public synchronized void initialize(OperatorContext context) throws Exception {
      
      super.initialize(context);
      
      logger = Logger.getLogger(this.getClass());
      logger.debug("WatsonIoTDeviceConnector initialize() started");
      
      StreamSchema schema = context.getStreamingOutputs().get(0).newTuple().getStreamSchema();
      if (schema.getAttribute(commandNameAttribute)==null) throw new Exception("sorry, no output attribute '" + commandNameAttribute + "' found for parameter 'commandName'");
      if (schema.getAttribute(commandDataAttribute)==null) throw new Exception("sorry, no output attribute '" + commandDataAttribute + "' found for parameter 'commandData'");
      if (commandFormatAttribute!=null && schema.getAttribute(commandFormatAttribute)==null) throw new Exception("sorry, no output attribute '" + commandFormatAttribute + "' found for parameter 'commandFormat'");
      if (schema.getAttribute(commandDataAttribute).getType().getMetaType()!=Type.MetaType.RSTRING) throw new Exception("sorry, output attribute '" + commandDataAttribute + "' must be of type 'rstring' for parameter 'commandData'");
      
      // create a queue for passing commands received from applications via Watson IoT Platform from
      // the device client's callback thread to this operator's 'process' thread.
      queue = new LinkedBlockingQueue<Command>();
      
      // get an instance of a Watson IoT device client
      client = new DeviceClient(deviceCredentials);
      
      // create a callback with the Watson IoT device client that will handle
      // commands received from applications via Watson IoT Platform by enqueuing them 
      // for processing by a separate thread
      callback = new WatsonIoTDeviceConnectorCallback(queue);
      client.setCommandCallback(callback);
      
      // create a thread for processing commands received from applications via 
      // Watson IoT Platform by sending them downstream as output tuples
      process = new WatsonIoTDeviceConnectorProcess(this, queue, logger);
      thread = getOperatorContext().getThreadFactory().newThread(process);
      thread.setDaemon(false);
      
      logger.debug("WatsonIoTDeviceConnector initialize() ended");
    }


    /**
     * Start this operator.
     * @throws Exception Operator failure, will cause the enclosing PE to terminate.
     */
    @Override
      public void allPortsReady() throws Exception {

        logger.debug("WatsonIoTDeviceConnector allPortsReady() started");

        if (thread!=null) thread.start();

        if (!client.isConnected()) {
          logger.info("WatsonIoTDeviceConnector connecting to Watson IoT Platform");
          client.connect(); 
          if (!client.isConnected()) logger.error("WatsonIoTDeviceConnector failed to connect"); }

        logger.debug("WatsonIoTDeviceConnector allPortsReady() ended");
    }


    /**
     * Process an incoming tuple that arrived on the specified port.
     * <P>
     * Copy the incoming tuple to a new output tuple and submit to the output port. 
     * </P>
     * @param inputStream Port the tuple is arriving on.
     * @param tuple Object representing the incoming tuple.
     * @throws Exception Operator failure, will cause the enclosing PE to terminate.
     */
    @Override
      public final void process(StreamingInput<Tuple> inputStream, Tuple tuple) throws Exception {
      
      String name = eventNameAttribute.getValue(tuple);
      String data = eventDataAttribute.getValue(tuple);
      String format = eventFormatAttribute!=null ? eventFormatAttribute.getValue(tuple) : "json";
      logger.debug("WatsonIoTDeviceConnector sending event=" + name + ", format=" + format + ", data=" + data);          ;
      client.publishEvent(name, data, format, eventQOS);
    }
    


    /**
     * Stop this operator.
     * @throws Exception Operator failure, will cause the enclosing PE to terminate.
     */
    public synchronized void shutdown() throws Exception {
        logger.debug("WatsonIoTDeviceConnector shutdown() started");

        if (client.isConnected()) {
          logger.info("WatsonIoTDeviceConnector disconnecting from Watson IoT Platform");
          client.disconnect(); }

        process.shutdown();
        thread.interrupt();

        super.shutdown();
        logger.debug("WatsonIoTDeviceConnector shutdown() ended");
    }
}

////////////////////////////////////////////////////////////////////////////////////////////////////
