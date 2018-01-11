
package com.ibm.streamsx.watsoniot;

import java.io.File;

import java.util.Properties;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.log4j.Logger;

import com.ibm.iotf.client.app.Event;
import com.ibm.iotf.client.app.Command;
import com.ibm.iotf.client.app.EventCallback;
import com.ibm.iotf.client.app.ApplicationClient;

import com.ibm.streams.operator.AbstractOperator;
import com.ibm.streams.operator.OperatorContext;
import com.ibm.streams.operator.StreamSchema;
import com.ibm.streams.operator.StreamingData.Punctuation;
import com.ibm.streams.operator.StreamingInput;
import com.ibm.streams.operator.Tuple;
import com.ibm.streams.operator.TupleAttribute;
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
import com.ibm.streams.operator.samples.patterns.TupleProducer;

////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * This class handles events received from devices via the Watson IoT Platform
 */

class WatsonIoTApplicationSourceCallback implements EventCallback, Runnable {

  private WatsonIoTApplicationSource operator;
  private Logger logger;
  private LinkedBlockingQueue<Event> queue = new LinkedBlockingQueue<Event>();
  private StreamingOutput<OutputTuple> outputStream;
  private OutputTuple outputTuple;
  private Thread thread;
  private boolean running = true;
	
    /**
     * This constructor initializes the class's state variables and starting its run() method on a separate thread.
     * @param operator the Streams operator 
     */
	public WatsonIoTApplicationSourceCallback(WatsonIoTApplicationSource operator) {
		
	  	this.operator = operator; 
        logger = Logger.getLogger(operator.getOperatorContext().getClass());
        logger.info("WatsonIoTApplicationSourceCallback constructor executed");

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
    logger.info("WatsonIoTApplicationSourceCallback thread started");
    while(running) {
      try {
        Event event = queue.take();
        String name = event.getEvent();
        String format = event.getFormat();
        String data = new String(event.getRawPayload());
        String deviceType = event.getDeviceType();
        String deviceId = event.getDeviceId();
        logger.info("WatsonIoTApplicationSourceCallback received event='" + name + "', format='" + format + "', data='" + data + "', deviceType='" + deviceType + "', deviceId='" + deviceId + "'"); 

        outputTuple.setString(operator.eventNameAttribute, name);
        outputTuple.setString(operator.eventDataAttribute, data);
        if (operator.eventFormatAttribute!=null) outputTuple.setString(operator.eventFormatAttribute, format);
        if (operator.eventDeviceTypeAttribute!=null) outputTuple.setString(operator.eventDeviceTypeAttribute, deviceType);
        if (operator.eventDeviceIdAttribute!=null) outputTuple.setString(operator.eventDeviceIdAttribute, deviceId);
        outputStream.submit(outputTuple);
      } 
      catch (InterruptedException e) { logger.info("WatsonIoTApplicationSourceCallback InterruptedException: " + e); }
      catch (Exception e) { logger.info("WatsonIoTApplicationSourceCallback Exception: " + e); }	
    }
    logger.info("WatsonIoTApplicationSourceCallback thread ended");
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
                     namespace="com.ibm.streamsx.watsoniot", 
                     description="connects an SPL data flow graph to the Watson IoT Platform as an application that receives events from devices and sends commands to them")

@OutputPorts ( {
	@OutputPortSet ( optional=false, 
                     cardinality=1, 
                     windowPunctuationOutputMode=WindowPunctuationOutputMode.Free,
                     description="output port for tuples received as events from devices via the Watson IoT Platform" )
      } )

@Libraries( { "opt/*" } )


///////////////public class WatsonIoTApplicationSource extends AbstractOperator {
public class WatsonIoTApplicationSource extends TupleProducer {
	
  @Parameter ( name="applicationCredentials", 
               optional=false, 
               //cardinality=1, 
               description="the name of a file containing Watson IoT Platform application credentials" )
  public void setApplicationCredentials(String filename) { this.applicationCredentials = ApplicationClient.parsePropertiesFile(new File(filename)); }
  private Properties applicationCredentials;
  
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
	
  // internal state variables or this operator	
	private Logger logger;
	private WatsonIoTApplicationClient client;
    private WatsonIoTApplicationSourceCallback callback;


    /**
     * Initialize this operator. Called once before any tuples are processed.
     * @param context OperatorContext for this operator.
     * @throws Exception Operator failure, will cause the enclosing PE to terminate.
     */
	@Override
	public synchronized void initialize(OperatorContext context) throws Exception {
    	
		super.initialize(context);

        logger = Logger.getLogger(this.getClass());
        logger.info("WatsonIoTApplicationSource initializing");

        StreamSchema schema = context.getStreamingOutputs().get(0).newTuple().getStreamSchema();
        if (schema.getAttribute(eventNameAttribute)==null) throw new Exception("sorry, no output attribute '" + eventNameAttribute + "' found for parameter 'eventName'");
        if (schema.getAttribute(eventDataAttribute)==null) throw new Exception("sorry, no output attribute '" + eventDataAttribute + "' found for parameter 'eventData'");
        if (eventFormatAttribute!=null && schema.getAttribute(eventFormatAttribute)==null) throw new Exception("sorry, no output attribute '" + eventFormatAttribute + "' found for parameter 'eventFormat'");
        if (eventDeviceTypeAttribute!=null && schema.getAttribute(eventDeviceTypeAttribute)==null) throw new Exception("sorry, no output attribute '" + eventDeviceTypeAttribute + "' found for parameter 'eventDeviceType'");
        if (eventDeviceIdAttribute!=null && schema.getAttribute(eventDeviceIdAttribute)==null) throw new Exception("sorry, no output attribute '" + eventDeviceIdAttribute + "' found for parameter 'eventDeviceId'");

        callback = new WatsonIoTApplicationSourceCallback(this);
        client = WatsonIoTApplicationClient.getInstance(applicationCredentials);
        client.setEventCallback(callback);
        client.connect();

        for (String deviceType: subscriptionDeviceTypes) {
          for (String deviceId: subscriptionDeviceIds) {
            for (String event: subscriptionEvents) {
              for (String format: subscriptionFormats) {
                logger.info("WatsonIoTApplicationSource subscribing to deviceType='" + deviceType + "', deviceId='" + deviceId + "', event='" + event + "', format='" + format + "'");
                client.subscribeToDeviceEvents(deviceType, deviceId, event, format); } } } }
    }





    @Override
      public void startProcessing() throws Exception {
      System.out.println("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX WatsonIoTApplicationSource process() called");
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
        logger.info("WatsonIoTApplicationSource shutdown");
        callback.shutdown();
        client.disconnect();
        super.shutdown();
    }

}

////////////////////////////////////////////////////////////////////////////////////////////////////
