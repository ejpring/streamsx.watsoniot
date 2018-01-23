// Copyright (C) 2017  International Business Machines Corporation
// All Rights Reserved

package com.ibm.streamsx.watsoniot.device;

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

import java.io.StringReader;

import java.util.Properties;

import org.apache.log4j.Logger;

////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * Class for WatsonIoTDeviceSink operator, which: 
 * <ul>
 * <li>recieves tuples from upstream operators and sends them as events to applications via the Watson IoT Platform</li>
 * <li>receives commands from applications via the Watson IoT Platform and sends them downstream as tuples to other operators.</li>
 * </ul>
 */

@PrimitiveOperator ( name="WatsonIoTDeviceSink", 
                     namespace="com.ibm.streamsx.watsoniot.device", 
                     description="The WatsonIoTDeviceSink operator connects an SPL graph to the Watson IoT Platform as an IoT 'device': it encodes input tuples as 'events' and sends them to IoT applications. The operator requires a file containing 'device credentials' issued by Watson IoT Platform. The credentials must be specified as shown in the 'Using a configuration file' section of the page at 'https://console.bluemix.net/docs/services/IoT/devices/libraries/java.html'. This operator may be used together with the WatsonIoTDeviceSource operator, which receives 'commands' from IoT devices. If so, the pair must specify the same credentials file, and must be fused into the same Streams PE.")
@InputPorts ( { 
	@InputPortSet ( optional=false, 
                    cardinality=1, 
                    windowingMode=WindowMode.NonWindowed, 
                    windowPunctuationInputMode=WindowPunctuationInputMode.Oblivious,
                    description="The input port consumes tuples encoded as 'events' and sends them to IoT applications via the Watson IoT Platform. Input tuples must at least include attributes for the event name and event data. By default, the data should be formatted as a JSON-encoded string. Optionally, input tuples may include an attribute for the data format." )
      } )

@Libraries( { "opt/*" } )

  public class WatsonIoTDeviceSink extends AbstractOperator {
	
    @Parameter ( name="deviceCredentials", 
               optional=false, 
               description="the contents of a Watson IoT Platform devicecredentials file (that is, a Java Properties file containing 'key = value' pairs), with newlines replaced by commas" )
    public void setDeviceCredentials(String credentials) throws Exception { 
      this.deviceCredentials = new Properties(); 
      deviceCredentials.load(new StringReader(credentials.replace(',', '\n'))); 
      System.out.println("******************"+deviceCredentials); }
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
        logger.debug("WatsonIoTDeviceSink.initialize() started");

        client = WatsonIoTDeviceClient.getClient(deviceCredentials, logger);

        logger.debug("WatsonIoTDeviceSink.initialize() ended");
    }


    @Override
      public void allPortsReady() throws Exception {

        logger.debug("WatsonIoTDeviceSink.allPortsReady() started");

        if (!client.isConnected()) {
          logger.info("WatsonIoTDeviceSink connecting to Watson IoT Platform");
          client.connect(); 
          if (!client.isConnected()) logger.error("WatsonIoTDeviceSink failed to connect"); }

        logger.debug("WatsonIoTDeviceSink.allPortsReady() ended");
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
      String format = eventFormatAttribute!=null ? eventFormatAttribute.getValue(tuple) : "json";
      String data = eventDataAttribute.getValue(tuple);
      logger.debug("WatsonIoTDeviceSink sending event=" + name + ", format=" + format + ", data=" + data);          ;
      client.publishEvent(name, data, format, eventQOS);
    }
    

    /**
     * Shutdown this operator.
     * @throws Exception Operator failure, will cause the enclosing PE to terminate.
     */
    public synchronized void shutdown() throws Exception {

        logger.debug("WatsonIoTDeviceSink.shutdown() started");

        if (client.isConnected()) {
          logger.info("WatsonIoTDeviceSink disconnecting from Watson IoT Platform");
          client.disconnect(); }

        super.shutdown();

        logger.debug("WatsonIoTDeviceSink.shutdown() ended");
    }
}

////////////////////////////////////////////////////////////////////////////////////////////////////
