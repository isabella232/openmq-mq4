/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2000-2010 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

/*
 * %W% %G%
 */ 

package com.sun.messaging.jmq.jmsclient;

import javax.jms.*;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.jmsclient.resources.ClientResources;

//import com.sun.messaging.AdministeredObject;
import java.io.IOException;


public class WriteChannel {

    private boolean debug = Debug.debug;

    private ProtocolHandler protocolHandler = null;
    //private ReadChannel readChannel = null;
    //private InterestTable interestTable = null;
    private ConnectionImpl connection = null;
    /**
     * flow control vars.
     */
    public static final String JMQSize = "JMQSize";

    //private boolean shouldPause = false;
    private int flowCount = -1;

    protected boolean turnOffFlowControl = false;
    
    protected boolean noFlowControl = false;
    
    public WriteChannel ( ConnectionImpl conn ) {
        this.connection = conn;
        this.protocolHandler = conn.getProtocolHandler();
        //this.readChannel = conn.getReadChannel();
        //this.interestTable = conn.getInterestTable();

        if ( System.getProperty("NoimqProducerFlowControl") != null ) {
            turnOffFlowControl = true;
        }
        
        if ( System.getProperty("imq.producer.flowControl.disabled") != null) {
        	noFlowControl = true;
        	ConnectionImpl.getConnectionLogger().info("Producer flow control is turned off.");
        }
    }

    //protected void
    //setReadChannel (ReadChannel readChannel) {
        //this.readChannel = readChannel;
    //}

    /**
     * Register interest to the broker.
     */
    protected void
    addInterest (Consumer consumer) throws JMSException {
        protocolHandler.addInterest ( consumer );
    }

    protected void
    removeInterest (Consumer consumer) throws JMSException {
        protocolHandler.removeInterest(consumer);
    }

    protected void
    unsubscribe (String durableName) throws JMSException {
        protocolHandler.unsubscribe (durableName);
    }

    protected void
    writeJMSMessage_save ( Message message ) throws JMSException {
        if ( turnOffFlowControl &&
            connection.getBrokerProtocolLevel() < PacketType.VERSION350 ) {
            protocolHandler.writeJMSMessage (message);
        } else {
            sendWithFlowControl (message);
        }
    }
    
    protected void writeJMSMessage(Message message) throws JMSException {
		
    	if (this.noFlowControl) {
			protocolHandler.writeJMSMessage(message);
		} else if (turnOffFlowControl
				&& connection.getBrokerProtocolLevel() < PacketType.VERSION350) {
			protocolHandler.writeJMSMessage(message);
		} else {
			sendWithFlowControl(message);
		}
	}

    /**
     * The follwing methods are for producer flow control.
     * This method is called by ReadChannel when it received
     * RESUME_FLOW packet from the broker.
     */
    protected void
    updateFlowControl (ReadOnlyPacket pkt) throws JMSException {

        int jmqSize = -1;

        try {
            Integer prop = (Integer) pkt.getProperties().get(JMQSize);
            if ( prop != null ) {
                jmqSize = prop.intValue();
            }
        } catch (IOException e) {
        ExceptionHandler.handleException(e, ClientResources.X_PACKET_GET_PROPERTIES, true);

        } catch (ClassNotFoundException e) {
        ExceptionHandler.handleException(e, ClientResources.X_PACKET_GET_PROPERTIES, true);
        }

        setFlowCount (jmqSize);
    }

    private synchronized void setFlowCount (int jmqSize) {
        flowCount = jmqSize;
        notifyAll();
    }

    /**
     * Send a message with flow control feature.
     */
    protected void sendWithFlowControl (Message message) throws JMSException {

        /**
         * wait until allow to send.
         */
        pause(message);

        /**
         * send message.
         */
        protocolHandler.writeJMSMessage (message);
    }

    protected synchronized void pause (Message message) {

        while (flowCount == 0) {

            if (debug) {
                Debug.println(
                    "WriteChannel : Waiting for RESUME_FLOW");
            }

            try {
                wait();
            }
            catch (InterruptedException e) {
                ;
            }
        }

        if (debug) {
            Debug.println("WriteChannel : wait() returned...");
        }

        if (flowCount > 0) {
            flowCount--;
        }

        if (flowCount == 0) {
            ( (MessageImpl) message).getPacket().setFlowPaused(true);
        }
        else {
            ( (MessageImpl) message).getPacket().setFlowPaused(false);
        }
    }

    protected void close() {
        if (debug) {
            Debug.println(
                "WriteChannel.close() : Waking up blocked producers");
        }

        // Set flow count to -1 to unblock the producers waiting for
        // RESUME_FLOW. There is no need to throw an exception
        // directly from the pause() method because
        // protocolHandler.writeJMSMessage() is guaranteed to fail
        // down the line...

        setFlowCount(-1);
    }

}
