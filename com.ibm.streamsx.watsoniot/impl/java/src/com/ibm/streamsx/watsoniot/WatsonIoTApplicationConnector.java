
package com.ibm.streamsx.watsoniot;

import java.io.File;

import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.log4j.Logger;

import com.ibm.iotf.client.app.Event;
import com.ibm.iotf.client.app.Command;
import com.ibm.iotf.client.app.EventCallback;
import com.ibm.iotf.client.app.ApplicationClient;

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
 * This class handles events received from devices via the Watson IoT Platform
 */
class WatsonIoTEventCallback implements EventCallback, Runnable {

  private WatsonIoTApplicationConnector operator;
  private Logger logger;
  private BlockingQueue<Event> queue = new LinkedBlockingQueue<Event>();
  private StreamingOutput<OutputTuple> outputStream;
  private OutputTuple outputTuple;
  private Thread thread;
  private boolean running = true;
	
    /**
     * This constructor initializes the class's state variables and starting its run() method on a separate thread.
     * @param operator the Streams operator 
     */
	public WatsonIoTEventCallback(WatsonIoTApplicationConnector operator) {
		
	  	this.operator = operator; 
        logger = Logger.getLogger(operator.getOperatorContext().getClass());
        logger.info("WatsonIoTEventCallback constructor executed");

        outputStream = operator.getOperatorContext().getStreamingOutputs().get(0);
        outputTuple = outputStream.newTuple();

        thread = new Thread(this);
        thread.start();
	}
	

    /**
     * This method enqueues events received from devices via the Watson IoT Platform. It enqueues the event for processing on a separate thread below.
     * @param event event received from Watson IoT Platform 
     */
    @Override
    public void processEvent(Event event) {
      try { queue.put(event); } catch (InterruptedException e) {}
    }

    
    /**
     * This method receives and discards commands sent to devices by other applications via the Watson IoT Platform. It enqueues the event for processing on a separate thread below.
     * @param event event received from Watson IoT Platform 
     */
    @Override
    public void processCommand(Command command) {}


    /**
     * This method should be called by another thread to stop the thread running the run() method below.
     *
     */
  public void shutdown() { running = false; thread.interrupt(); }
    

  /**
   * This method dequeues and processes events from devices via the Watson IoT Platform. It copies fields from the event into an output tuple and sends it downstream.
   */
  @Override
    public void run() {
    logger.info("WatsonIoTEventCallback thread started");
    while(running) {
      try {
        Event event = queue.take();
        String name = event.getEvent();
        String format = event.getFormat();
        String data = new String(event.getRawPayload());
        String deviceType = event.getDeviceType();
        String deviceId = event.getDeviceId();
        logger.info("WatsonIoTEventCallback received event='" + name + "', format='" + format + "', data='" + data + "', deviceType='" + deviceType + "', deviceId='" + deviceId + "'"); 

        outputTuple.setString(operator.eventNameAttribute, name);
        outputTuple.setString(operator.eventDataAttribute, data);
        if (operator.eventFormatAttribute!=null) outputTuple.setString(operator.eventFormatAttribute, format);
        if (operator.eventDeviceTypeAttribute!=null) outputTuple.setString(operator.eventDeviceTypeAttribute, deviceType);
        if (operator.eventDeviceIdAttribute!=null) outputTuple.setString(operator.eventDeviceIdAttribute, deviceId);
        outputStream.submit(outputTuple);
      } 
      catch (InterruptedException e) {}
      catch (Exception e) { logger.info("WatsonIoTEventCallback Exception: " + e); }	
    }
    logger.info("WatsonIoTEventCallback thread ended");
  }
}

////////////////////////////////////////////////////////////////////////////////////////////////////









/**
 * Class for an operator that receives a tuple and then optionally submits a tuple. 
 * This pattern supports one or more input streams and one or more output streams. 
 * <P>
 * The following event methods from the Operator interface can be called:
 * </p>
 * <ul>
 * <li><code>initialize()</code> to perform operator initialization</li>
 * <li>allPortsReady() notification indicates the operator's ports are ready to process and submit tuples</li> 
 * <li>process() handles a tuple arriving on an input port 
 * <li>processPuncuation() handles a punctuation mark arriving on an input port 
 * <li>shutdown() to shutdown the operator. A shutdown request may occur at any time, 
 * such as a request to stop a PE or cancel a job. 
 * Thus the shutdown() may occur while the operator is processing tuples, punctuation marks, 
 * or even during port ready notification.</li>
 * </ul>
 * <p>With the exception of operator initialization, all the other events may occur concurrently with each other, 
 * which lead to these methods being called concurrently by different threads.</p> 
 */

/**
 * Class for WatsonIoTApplicationConnector operator, which: 
 * <ul>
 * <li>recieves tuples from upstream operators and sends them as commands to devices via the Watson IoT Platform</li>
 * <li>receives events from devices via the Watson IoT Platform and sends them downstream as tuples to other operators</li>
 * </ul>
 */

@PrimitiveOperator ( name="WatsonIoTApplicationConnector", 
                     namespace="com.ibm.streamsx.watsoniot", 
                     description="connects an SPL data flow graph to the Watson IoT Platform as an application that receives events from devices and sends commands to them")
@InputPorts ( { 
	@InputPortSet ( optional=false, 
                    cardinality=1, 
                    windowingMode=WindowMode.NonWindowed, 
                    windowPunctuationInputMode=WindowPunctuationInputMode.Oblivious,
                    description="input port for tuples to be sent as commands to devices via the Watson IoT Platform" )
      } )

@OutputPorts ( {
	@OutputPortSet ( optional=false, 
                     cardinality=1, 
                     windowPunctuationOutputMode=WindowPunctuationOutputMode.Free,
                     description="output port for tuples received as events from devices via the Watson IoT Platform" )
      } )

@Libraries( { "opt/*" } )


public class WatsonIoTApplicationConnector extends AbstractOperator {
	
  @Parameter ( name="applicationCredentials", 
               optional=false, 
               //cardinality=1, 
               description="the name of a file containing Watson IoT Platform application credentials" )
  public void setApplicationCredentials(String filename) { this.applicationCredentials = ApplicationClient.parsePropertiesFile(new File(filename)); }
  private Properties applicationCredentials;
  
	@Parameter ( name="commandName", 
                 optional=false, 
                 //cardinality=1, 
                 description="an input attribute that will be sent to the Watson IoT Platform as the command name" )
    public void setCommandName(TupleAttribute<Tuple,String> attribute) { this.commandNameAttribute = attribute; }
	private TupleAttribute<Tuple,String> commandNameAttribute; 

	@Parameter ( name="commandData", 
                 optional=false, 
                 //cardinality=1, 
                 description="an input attribute of type 'rstring' that will be sent to the Watson IoT Platform as command data" )
	public void setCommandData(TupleAttribute<Tuple,String> attribute) { this.commandDataAttribute = attribute; }
	private TupleAttribute<Tuple,String> commandDataAttribute; 

	@Parameter ( name="commandFormat", 
                 optional=true, 
                 //cardinality=1, 
                 description="an input attribute of type 'rstring' that specifies the format of the data sent to the Watson IoT Platform, defaulting to 'json' if not specified" )
	public void setCommandFormat(TupleAttribute<Tuple,String> attribute) { this.commandFormatAttribute = attribute; }
	private TupleAttribute<Tuple,String> commandFormatAttribute = null;

	@Parameter ( name="commandQOS", 
                 optional=true, 
                 //cardinality=1, 
                 description="the 'quality of service' for commmands sent to the Watson IoT Platform, either '0' or '1' or '2', defaulting to '2' if not specified" ) 
	public void setCommandQOS(int value) { this.commandQOS = value; }
	private int commandQOS = 2;

	@Parameter ( name="commandDeviceType", 
                 optional=false, 
                 //cardinality=1, 
                 description="an input attribute of type 'rstring' that specifies the type of device the Watson IoT Platform should send the command to, defaulting to '??????????' if not specified" )
	public void setCommandDeviceType(TupleAttribute<Tuple,String> attribute) { this.commandDeviceTypeAttribute = attribute; }
	private TupleAttribute<Tuple,String> commandDeviceTypeAttribute;

	@Parameter ( name="commandDeviceId", 
                 optional=false, 
                 //cardinality=1, 
                 description="an input attribute of type 'rstring' that identfies the device the Watson IoT Platform should send the command to, defaulting to '??????????' if not specified" )
	public void setCommandDeviceId(TupleAttribute<Tuple,String> attribute) { this.commandDeviceIdAttribute = attribute; }
	private TupleAttribute<Tuple,String> commandDeviceIdAttribute;

  @Parameter ( name="subscriptionDeviceTypes", 
               optional=true, 
               //cardinality=1, 
               description="output tuples will be produced from events received from these device types, defaulting to '+', meaning all device types" )
  public void setSubscriptionDeviceTypes(String[] subscriptions) { subscriptionDeviceTypes = subscriptions; }
  private String[] subscriptionDeviceTypes = { "+" };

  @Parameter ( name="subscriptionDeviceIds", 
               optional=true, 
               //cardinality=1, 
               description="output tuples will be produced from events received from these devices, defaulting to '+', meaning all devices" )
  public void setSubscriptionDeviceIds(String[] subscriptions) { subscriptionDeviceIds = subscriptions; }
  private String[] subscriptionDeviceIds = { "+" };

  @Parameter ( name="subscriptionEvents", 
               optional=true, 
               //cardinality=1, 
               description="output tuples will be produced from these events, defaulting to '+', meaning all events" )
  public void setSubscriptionEvents(String[] subscriptions) { subscriptionEvents = subscriptions; }
  private String[] subscriptionEvents = { "+" };

  @Parameter ( name="subscriptionFormats", 
               optional=true, 
               //cardinality=1, 
               description="output tuples will be produced from events received in these formats, defaulting to '+', meaning all formats" )
  public void setSubscriptionFormats(String[] subscriptions) { subscriptionFormats = subscriptions; }
  private String[] subscriptionFormats = { "+" };

	@Parameter ( name="eventName", 
                 optional=false, 
                 //cardinality=1, 
                 description="an output attribute of type 'rstring' for the name of the event recieved from a device via the Watson IoT Platform" )
	public void setEventName(String attribute) { this.eventNameAttribute = attribute; }
	public String eventNameAttribute;
	
	@Parameter ( name="eventData", 
                 optional=false, 
                 //cardinality=1, 
                 description="an output attribute of type 'rstring' for data recieved with an event from a device via the Watson IoT Platform" )
	public void setEventData(String attribute) { this.eventDataAttribute = attribute; }
	public String eventDataAttribute;
	
	@Parameter ( name="eventFormat", 
                 optional=true, 
                 //cardinality=1, 
                 description="optionally, an output attribute of type 'rstring' for the format of the data recieved with an event from a device via the Watson IoT Platform, with no default" )
	public void setEventFormat(String attribute) { this.eventFormatAttribute = attribute; }
	public String eventFormatAttribute = null;

	@Parameter ( name="eventDeviceType", 
                 optional=true, 
                 //cardinality=1, 
                 description="optionally, an output attribute of type 'rstring' for the type of the device that sent the event recieved via the Watson IoT Platform, with no default" )
	public void setEventDeviceType(String attribute) { this.eventDeviceTypeAttribute = attribute; }
	public String eventDeviceTypeAttribute = null;
	
	@Parameter ( name="eventDeviceId", 
                 optional=true, 
                 //cardinality=1, 
                 description="optionally, an output attribute of type 'rstring' for the identifier of the device that sent the event received from the Watson IoT Platform, with no default" )
	public void setEventDeviceId(String attribute) { this.eventDeviceIdAttribute = attribute; }
	public String eventDeviceIdAttribute = null;
	
	
	private OperatorContext context;
	private Logger logger;
	private ApplicationClient client;
    private WatsonIoTEventCallback callback;


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
        logger.info("WatsonIoTApplicationConnector initializing");

        StreamSchema schema = context.getStreamingOutputs().get(0).newTuple().getStreamSchema();
        if (schema.getAttribute(eventNameAttribute)==null) throw new Exception("sorry, no output attribute '" + eventNameAttribute + "' found for parameter 'eventName'");
        if (schema.getAttribute(eventDataAttribute)==null) throw new Exception("sorry, no output attribute '" + eventDataAttribute + "' found for parameter 'eventData'");
        if (eventFormatAttribute!=null && schema.getAttribute(eventFormatAttribute)==null) throw new Exception("sorry, no output attribute '" + eventFormatAttribute + "' found for parameter 'eventFormat'");
        if (eventDeviceTypeAttribute!=null && schema.getAttribute(eventDeviceTypeAttribute)==null) throw new Exception("sorry, no output attribute '" + eventDeviceTypeAttribute + "' found for parameter 'eventDeviceType'");
        if (eventDeviceIdAttribute!=null && schema.getAttribute(eventDeviceIdAttribute)==null) throw new Exception("sorry, no output attribute '" + eventDeviceIdAttribute + "' found for parameter 'eventDeviceId'");

        callback = new WatsonIoTEventCallback(this);
        client = new ApplicationClient(applicationCredentials);
        client.setEventCallback(callback);
        client.connect();

        for (String deviceType: subscriptionDeviceTypes) {
          for (String deviceId: subscriptionDeviceIds) {
            for (String event: subscriptionEvents) {
              for (String format: subscriptionFormats) {
                logger.info("WatsonIoTApplicationConnector subscribing to deviceType='" + deviceType + "', deviceId='" + deviceId + "', event='" + event + "', format='" + format + "'");
                client.subscribeToDeviceEvents(deviceType, deviceId, event, format); } } } }
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

      String type = commandDeviceTypeAttribute.getValue(tuple);
      String id = commandDeviceIdAttribute.getValue(tuple);
      String name = commandNameAttribute.getValue(tuple);
      String data = commandDataAttribute.getValue(tuple);
      String format = commandFormatAttribute!=null ? commandFormatAttribute.getValue(tuple) : "json";
      logger.info("WatsonIoTApplicationConnector sending command='" + name + "', format='" + format + "', data='" + data + "', deviceType='" + type + "', deviceId='" + id + "'");          ;
        
      boolean success = client.publishCommand(type, id, name, data, format, commandQOS);
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
        logger.info("WatsonIoTApplicationConnector shutdown");
        callback.shutdown();
        client.disconnect();
        super.shutdown();
    }

}

////////////////////////////////////////////////////////////////////////////////////////////////////
