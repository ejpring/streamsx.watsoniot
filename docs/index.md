

The [streamsx.watsoniot repository](https://github.com/ejpring/streamsx.watsoniot/) contains operators for IBM Streams that can connect its processing elements (PEs) to the Watson IoT Platform:

* The 'com.ibm.streamsx.watsoniot.application' toolkit contains operators that connect a PE to the Watson IoT Platform as a 'application'. These operators should be used in PEs that run in a Streaming Analytics instance in IBM Cloud. They receive event messages from devices via the Watson IoT Platform, decode their data into tuples, and send it downstream to cloud analytics. They may also encode commands from cloud analytics and send them to devices.

* The 'com.ibm.streamsx.watsoniot.device' toolkit contains operators that connect a PE to the Watson IoT Platform as a 'device'. These operators should be used in PEs that run outside the IBM cloud. They read sensors for device analytics, encode their data into event messages, and send them to applications via the Watson IoT Platform. They may also decode command messages from applications and send them to device analytics and actuators.

See the [repository's README](https://github.com/ejpring/streamsx.watsoniot/blob/master/README.md) for an overview of how Streams PEs can connect to the Watson IoT Platform.

See the [SPLDOC documentation](spldoc/html/index.html) for the specifics for configuring each operator.

See the [GitHub repository](https://github.com/ejpring/streamsx.watsoniot/) for the operator source code and sample SPL flow graphs that illustrate how the operators can connect analytics to the Watson IoT Platform.
