
package com.ibm.streamsx.watsoniot;

import java.io.File;

import java.util.Properties;

import org.apache.log4j.Logger;

import com.ibm.iotf.client.device.DeviceClient;

import com.ibm.streams.operator.AbstractOperator;
import com.ibm.streams.operator.OperatorContext;
import com.ibm.streams.operator.StreamingInput;
import com.ibm.streams.operator.Tuple;
import com.ibm.streams.operator.TupleAttribute;
import com.ibm.streams.operator.model.InputPortSet.WindowMode;
import com.ibm.streams.operator.model.InputPortSet.WindowPunctuationInputMode;
import com.ibm.streams.operator.model.InputPortSet;
import com.ibm.streams.operator.model.InputPorts;
import com.ibm.streams.operator.model.Libraries;
import com.ibm.streams.operator.model.Parameter;
import com.ibm.streams.operator.model.PrimitiveOperator;

////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * Class for WatsonIoTDeviceSink operator, which: 
 * <ul>
 * <li>recieves tuples from upstream operators and sends them as events to applications via the Watson IoT Platform</li>
 * <li>receives commands from applications via the Watson IoT Platform and sends them downstream as tuples to other operators.</li>
 * </ul>
 */

@PrimitiveOperator ( name="WatsonIoTDeviceSink", 
                     namespace="com.ibm.streamsx.watsoniot", 
                     description="connects an SPL data flow graph to the Watson IoT Platform as a device that sends events to applications and receives commands from them")
@InputPorts ( { 
	@InputPortSet ( optional=false, 
                    cardinality=1, 
                    windowingMode=WindowMode.NonWindowed, 
                    windowPunctuationInputMode=WindowPunctuationInputMode.Oblivious,
                    description="input port for tuples to be sent as events to applications via the Watson IoT Platform" )
      } )

@Libraries( { "opt/*" } )

  public class WatsonIoTDeviceSink extends AbstractOperator {
	
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
	
  // internal state variables or this operator
	private Logger logger;
	private WatsonIoTDeviceClientMBean client;
	
    /**
     * Initialize this operator. Called once before any tuples are processed.
     * @param context OperatorContext for this operator.
     * @throws Exception Operator failure, will cause the enclosing PE to terminate.
     */
	@Override
      public synchronized void initialize(OperatorContext context) throws Exception {
    	
		super.initialize(context);
		
        logger = Logger.getLogger(this.getClass());
        logger.info("WatsonIoTDeviceSink initialize() started");

        client = WatsonIoTDeviceClient.getClient(deviceCredentials, logger);

        logger.info("WatsonIoTDeviceSink initialize() ended");
    }


    @Override
      public void allPortsReady() throws Exception {

        logger.info("WatsonIoTDeviceSink allPortsReady() started");

        if (!client.isConnected()) {
          logger.info("WatsonIoTDeviceSink connecting to Watson IoT Platform");
          client.connect(); 
          if (!client.isConnected()) logger.error("WatsonIoTDeviceSink failed to connect"); }

        logger.info("WatsonIoTDeviceSink allPortsReady() ended");
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
      logger.info("WatsonIoTDeviceSink sending event=" + name + ", format=" + format + ", data=" + data);          ;
      client.publishEvent(name, data, format, eventQOS);
    }
    

    /**
     * Shutdown this operator.
     * @throws Exception Operator failure, will cause the enclosing PE to terminate.
     */
    public synchronized void shutdown() throws Exception {

        logger.info("WatsonIoTDeviceSink shutdown() started");

        if (client.isConnected()) {
          logger.info("WatsonIoTDeviceSink shutdown() disconnecting");
          client.disconnect(); }

        super.shutdown();

        logger.info("WatsonIoTDeviceSink shutdown() ended");
    }
}

////////////////////////////////////////////////////////////////////////////////////////////////////
