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

package org.edgexfoundry.engine;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;

import org.edgexfoundry.controller.CmdClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class CommandExecutor {

  @Autowired
  private CmdClient client;

  private static final org.edgexfoundry.support.logging.client.EdgeXLogger logger =
      org.edgexfoundry.support.logging.client.EdgeXLoggerFactory
          .getEdgeXLogger(CommandExecutor.class);

  @Async
  public void fireCommand(String deviceId, String commandId, String body) {
    //logger.info(
        //"Sending request to:  " + deviceId + "for command:  " + commandId + " with body: " + body);
    try {
      // for now - all rule engine requests are puts
      forwardRequest(deviceId, commandId, body, true);
    } catch (Exception exception) {
      logger.error("Problem sending command to the device service " + exception);
    }
  }
  /*
  private void forwardRequest(String id, String commandId, String body, boolean isPut) {
    if (client != null) {
      if (isPut)
        logger.debug("Resposne from command put is:  " + client.put(id, commandId, body));
      else
        logger.debug("Resposne from command get is:  " + client.get(id, commandId));
    } else {
      logger.error("Command Client not available - no command sent for: " + id + " to " + commandId
          + " containing: " + body);
    }
  }
  */
  private void forwardRequest(String id, String commandId, String body, boolean isPut) {
      String ip = "192.168.40.60";
      int port = 51717;
      InetSocketAddress serverAddr = new InetSocketAddress(ip, port);
      DatagramChannel dataChannel; 
	  try {    
		  dataChannel = DatagramChannel.open(); 
          dataChannel.configureBlocking(false);     
		  dataChannel.socket().connect(serverAddr);
	       
          byte[] packetContent = new byte[10000];

          byte[] frmid = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(-1).array();
          byte[] datatype = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(1).array();
          byte[] frmsize = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(500).array();
          System.arraycopy(frmid, 0, packetContent, 0, 4);
          System.arraycopy(datatype, 0, packetContent, 4, 4);
          System.arraycopy(frmsize, 0, packetContent, 8, 4);
          //System.out.print(body);
          System.arraycopy(body.getBytes(), 0, packetContent, 12, body.length());

          ByteBuffer buffer = ByteBuffer.allocate(packetContent.length).put(packetContent);
          buffer.flip();
          dataChannel.send(buffer, serverAddr);
      } catch (Exception e) {
          e.printStackTrace();
      }
  }
}
