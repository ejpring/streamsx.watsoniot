
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

////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * Class for WatsonIoTApplicationSink operator, which: 
 * <ul>
 * <li>recieves tuples from upstream operators and sends them as commands to devices via the Watson IoT Platform</li>
 * <li>receives events from devices via the Watson IoT Platform and sends them downstream as tuples to other operators.</li>
 * </ul>
 */

@PrimitiveOperator ( name="WatsonIoTApplicationSink", 
                     namespace="com.ibm.streamsx.watsoniot", 
                     description="connects an SPL data flow graph to the Watson IoT Platform as an application that receives events from devices and sends commands to them")
@InputPorts ( { 
	@InputPortSet ( optional=false, 
                    cardinality=1, 
                    windowingMode=WindowMode.NonWindowed, 
                    windowPunctuationInputMode=WindowPunctuationInputMode.Oblivious,
                    description="input port for tuples to be sent as commands to devices via the Watson IoT Platform" )
      } )

@Libraries( { "opt/*" } )


public class WatsonIoTApplicationSink extends AbstractOperator {
	
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

  // internal state variables or this operator	
	private Logger logger;
	private WatsonIoTApplicationClient client;


    /**
     * Initialize this operator. Called once before any tuples are processed.
     * @param context OperatorContext for this operator.
     * @throws Exception Operator failure, will cause the enclosing PE to terminate.
     */
	@Override
	public synchronized void initialize(OperatorContext context) throws Exception {
    	
		super.initialize(context);

        logger = Logger.getLogger(this.getClass());
        logger.info("WatsonIoTApplicationSink initializing");

        client = WatsonIoTApplicationClient.getInstance(applicationCredentials);
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

      String type = commandDeviceTypeAttribute.getValue(tuple);
      String id = commandDeviceIdAttribute.getValue(tuple);
      String name = commandNameAttribute.getValue(tuple);
      String data = commandDataAttribute.getValue(tuple);
      String format = commandFormatAttribute!=null ? commandFormatAttribute.getValue(tuple) : "json";
      logger.info("WatsonIoTApplicationSink sending command='" + name + "', format='" + format + "', data='" + data + "', deviceType='" + type + "', deviceId='" + id + "'");          ;
        
      client.publishCommand(type, id, name, data, format, commandQOS);
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
        logger.info("WatsonIoTApplicationSink shutdown");
        client.disconnect();
        super.shutdown();
    }

}

////////////////////////////////////////////////////////////////////////////////////////////////////
