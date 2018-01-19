// Copyright (C) 2017  International Business Machines Corporation
// All Rights Reserved

package com.ibm.streamsx.watsoniot.application;

import org.eclipse.paho.client.mqttv3.MqttException;

public interface WatsonIoTApplicationClientMBean {

  public boolean isConnected();

  public void connect() throws MqttException;

  public void setEnqueueEvents(boolean enqueue);

  public Object takeEvent(Class<?> eventClass);

  public void subscribeToDeviceEvents(String deviceType, String deviceId, String event, String format) throws Exception;

  public boolean publishCommand(String deviceType, String deviceId, String command, Object data, String format, int commandQOS) throws Exception;
  
  public void disconnect();

}
