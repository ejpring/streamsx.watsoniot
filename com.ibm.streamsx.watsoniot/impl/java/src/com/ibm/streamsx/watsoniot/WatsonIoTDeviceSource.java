
package com.ibm.streamsx.watsoniot;

import java.io.File;

import java.util.Properties;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.log4j.Logger;

import com.ibm.iotf.client.device.Command;
import com.ibm.iotf.client.device.CommandCallback;
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

////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * This class handles commands received from applications via the Watson IoT Platform by
 * enqueuing them for the 'process' thread below to emit as output tuples.
 */
class WatsonIoTDeviceSourceCallback implements CommandCallback {

  private final LinkedBlockingQueue<Command> queue; 
  
  /**
   * This constructor saves the queue commands will be put into as they are received.
   * @param queue the queue for commands received from Watson IoT Platform
   */
  public WatsonIoTDeviceSourceCallback(LinkedBlockingQueue<Command> queue) {
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
class WatsonIoTDeviceSourceProcess implements Runnable {

  private final WatsonIoTDeviceSource operator;
  private final LinkedBlockingQueue<Command> queue; 
  private final Logger logger;
  private boolean running = true;

  /**
   * This constructor ...
   * @param operator the Streams operator
   */
  public WatsonIoTDeviceSourceProcess(WatsonIoTDeviceSource operator, LinkedBlockingQueue<Command> queue, Logger logger) {
	
    this.operator = operator;
    this.queue = queue;
    this.logger = logger;
    logger.info("WatsonIoTDeviceSourceProcess constructor executed");
  }

  /**
   * This method dequeues and processes commands from applications via the Watson IoT Platform. It copies fields from the command into an output tuple and sends it downstream.
   */
  @Override
    public void run() {
    logger.info("WatsonIoTDeviceSourceProcess run() started");
    
    while (running) {
      try {
        Command command = queue.take();
        String name = command.getCommand();
        String format = command.getFormat();
        String data = new String(command.getRawPayload());
        logger.info("WatsonIoTDeviceSourceProcess received command=" + name + ", format=" + format + ", data=" + data);          ;
        
        StreamingOutput<OutputTuple> outputStream = operator.getOperatorContext().getStreamingOutputs().get(0);
        OutputTuple outputTuple = outputStream.newTuple();
        outputTuple.setString(operator.commandNameAttribute, name);
        outputTuple.setString(operator.commandDataAttribute, data);
        if (operator.commandFormatAttribute!=null) outputTuple.setString(operator.commandFormatAttribute, format);
        outputStream.submit(outputTuple);
      } 
      catch (InterruptedException e) { logger.info("WatsonIoTDeviceSourceProcess caught InterruptedException: " + e); }
      catch (Exception e) { logger.info("WatsonIoTDeviceSourceProcess caught Exception: " + e); }	
    }
    
    logger.info("WatsonIoTDeviceSourceProcess run() ended");
  }
  
  /**
   * This method should be called by another thread to stop the thread running the run() method above..
   *
   */
  public void shutdown() { 
    logger.info("WatsonIoTDeviceSourceProcess shutdown() started");
    running = false; 
    logger.info("WatsonIoTDeviceSourceProcess shutdown() ended");
  }

}

////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * Class for WatsonIoTDeviceSource operator, which receives commands from applications via the Watson IoT Platform and sends them downstream as output tuples to other operators.
 */

@PrimitiveOperator ( name="WatsonIoTDeviceSource", 
                     namespace="com.ibm.streamsx.watsoniot", 
                     description="connects an SPL data flow graph to the Watson IoT Platform as a device that receives commands from applications via the Watson IoT Platform.")

@OutputPorts ( {
	@OutputPortSet ( optional=false, 
                     cardinality=1, 
                     windowPunctuationOutputMode=WindowPunctuationOutputMode.Free,
                     description="output port for tuples received as commands from applications via the Watson IoT Platform" )
      } )

@Libraries( { "opt/*" } )

public class WatsonIoTDeviceSource extends AbstractOperator {
	
	@Parameter ( name="deviceCredentials", 
                 optional=false, 
                 description="the name of a file containing Watson IoT Platform device credentials" )
	public void setDeviceCredentials(String filename) { this.deviceCredentials = DeviceClient.parsePropertiesFile(new File(filename)); }
	private Properties deviceCredentials;

	@Parameter ( name="commandName", 
                 optional=false, 
                 description="an output attribute of type 'rstring' for the name of the command recieved from the Watson IoT Platform" )
	public void setCommandName(String attribute) { this.commandNameAttribute = attribute; }
	public String commandNameAttribute;
	
	@Parameter ( name="commandData", 
                 optional=false, 
                 description="an output attribute of type 'rstring' or 'blob' for data recieved from the Watson IoT Platform with a command" )
	public void setCommandData(String attribute) { this.commandDataAttribute = attribute; }
	public String commandDataAttribute;
	
	@Parameter ( name="comandFormat", 
                 optional=true, 
                 description="optionally, an output attribute of type 'rstring' for the format of the data recieved from the Watson IoT Platform with a command, with no default" )
	public void setCommandFormat(String attribute) { this.commandFormatAttribute = attribute; }
	public String commandFormatAttribute = null;
	
	
  // internal state variables or this operator
  private Logger logger;
  private WatsonIoTDeviceClientMBean client;
  private WatsonIoTDeviceSourceCallback callback;
  private WatsonIoTDeviceSourceProcess process;
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
      logger.info("WatsonIoTDeviceSource initialize() started");
     
      // validate the parameters that specify output attributes
      StreamSchema schema = context.getStreamingOutputs().get(0).newTuple().getStreamSchema();
      if (schema.getAttribute(commandNameAttribute)==null) throw new Exception("sorry, no output attribute '" + commandNameAttribute + "' found for parameter 'commandName'");
      if (schema.getAttribute(commandDataAttribute)==null) throw new Exception("sorry, no output attribute '" + commandDataAttribute + "' found for parameter 'commandData'");
      if (commandFormatAttribute!=null && schema.getAttribute(commandFormatAttribute)==null) throw new Exception("sorry, no output attribute '" + commandFormatAttribute + "' found for parameter 'commandFormat'");
      if (schema.getAttribute(commandDataAttribute).getType().getMetaType()!=Type.MetaType.RSTRING) throw new Exception("sorry, output attribute '" + commandDataAttribute + "' must be of type 'rstring' for parameter 'commandData'");
      
      // create a queue for passing commands received from applications via Watson IoT Platform from
      // the device client's callback thread to this operator's 'process' thread.
      queue = new LinkedBlockingQueue<Command>();
      
      // get an instance of a Watson IoT device client, possibly shared with a WatsonIoTDeviceSink operator.
      client = WatsonIoTDeviceClient.getClient(deviceCredentials, logger);
      
      // create a callback with the Watson IoT device client that will handle
      // commands recieved from applications via Watson IoT Platform by enqueuing them 
      // for processing by a separate thread
      callback = new WatsonIoTDeviceSourceCallback(queue);
      client.setCommandCallback(callback);
      
      // create a thread for processing commands recieved from applications via 
      // Watson IoT Platform by sending them downstream as output tuples
      process = new WatsonIoTDeviceSourceProcess(this, queue, logger);
      thread = getOperatorContext().getThreadFactory().newThread(process);
      thread.setDaemon(false);
      
      logger.info("WatsonIoTDeviceSource initialize() ended");
    }


    /**
     * Start this operator.
     * @throws Exception Operator failure, will cause the enclosing PE to terminate.
     */
    @Override
      public void allPortsReady() throws Exception {

        logger.info("WatsonIoTDeviceSource allPortsReady() started");

        if (thread!=null) thread.start();

        if (!client.isConnected()) {
          logger.info("WatsonIoTDeviceSource connecting to Watson IoT Platform");
          client.connect(); 
          if (!client.isConnected()) logger.error("WatsonIoTDeviceSource failed to connect"); }

        logger.info("WatsonIoTDeviceSource allPortsReady() ended");
    }


    /**
     * Stop this operator.
     * @throws Exception Operator failure, will cause the enclosing PE to terminate.
     */
    public synchronized void shutdown() throws Exception {
        logger.info("WatsonIoTDeviceSource shutdown() started");

        if (client.isConnected()) {
          logger.info("WatsonIoTDeviceSource disconnecting from Watson IoT Platform");
          client.disconnect(); }

        process.shutdown();
        thread.interrupt();

        super.shutdown();
        logger.info("WatsonIoTDeviceSource shutdown() ended");
    }
}

////////////////////////////////////////////////////////////////////////////////////////////////////
