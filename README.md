Copyright &copy; 2017  International Business Machines Corporation
All Rights Reserved

----


# streamsx.watsoniot -- toolkits for IBM Streams

The 'streamsx.watsoniot' repository contains operators for IBM Streams that can connect its processing elements (PEs) to the Watson IoT Platform:

* The 'com.ibm.streamsx.watsoniot.application' toolkit contains operators that connect a PE to the Watson IoT Platform as a 'application'. These operators should be used in PEs that run in a Streaming Analytics instance in IBM Cloud. They receive event messages from devices via the Watson IoT Platform, decode their data into tuples, and send it downstream to cloud analytics. They may also encode commands from cloud analytics and send them to devices.

* The 'com.ibm.streamsx.watsoniot.device' toolkit contains operators that connect a PE to the Watson IoT Platform as a 'device'. These operators should be used in PEs that run outside the IBM cloud. They read sensors for device analytics, encode their data into event messages, and send them to applications via the Watson IoT Platform. They may also decode command messages from applications and send them to device analytics and actuators.

The specifics for configuring each operator are documented in [these SPLDOC pages](http://ejpring.github.io/streamsx.watsoniot). Note that all of the operators in these toolkits require credentials from Watson IoT Platform, as described below. 

The repository includes several samples that illustrate how the operators can be configured in SPL flow graphs to connect analytics to the Watson IoT Platform.


# com.ibm.streamsx.watsoniot.application toolkit

PEs that run in a Streaming Analytics instance in IBM Cloud connect to the Watson IoT Platform as 'applications'. These PEs can connect with either the WatsonIoTApplicationConnector, or a pair of WatsonIoTApplicationSource and WatsonIoTApplicationSink operators. In any case, these operators require application credentials granted by Watson IoT Platform, as described below.

### data flow for Watson IoT applications

The WatsonIoTApplicationConnector operator consumes 'commands' as input tuples and produces 'events' as output tuples. By default, data is encoded as a JSON string in both events and commands. Data flows between the WatsonIoTApplicationConnector and cloud analytics like this:

![WatsonIoTApplicationConnector Data flow](images/streamsx.watsoniot/Slide3.png)

Alternatively, PEs may connect to the Watson IoT Platform as applications with a pair of WatsonIoTApplicationSource and WatsonIoTApplicationSink operators, which separately produce 'events' and consume 'commands', respectively. Data flows between these operators and cloud analytics like this:

![WatsonIoTApplicationSource and WatsonIoTApplicationSink data flow](images/streamsx.watsoniot/Slide4.png)

### credentials for Watson IoT applications

The operators in the 'com.ibm.streamsx.watsoniot.application' toolkit require application credentials granted by the Watson IoT platform to connect. To create application credentials, navigate to the "IBM Watson IoT Platform" dashboard, click "Apps" in the left-side toolbar, and then click "+Generate API Key" to reach this page:

![WatsonIoTPlatform dashboard --> Apps --> Generate API Key](images/WatsonIoTPlatform_dashboard_Apps_GenerateAPIKey.png)

*Before clicking 'Generate'*, copy the 'API Key' and 'Authentication Token' fields into an 'applicationid.credentials' file, like this:

```
[application]
org = ... paste IBM Cloud 'organization ID' here ...
id = ... assign an identifier for the application here ...
auth-method = apikey
auth-key = ... paste Watsopn IOT 'API Key' here ...
auth-token = ... paste Watson IoT 'Authentication Token here ...
enable-shared-subscription = false
```

When a pair of WatsonIoTApplicationSource and WatsonIoTApplicationSink operators are used, both must specify the same application credentials file, and they must be fused into the same PE.

See [Using a configuration file](https://github.com/IBM-Bluemix-Docs/IoT/blob/master/applications/libraries/java.md#constructor) for the details of application credentials files.

### sample SPL source code for Watson IoT applications

This repository includes sample SPL source code that illustrates how the 'application' operators in a PE running in a Streaming Analytics instance in the IBM Cloud can be configured to recieve events from the Watson IoT Platform and send commands to it. Each sample is included in the repository as a separate Eclipse project for Streams Studio:

* The SampleWatsonIoTApplicationConnector project illustrates the WatsonIoTApplicationConnector operator

* The SampleWatsonIoTApplicationSourceAndSink project illustates the WatsonIoTApplicationSource and SinkWatsonIoTApplication operators

These projects encode data for both events and commands in the default format of 'json', and depend upon the 'com.ibm.streamsx.json' toolkit. That toolkit is included in the IBM Streams product at '$STREAMS_INSTALL/toolkits/com.ibm.streams.json'.

To try these samples, generate application credentials as described above and store them in the project directories as 'applicationid.credentials' files, using the 'applicationid.credentials-template' files in those directories as guides.

* Install Python version 3.5, if not already installed, and then install the Python 'streamsx.rest' package, by executing these commands at a Linux command prompt:
```
sudo yum install https://centos6.iuscommunity.org/ius-release.rpm
sudo yum install python35u-3.5.4 python35u-pip python35u-devel 
sudo pip3.5 install streamsx.rest
```

* Open a Linux 'Terminal' window and go to one of the sample directories:
```bash
cd $HOME/git/streamsx.watsoniot/samples/SampleWatsonIoTApplicationConnector
... or ...
cd $HOME/git/streamsx.watsoniot/samples/SampleWatsonIoTApplicationSourceAndSink
```

* Build the sample SPL source code into a Streams Application Bundle (SAB) by executing this BASH script at a command prompt:
```
build.sh
```

* Run the SAB bundle locally in standalone mode by executing this BASH script at a command prompt:
```
run.sh
```

* Or, submit the SAB bundle to run as a job in a Streaming Analytics instance in IBM Cloud by executing this Python script at a command prompt:
```
python3.5 submitJob.py
```

* Run one of the 'device' samples described below concurrently with the 'application' sample to illustrate receiving events as well as sending commands.

* Retrieve the logs from a job running in a Streaming Analytics instance in IBM Cloud by executing this Python script at a command prompt:
```
python3.5 retrieveLogs.py
```

* Cancel a job running in a Streaming Analytics instance in IBM Cloud by executing this Python3 script at a command prompt:
```
python3.5 cancelJob.py
```

The 'run.sh' and 'submitJob.py' scripts configure the sample SPL code to run for a limited period of time and then terminate themselves. You can change their timeout by editing the script files and changing the 'timeoutInterval' parameter, specifying a new value in seconds. Or, to cancel the 'run.sh' script immediately, type 'Ctrl-C' twice. Or, to cancel a submitted job, execute the command 'python3.5 cancelJob.py'. 


# com.ibm.streamsx.watsoniot.device toolkit

PEs that run outside the IBM Cloud connect to Watson IoT Platform as 'devices'. These PEs can connect with either the WatsonIoTDeviceConnector, or a pair of WatsonIoTDeviceSource and WatsonIoTDeviceSink operators. In any case, these operators require devicecredentials granted by Watson IoT Platform, as described below.

### data flow for Watson IoT devices

The WatsonIoTDeviceConnector operator consumes 'events' as input tuples and produces 'commands' as output tuples. By default, data is encoded as a JSON string in both events and commands. Data flows between the WatsonIoTPlatformDeviceConnector and device analytics like this:

![WatsonIoTApplicationConnector Data flow](images/streamsx.watsoniot/Slide1.png)

Alternatively, PEs may connect to the Watson IoT Platform as devices with a pair of WatsonIoTDeviceSource and WatsonIoTDeviceSink operators, which separately consume 'events' and produce 'commands', respectively. Data flows between these operators and device analytics like this:

![WatsonIoTDeviceSource and WatsonIoTDeviceSink data flow](images/streamsx.watsoniot/Slide2.png)


### credentials for Watson IoT devices

The operators in the 'com.ibm.streamsx.watsoniot.device' toolkit require device credentials granted by the Watson IoT platform to connect. To create device credentials, navigate to the "IBM Watson IoT Platform" dashboard, click "Devices" in the left-side toolbar, and click "+Add Device". Fill in the forms, clicking "Next" on each one. Then click "Done" on the "Summary" to reach this page:

![WatsonIoTPlatform dashboard --> Devices -> +Add Device ... Device Credentials](images/WatsonIoTPlatform_dashboard_Devices_AddDevice_Credentials.png)

*After clicking 'Done'*, copy the 'Organization ID', 'Device Type', 'Device ID', and 'Authentication Token' fields into a 'deviceid.credentials' file, like this:

```
[device]
org = ... paste IBM Cloud 'organization ID' here ...
type = ... paste Watson IoT device type here ...
id = ... paste Watson IoT device ID here ...
auth-token = ... paste Watson IoT 'Authentication Token' here ...
auth-method = token
```

When a pair of WatsonIoTDeviceSource and WatsonIoTDeviceSink operators are used, both must specify the same device credentials file, and they must be fused into the same PE.

See [Using a configuration file](https://github.com/IBM-Bluemix-Docs/IoT/blob/master/devices/libraries/java.md#constructor) for the details of device credentials files.

### sample SPL source code for Watson IoT devices

This repository includes sample SPL source code that illustrates how the 'device' operators in PEs running outside the IBM Cloud can be configured to send events to the Watson IoT Platform and receive commands from it. Each sample is included in the repository as a separate Eclipse project for Streams Studio:

* The SampleWatsonIoTDeviceConnector project illustrates the WatsonIoTDeviceConnector operator

* The SampleWatsonIoTDeviceSourceAndSink project illustates the WatsonIoTDeviceSource and SinkWatsonIoTDevice operators

These projects encode data for both events and commands in the default format of 'json', and depend upon the 'com.ibm.streamsx.json' toolkit. That toolkit is included in the IBM Streams product at '$STREAMS_INSTALL/toolkits/com.ibm.streams.json'.

To try these samples, generate device credentials as described above and store them in the project directories as 'deviceid.credentials' files, using the 'deviceid.credentials-template' files in those directories as guides.

* Open a Linux 'Terminal' window and go to one of the sample directories:
```bash
cd $HOME/git/streamsx.watsoniot/samples/SampleWatsonIoTDeviceConnector
... or ...
cd $HOME/git/streamsx.watsoniot/samples/SampleWatsonIoTDeviceSourceAndSink
```

* Build the sample SPL source code into a Streams Device Bundle (SAB) by executing this BASH script at a command prompt:
```
build.sh
```

* Run the SAB bundle in standalone mode by executing this BASH script at a command prompt:
```
run.sh
```

* Run one of the 'application' samples described above concurrently with the 'device' sample to illustrate receiving commands as well as sending events.

The 'run.sh' script configures the sample SPL code to run for a limited period of time and then terminate itself. You can change its timeout by editing the script file and changing the 'timeoutInterval' parameter, specifying a new value in seconds. Or, to cancel the 'run.sh' script immediately, type 'Ctrl-C' twice. 

----
