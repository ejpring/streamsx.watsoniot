// Copyright (C) 2017  International Business Machines Corporation
// All Rights Reserved

package com.ibm.streamsx.watsoniot.device;

import org.eclipse.paho.client.mqttv3.MqttException;

public interface WatsonIoTDeviceClientMBean {

  public boolean isConnected();

  public void connect() throws MqttException;

  public void setEnqueueCommands(Class<?> commandClass) throws Exception;

  public Object takeCommand();

  public boolean publishEvent(String event, Object data, String format, int qos) throws Exception;

  public void disconnect();

}
