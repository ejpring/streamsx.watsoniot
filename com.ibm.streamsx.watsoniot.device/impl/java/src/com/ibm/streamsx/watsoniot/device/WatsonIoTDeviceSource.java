// Copyright (C) 2017  International Business Machines Corporation
// All Rights Reserved

package com.ibm.streamsx.watsoniot.device;

import com.ibm.iotf.client.device.DeviceClient;

import com.ibm.streams.operator.AbstractOperator;
import com.ibm.streams.operator.OperatorContext;
import com.ibm.streams.operator.OutputTuple;
import com.ibm.streams.operator.StreamSchema;
import com.ibm.streams.operator.StreamingOutput;
import com.ibm.streams.operator.Type;
import com.ibm.streams.operator.model.Libraries;
import com.ibm.streams.operator.model.OutputPortSet.WindowPunctuationOutputMode;
import com.ibm.streams.operator.model.OutputPortSet;
import com.ibm.streams.operator.model.OutputPorts;
import com.ibm.streams.operator.model.Parameter;
import com.ibm.streams.operator.model.PrimitiveOperator;

import java.io.StringReader;

import java.util.Properties;

import org.apache.log4j.Logger;

////////////////////////////////////////////////////////////////////////////////////////////////////

class WatsonIoTDeviceSourceCommand {
  public final String name;
  public final String format;
  public final byte[] data;

  public WatsonIoTDeviceSourceCommand(String name, String format, byte[] data) {
    this.name = name;
    this.format = format;
    this.data = data;
  }

  public String toString() { return "event='" + name + "', format='" + format + "', data='" + new String(data) + "'"; }
} 

////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * This class ...
 */
class WatsonIoTDeviceSourceProcess implements Runnable {

  private final WatsonIoTDeviceSource operator;
  private final WatsonIoTDeviceClientMBean client;
  private final Logger logger;
  private boolean running = true;

  /**
   * This constructor ...
   * @param operator the Streams operator
   */
  public WatsonIoTDeviceSourceProcess(WatsonIoTDeviceSource operator, WatsonIoTDeviceClientMBean client, Logger logger) {
    this.operator = operator;
    this.client = client;
    this.logger = logger;
    logger.debug("WatsonIoTDeviceSourceProcess constructor executed");
  }

  /**
   * This method dequeues and processes commands from applications via the Watson IoT Platform. It copies fields from the command into an output tuple and sends it downstream.
   */
  @Override
    public void run() {
    logger.debug("WatsonIoTDeviceSourceProcess.run() started");
    
    while (running) {
      try {
        logger.debug("WatsonIoTDeviceSourceProcess.run() waiting for event ...");
        WatsonIoTDeviceSourceCommand command = (WatsonIoTDeviceSourceCommand)client.takeCommand(); 
        if (command==null) continue;
        logger.debug("WatsonIoTDeviceSourceProcess.run() proceeding with " + command);

        StreamingOutput<OutputTuple> outputStream = operator.getOperatorContext().getStreamingOutputs().get(0);
        OutputTuple outputTuple = outputStream.newTuple();
        outputTuple.setString(operator.commandNameAttribute, command.name);
        if (operator.commandFormatAttribute!=null) outputTuple.setString(operator.commandFormatAttribute, command.format);
        outputTuple.setString(operator.commandDataAttribute, new String(command.data));
        outputStream.submit(outputTuple);
        logger.debug("WatsonIoTDeviceSourceProcess.run() submitted " + outputTuple);
      } 
      catch (Exception e) { logger.error("WatsonIoTDeviceSourceProcess caught Exception: " + e); }	
    }
    
    logger.debug("WatsonIoTDeviceSourceProcess.run() ended");
  }
  
  /**
   * This method should be called by another thread to stop the thread running the run() method above..
   *
   */
  public void shutdown() { 
    logger.debug("WatsonIoTDeviceSourceProcess.shutdown() started");
    running = false; 
    logger.debug("WatsonIoTDeviceSourceProcess.shutdown() ended");
  }

}

////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * Class for WatsonIoTDeviceSource operator, which receives commands from applications via the Watson IoT Platform and sends them downstream as output tuples to other operators.
 */

@PrimitiveOperator ( name="WatsonIoTDeviceSource", 
                     namespace="com.ibm.streamsx.watsoniot.device", 
                     description="The WatsonIoTDeviceSource operator connects an SPL graph to the Watson IoT Platform as an IoT 'device': it receives 'commands' from IoT devices and decodes them into output tuples. The operator requires a file containing 'device credentials' issued by Watson IoT Platform. The credentials must be specified as shown in the 'Using a configuration file' section of the page at 'https://console.bluemix.net/docs/services/IoT/devices/libraries/java.html'. This operator may be used together with the WatsonIoTDeviceSink operator, which sends 'events' to IoT devices. If so, the pair must specify the same credentials file, and must be fused into the same Streams PE.")

@OutputPorts ( {
	@OutputPortSet ( optional=false, 
                     cardinality=1, 
                     windowPunctuationOutputMode=WindowPunctuationOutputMode.Free,
                     description="The output port produces tuples decoded as 'commands' received from IoT applications via the Watson IoT Platform. Output tuples must at least include attributes for the command name and command data. By default, the data is assumed to be formatted as a JSON-encoded string. Optionally, output tuples may include an attribute for the data format." )
      } )

@Libraries( { "opt/*" } )

public class WatsonIoTDeviceSource extends AbstractOperator {
	
    @Parameter ( name="deviceCredentials", 
               optional=false, 
               description="the contents of a Watson IoT Platform devicecredentials file (that is, a Java Properties file containing 'key = value' pairs), with newlines replaced by commas" )
    public void setDeviceCredentials(String credentials) throws Exception { 
      this.deviceCredentials = new Properties(); 
      deviceCredentials.load(new StringReader(credentials.replace(',', '\n'))); 
      System.out.println("******************"+deviceCredentials); }
    private Properties deviceCredentials;

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
	
	
  // internal state variables for this operator
  private Logger logger;
  private WatsonIoTDeviceClientMBean client;
  private WatsonIoTDeviceSourceProcess process;
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
      logger.debug("WatsonIoTDeviceSource initialize() started");
     
      // validate the parameters that specify output attributes
      StreamSchema schema = context.getStreamingOutputs().get(0).newTuple().getStreamSchema();
      if (schema.getAttribute(commandNameAttribute)==null) throw new Exception("sorry, no output attribute '" + commandNameAttribute + "' found for parameter 'commandName'");
      if (schema.getAttribute(commandDataAttribute)==null) throw new Exception("sorry, no output attribute '" + commandDataAttribute + "' found for parameter 'commandData'");
      if (commandFormatAttribute!=null && schema.getAttribute(commandFormatAttribute)==null) throw new Exception("sorry, no output attribute '" + commandFormatAttribute + "' found for parameter 'commandFormat'");
      if (schema.getAttribute(commandDataAttribute).getType().getMetaType()!=Type.MetaType.RSTRING) throw new Exception("sorry, output attribute '" + commandDataAttribute + "' must be of type 'rstring' for parameter 'commandData'");
      
      // get an instance of a Watson IoT device client, possibly shared with a WatsonIoTDeviceSink operator.
      client = WatsonIoTDeviceClient.getClient(deviceCredentials, logger);

      // configure the client to enqueue commands to be processed in a separate thread
      client.setEnqueueCommands(WatsonIoTDeviceSourceCommand.class);

      // create a thread for processing commands received from applications via 
      // Watson IoT Platform by sending them downstream as output tuples
      process = new WatsonIoTDeviceSourceProcess(this, client, logger);
      thread = getOperatorContext().getThreadFactory().newThread(process);
      thread.setDaemon(false);
      
      logger.debug("WatsonIoTDeviceSource initialize() ended");
    }


    /**
     * Start this operator.
     * @throws Exception Operator failure, will cause the enclosing PE to terminate.
     */
    @Override
      public void allPortsReady() throws Exception {

        logger.debug("WatsonIoTDeviceSource.allPortsReady() started");

        thread.start();

        if (!client.isConnected()) {
          logger.info("WatsonIoTDeviceSource connecting to Watson IoT Platform");
          client.connect(); 
          if (!client.isConnected()) logger.error("WatsonIoTDeviceSource failed to connect"); }

        logger.debug("WatsonIoTDeviceSource.allPortsReady() ended");
    }


    /**
     * Stop this operator.
     * @throws Exception Operator failure, will cause the enclosing PE to terminate.
     */
    public synchronized void shutdown() throws Exception {
        logger.debug("WatsonIoTDeviceSource.shutdown() started");

        if (client.isConnected()) {
          logger.info("WatsonIoTDeviceSource disconnecting from Watson IoT Platform");
          client.disconnect(); }

        process.shutdown();
        thread.interrupt();

        super.shutdown();
        logger.debug("WatsonIoTDeviceSource.shutdown() ended");
    }
}

////////////////////////////////////////////////////////////////////////////////////////////////////
