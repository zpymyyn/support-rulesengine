/*******************************************************************************
 * Copyright 2017 Dell Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 * @microservice: support-rulesengine
 * @author: Jim White, Dell
 * @version: 1.0.0
 *******************************************************************************/

package org.edgexfoundry.messaging;

import com.google.gson.Gson;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.lang.Object;

import org.edgexfoundry.domain.core.Event;
import org.edgexfoundry.engine.RuleEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.zeromq.ZMQ;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;

/**
 * Export data message ingestion bean - gets messages out of ZeroMQ from export service.
 */
@Component
public class ZeroMQEventSubscriber {

  private static final org.edgexfoundry.support.logging.client.EdgeXLogger logger =
      org.edgexfoundry.support.logging.client.EdgeXLoggerFactory
          .getEdgeXLogger(ZeroMQEventSubscriber.class);

  @Value("${export.zeromq.port}")
  private String zeromqAddressPort;
  @Value("${export.zeromq.host}")
  private String zeromqAddress;
  @Value("${export.client}")
  private boolean exportClient;
  //pengzhou: port and addr for device discovery from metadata
  @Value("${export.zeromq.portdevice}")
  private String zeromqAddressPort_device;
  @Value("${export.zeromq.host}")
  private String zeromqAddress_device;
  @Autowired
  RuleEngine engine;

  private ZMQ.Socket subscriber;
  private ZMQ.Socket subscriber_device;
  private ZMQ.Context context;

  {
    context = ZMQ.context(1);
  }

  public void receive() {
    getSubscriber();
    String exportString = null;
    byte[] exportBytes = null;
    Event event;
    logger.info("Watching for new exported Event messages...");
    double[] dur;
    double sum = 0;
    double mean = 0;
    int i = 0;
    
    dur = new double[1000];
    try {
      while (!Thread.currentThread().isInterrupted()) {
        if (exportClient) {
          exportString = subscriber.recvStr();
          event = toEvent(exportString);
        } else {
          exportBytes = subscriber.recv();
          event = toEvent(exportBytes);
          //pengzhou: try print the event to see the format
          System.out.println(event);
        }
        //pengzhou: here below send the event to the rule engine
    	double startTime = System.nanoTime();
        engine.execute(event);
        double endTime = System.nanoTime();
        double duration = (endTime - startTime); 
        //logger.info("duratino of excution is " +  TimeUnit.SECONDS.convert(duration, TimeUnit.NANOSECONDS));
        dur[i] = duration;
        sum += duration; 
        //logger.info("event number " + i);
        if (i == 999) {
        	mean = sum/1000;
        	StandardDeviation sd2 = new StandardDeviation();        	
        	logger.info("average duration is " + mean + " std is " + sd2.evaluate(dur) + " nanoseconds");
        	System.out.println(Arrays.toString(dur));
        }
        i += 1;
        //logger.info("Event sent to rules engine for device id:  " + event.getDevice());
        
      }
    } catch (Exception e) {
      logger.error("Unable to receive messages via ZMQ: " + e.getMessage());
    }
    logger.error("Shutting off Event message watch due to error!");
    if (subscriber != null)
      subscriber.close();
    subscriber = null;
    // try to restart

    logger.debug("Attempting restart of Event message watch.");
    receive();
  }

  public String getZeromqAddress() {
    return zeromqAddress;
  }

  public void setZeromqAddress(String zeromqAddress) {
    this.zeromqAddress = zeromqAddress;
  }

  public String getZeromqAddressPort() {
    return zeromqAddressPort;
  }

  public void setZeromqAddressPort(String zeromqAddressPort) {
    this.zeromqAddressPort = zeromqAddressPort;
  }

  private ZMQ.Socket getSubscriber() {
    if (subscriber == null) {
      try {
        subscriber = context.socket(ZMQ.SUB);
        subscriber.connect(zeromqAddress + ":" + zeromqAddressPort);
        subscriber.subscribe("".getBytes());
      } catch (Exception e) {
        logger.error("Unable to get a ZMQ subscriber.  Error:  " + e);
        subscriber = null;
      }
    }
    return subscriber;
  }

  //pengzhou: discover new device
  /*private ZMQ.Socket getSubscriber_device() {
	    if (subscriber_device == null) {
	      try {
	        subscriber_device = context.socket(ZMQ.SUB);
	        subscriber_device.connect(zeromqAddress_device + ":" + zeromqAddressPort_device);
	        subscriber_device.subscribe("".getBytes());
	      } catch (Exception e) {
	        logger.error("Unable to get a ZMQ subscriber for device discovery.  Error:  " + e);
	        subscriber_device = null;
	      }
	    }
	    return subscriber;
	  }
  */
  private Event toEvent(String eventString) throws IOException, ClassNotFoundException {
    byte[] data = Base64.getDecoder().decode(eventString);
    ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(data));
    Event event = (Event) in.readObject();
    in.close();
    return event;
  }

  private static Event toEvent(byte[] eventBytes) throws IOException, ClassNotFoundException {
    try {
      Gson gson = new Gson();
      String json = new String(eventBytes);
      return gson.fromJson(json, Event.class);
    } catch (Exception e) {
      // Try to degrade to deprecated serialization functionality gracefully
      ByteArrayInputStream bis = new ByteArrayInputStream(eventBytes);
      ObjectInput in = new ObjectInputStream(bis);
      Event event = (Event) in.readObject();
      return event;
    }
  }

}
