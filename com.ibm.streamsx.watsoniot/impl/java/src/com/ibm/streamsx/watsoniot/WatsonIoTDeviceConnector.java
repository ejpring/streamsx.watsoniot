
package com.ibm.streamsx.watsoniot;

import java.io.File;

import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.log4j.Logger;

import com.ibm.iotf.client.device.Command;
import com.ibm.iotf.client.device.CommandCallback;
import com.ibm.iotf.client.device.DeviceClient;

import com.ibm.streams.operator.AbstractOperator;
import com.ibm.streams.operator.OperatorContext;
import com.ibm.streams.operator.OutputTuple;
import com.ibm.streams.operator.StreamSchema;
import com.ibm.streams.operator.StreamingData.Punctuation;
import com.ibm.streams.operator.StreamingInput;
import com.ibm.streams.operator.StreamingOutput;
import com.ibm.streams.operator.Tuple;
import com.ibm.streams.operator.TupleAttribute;
import com.ibm.streams.operator.Type;
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
 * This class handles commands received from applications via the Watson IoT Platform
 */
class WatsonIoTCommandCallback implements CommandCallback, Runnable {

  private WatsonIoTDeviceConnector operator;
  private Logger logger;
  private BlockingQueue<Command> queue = new LinkedBlockingQueue<Command>();
  private StreamingOutput<OutputTuple> outputStream;
  private OutputTuple outputTuple;
  private Thread thread;
  private boolean running = true;
	
    /**
     * This constructor initializes the class's state variables and starting its run() method on a separate thread.
     * @param operator the Streams operator
     */
  public WatsonIoTCommandCallback(WatsonIoTDeviceConnector operator) {
		
	  	this.operator = operator; 
        logger = Logger.getLogger(operator.getOperatorContext().getClass());
        logger.info("WatsonIoTCommandCallback constructor executed");
		
        outputStream = operator.getOperatorContext().getStreamingOutputs().get(0);
        outputTuple = outputStream.newTuple();

        thread = new Thread(this);
        thread.start();
	}
	

    /**
     * This method enqueues commands received from applications via the Watson IoT Platform. It enqueues the command for processing on a separate thread below.
     * @param command command received from Watson IoT Platform 
     */
    @Override
    public void processCommand(Command command) {
        try { queue.put(command); } catch (InterruptedException e) {}
    }


    /**
     * This method should be called by another thread to stop the thread running the run() method below.
     *
     */
     public void shutdown() { running = false; thread.interrupt(); }

    /**
     * This method dequeues and processes commands from applications via the Watson IoT Platform. It copies fields from the command into an output tuple and sends it downstream.
     */
    @Override
    public void run() {
      logger.info("WatsonIoTCommandCallback thread started");
      while(running) {
        try {
          Command command = queue.take();
          String name = command.getCommand();
          String format = command.getFormat();
          String data = new String(command.getRawPayload());
          logger.info("WatsonIoTCommandCallback received command=" + name + ", format=" + format + ", data=" + data);          ;
          
          outputTuple.setString(operator.commandNameAttribute, name);
          outputTuple.setString(operator.commandDataAttribute, data);
          if (operator.commandFormatAttribute!=null) outputTuple.setString(operator.commandFormatAttribute, format);
          outputStream.submit(outputTuple);
        } 
        catch (InterruptedException e) {}
        catch (Exception e) { logger.info("WatsonIoTEventCommandback Exception: " + e); }	
      }
      logger.info("WatsonIoTCommandCallback thread ended");
    }
}


/**
 * Class for WatsonIoTDeviceConnector operator, which: 
 * <ul>
 * <li>recieves tuples from upstream operators and sends them as events to applications via the Watson IoT Platform</li>
 * <li>receives commands from applications via the Watson IoT Platform and sends them downstream as tuples to other operators.</li>
 * </ul>
 */

@PrimitiveOperator ( name="WatsonIoTDeviceConnector", 
                     namespace="com.ibm.streamsx.watsoniot", 
                     description="connects an SPL data flow graph to the Watson IoT Platform as a device that sends events to applications and receives commands from them")
@InputPorts ( { 
	@InputPortSet ( optional=false, 
                    cardinality=1, 
                    windowingMode=WindowMode.NonWindowed, 
                    windowPunctuationInputMode=WindowPunctuationInputMode.Oblivious,
                    description="input port for tuples to be sent as events to applications via the Watson IoT Platform" )
      } )

@OutputPorts ( {
	@OutputPortSet ( optional=false, 
                     cardinality=1, 
                     windowPunctuationOutputMode=WindowPunctuationOutputMode.Free,
                     description="output port for tuples received as commands from applications via the Watson IoT Platform" )
      } )

@Libraries( { "opt/*" } )

public class WatsonIoTDeviceConnector extends AbstractOperator {
	
	@Parameter ( name="deviceCredentials", 
                 optional=false, 
                 /////////////cardinality=1, 
                 description="the name of a file containing Watson IoT Platform device credentials" )
	public void setDeviceCredentials(String filename) { this.deviceCredentials = DeviceClient.parsePropertiesFile(new File(filename)); }
	private Properties deviceCredentials;

	@Parameter ( name="eventName", 
                 optional=false, 
                 //cardinality=1, 
                 description="an input attribute that will be sent to the Watson IoT Platform as the event name" )
	public void setEventName(TupleAttribute<Tuple,String> attribute) { this.eventNameAttribute = attribute; }
	private TupleAttribute<Tuple,String> eventNameAttribute; 

	@Parameter ( name="eventData", 
                 optional=false, 
                 //cardinality=1, 
                 description="an input attribute of type 'rstring' that will be sent to the Watson IoT Platform as event data" )
	public void setEventData(TupleAttribute<Tuple,String> attribute) { this.eventDataAttribute = attribute; }
	private TupleAttribute<Tuple,String> eventDataAttribute; 

	@Parameter ( name="eventFormat", 
                 optional=true, 
                 //cardinality=1, 
                 description="an input attribute of type 'rstring' that specifies the format of the data sent to the Watson IoT Platform, defaulting to 'json' if not specified" )
	public void setEventFormat(TupleAttribute<Tuple,String> attribute) { this.eventFormatAttribute = attribute; }
	private TupleAttribute<Tuple,String> eventFormatAttribute = null;
	
	@Parameter ( name="eventQOS", 
                 optional=true, 
                 //cardinality=1, 
                 description="the 'quality of service' for events sent to the Watson IoT Platform, either '0' or '1' or '2', defaulting to '0' if not specified" ) 
	public void setEventQOS(int value) { this.eventQOS = value; }
	private int eventQOS = 0;
	
	@Parameter ( name="commandName", 
                 optional=false, 
                 //cardinality=1, 
                 description="an output attribute of type 'rstring' for the name of the command recieved from the Watson IoT Platform" )
	public void setCommandName(String attribute) { this.commandNameAttribute = attribute; }
	public String commandNameAttribute;
	
	@Parameter ( name="commandData", 
                 optional=false, 
                 //cardinality=1, 
                 description="an output attribute of type 'rstring' or 'blob' for data recieved from the Watson IoT Platform with a command" )
	public void setCommandData(String attribute) { this.commandDataAttribute = attribute; }
	public String commandDataAttribute;
	
	@Parameter ( name="comandFormat", 
                 optional=true, 
                 //cardinality=1, 
                 description="optionally, an output attribute of type 'rstring' for the format of the data recieved from the Watson IoT Platform with a command, with no default" )
	public void setCommandFormat(String attribute) { this.commandFormatAttribute = attribute; }
	public String commandFormatAttribute = null;
	
	

	private OperatorContext context;
	private Logger logger;
	private DeviceClient client;
    private WatsonIoTCommandCallback callback;
	
    /**
     * Initialize this operator. Called once before any tuples are processed.
     * @param context OperatorContext for this operator.
     * @throws Exception Operator failure, will cause the enclosing PE to terminate.
     */
	@Override
	public synchronized void initialize(OperatorContext context) throws Exception {
    	
		this.context = context;
		super.initialize(context);
		
        logger = Logger.getLogger(this.getClass());
        logger.info("WatsonIoTDeviceConnector initializing");

        StreamSchema schema = context.getStreamingOutputs().get(0).newTuple().getStreamSchema();
        if (schema.getAttribute(commandNameAttribute)==null) throw new Exception("sorry, no output attribute '" + commandNameAttribute + "' found for parameter 'commandName'");
        if (schema.getAttribute(commandDataAttribute)==null) throw new Exception("sorry, no output attribute '" + commandDataAttribute + "' found for parameter 'commandData'");
        if (commandFormatAttribute!=null && schema.getAttribute(commandFormatAttribute)==null) throw new Exception("sorry, no output attribute '" + commandFormatAttribute + "' found for parameter 'commandFormat'");
        if (schema.getAttribute(commandDataAttribute).getType().getMetaType()!=Type.MetaType.RSTRING) throw new Exception("sorry, output attribute '" + commandDataAttribute + "' must be of type 'rstring' for parameter 'commandData'");
        
        callback = new WatsonIoTCommandCallback(this);
        client = new DeviceClient(deviceCredentials);
        client.setCommandCallback(callback);
        client.connect();
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
      logger.info("WatsonIoTDeviceConnector sending event=" + name + ", format=" + format + ", data=" + data);          ;
      boolean success = client.publishEvent(name, data, format, eventQOS);
    }
    


    /**
     * Process an incoming punctuation that arrived on the specified port.
     * @param stream Port the punctuation is arriving on.
     * @param mark The punctuation mark
     * @throws Exception Operator failure, will cause the enclosing PE to terminate.
     */
    @Override
    public void processPunctuation(StreamingInput<Tuple> stream, Punctuation mark) throws Exception {}



    /**
     * Shutdown this operator.
     * @throws Exception Operator failure, will cause the enclosing PE to terminate.
     */
    public synchronized void shutdown() throws Exception {
        logger.info("WatsonIoTDeviceConnector shutdown");
        callback.shutdown();
        client.disconnect();
        super.shutdown();
    }
}
