// Copyright (C) 2017  International Business Machines Corporation
// All Rights Reserved

package com.ibm.streamsx.watsoniot.application;

import com.ibm.iotf.client.app.ApplicationClient;

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

import java.io.File;

import java.util.Properties;

import org.apache.log4j.Logger;

////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * Class for WatsonIoTApplicationSink operator, which: 
 * <ul>
 * <li>recieves tuples from upstream operators and sends them as commands to devices via the Watson IoT Platform</li>
 * <li>receives events from devices via the Watson IoT Platform and sends them downstream as tuples to other operators.</li>
 * </ul>
 */

@PrimitiveOperator ( name="WatsonIoTApplicationSink", 
                     namespace="com.ibm.streamsx.watsoniot.application", 
                     description="The WatsonIoTApplicationSink operator connects an SPL graph to the Watson IoT Platform as an IoT 'application': it encodes input tuples as 'commands' and sends them to IoT devices. The operator requires a file containing 'application credentials' issued by Watson IoT Platform. The credentials must be specified as shown in the 'Using a configuration file' section of the page at 'https://console.bluemix.net/docs/services/IoT/applications/libraries/java.html'. This operator may be used together with the WatsonIoTApplicationSource operator, which receives 'events' from IoT devices. If so, the pair should specify the same credentials file, and should be fused into the same Streams PE.")
@InputPorts ( { 
	@InputPortSet ( optional=false, 
                    cardinality=1, 
                    windowingMode=WindowMode.NonWindowed, 
                    windowPunctuationInputMode=WindowPunctuationInputMode.Oblivious,
                    description="The input port consumes tuples encoded as 'commands' and sends them to IoT devices via the Watson IoT Platform. Input tuples must at least include attributes for the device type, device identifier, command name, and command data. By default, the data should be formatted as a JSON-encoded string. Optionally, input tuples may include an attribute for the data format." )
      } )

@Libraries( { "opt/*" } )


public class WatsonIoTApplicationSink extends AbstractOperator {
	
  @Parameter ( name="applicationCredentials", 
               optional=false, 
               description="the name of a file containing Watson IoT Platform application credentials" )
    public void setApplicationCredentials(String filename) { 
      this.applicationCredentialsFilename = filename;
      this.applicationCredentials = ApplicationClient.parsePropertiesFile(new File(filename)); }
    private String applicationCredentialsFilename;
    private Properties applicationCredentials;
  
	@Parameter ( name="commandName", 
                 optional=false, 
                 description="an input attribute that will be sent to the Watson IoT Platform as the command name" )
    public void setCommandName(TupleAttribute<Tuple,String> attribute) { this.commandNameAttribute = attribute; }
	private TupleAttribute<Tuple,String> commandNameAttribute; 

	@Parameter ( name="commandData", 
                 optional=false, 
                 description="an input attribute of type 'rstring' that will be sent to the Watson IoT Platform as command data" )
	public void setCommandData(TupleAttribute<Tuple,String> attribute) { this.commandDataAttribute = attribute; }
	private TupleAttribute<Tuple,String> commandDataAttribute; 

	@Parameter ( name="commandFormat", 
                 optional=true, 
                 description="an input attribute of type 'rstring' that specifies the format of the data sent to the Watson IoT Platform, defaulting to 'json' if not specified" )
	public void setCommandFormat(TupleAttribute<Tuple,String> attribute) { this.commandFormatAttribute = attribute; }
	private TupleAttribute<Tuple,String> commandFormatAttribute = null;

	@Parameter ( name="commandQOS", 
                 optional=true, 
                 description="the 'quality of service' for commmands sent to the Watson IoT Platform, either '0' or '1' or '2', defaulting to '2' if not specified" ) 
	public void setCommandQOS(int value) { this.commandQOS = value; }
	private int commandQOS = 2;

	@Parameter ( name="commandDeviceType", 
                 optional=false, 
                 description="an input attribute of type 'rstring' that specifies the type of device the Watson IoT Platform should send the command to, defaulting to '??????????' if not specified" )
	public void setCommandDeviceType(TupleAttribute<Tuple,String> attribute) { this.commandDeviceTypeAttribute = attribute; }
	private TupleAttribute<Tuple,String> commandDeviceTypeAttribute;

	@Parameter ( name="commandDeviceId", 
                 optional=false, 
                 description="an input attribute of type 'rstring' that identfies the device the Watson IoT Platform should send the command to, defaulting to '??????????' if not specified" )
	public void setCommandDeviceId(TupleAttribute<Tuple,String> attribute) { this.commandDeviceIdAttribute = attribute; }
	private TupleAttribute<Tuple,String> commandDeviceIdAttribute;

  // internal state variables or this operator	
	private Logger logger;
	private WatsonIoTApplicationClientMBean client;

    /**
     * Initialize this operator. Called once before any tuples are processed.
     * @param context OperatorContext for this operator.
     * @throws Exception Operator failure, will cause the enclosing PE to terminate.
     */
	@Override
	public synchronized void initialize(OperatorContext context) throws Exception {
    	
		super.initialize(context);

        logger = Logger.getLogger(this.getClass());
        logger.debug("WatsonIoTApplicationSink.initialize() started");

        client = WatsonIoTApplicationClient.getClient(applicationCredentials, logger);

        logger.debug("WatsonIoTApplicationSink.initialize() ended");
    }


    @Override
      public void allPortsReady() throws Exception {

        logger.debug("WatsonIoTApplicationSink.allPortsReady() started");

        if (!client.isConnected()) {
          logger.info("WatsonIoTApplicationSink connecting to Watson IoT Platform with credentials from " + applicationCredentialsFilename);
          client.connect(); 
          if (!client.isConnected()) logger.error("WatsonIoTApplicationSink failed to connect"); }

        logger.debug("WatsonIoTApplicationSink.allPortsReady() ended");
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
      String format = commandFormatAttribute!=null ? commandFormatAttribute.getValue(tuple) : "json";
      String data = commandDataAttribute.getValue(tuple);
      logger.debug("WatsonIoTApplicationSink sending command='" + name + "', format='" + format + "', data='" + data + "', deviceType='" + type + "', deviceId='" + id + "'");          ;
        
      client.publishCommand(type, id, name, data, format, commandQOS);
    }




    /**
     * Shutdown this operator.
     * @throws Exception Operator failure, will cause the enclosing PE to terminate.
     */
    public synchronized void shutdown() throws Exception {
        logger.debug("WatsonIoTApplicationSink.shutdown() started");

        if (client.isConnected()) {
          logger.info("WatsonIoTApplicationSink disconnecting from Watson IoT Platform");
          client.disconnect(); }

        super.shutdown();

        logger.debug("WatsonIoTApplicationSink.shutdown() ended");
    }

}

////////////////////////////////////////////////////////////////////////////////////////////////////
