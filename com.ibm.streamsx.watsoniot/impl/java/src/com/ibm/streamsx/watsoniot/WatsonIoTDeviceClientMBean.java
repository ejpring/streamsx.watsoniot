package com.ibm.streamsx.watsoniot;

import com.ibm.iotf.client.device.CommandCallback;

import org.eclipse.paho.client.mqttv3.MqttException;

public interface WatsonIoTDeviceClientMBean {

  public boolean isConnected();

  public void connect() throws MqttException;

  public void setCommandCallback(CommandCallback callback);

  public boolean publishEvent(String event, Object data, String format, int qos) throws Exception;

  public void disconnect();



}
