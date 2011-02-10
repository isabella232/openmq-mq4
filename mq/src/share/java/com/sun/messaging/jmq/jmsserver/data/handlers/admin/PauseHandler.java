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
 * @(#)PauseHandler.java	1.27 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.data.handlers.admin;

import java.util.Hashtable;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Vector;
import java.util.HashSet;
import java.util.Set;
import java.util.Iterator;

import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.io.PacketType;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.util.admin.MessageType;
import com.sun.messaging.jmq.util.admin.ConnectionInfo;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.util.ServiceType;
import com.sun.messaging.jmq.util.DestType;
import com.sun.messaging.jmq.jmsserver.core.Destination;
import com.sun.messaging.jmq.util.DestState;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.service.ServiceManager;
import com.sun.messaging.jmq.jmsserver.service.Service;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;

public class PauseHandler extends AdminCmdHandler
{

    private static boolean DEBUG = getDEBUG();

    public PauseHandler(AdminDataHandler parent) {
	super(parent);
    }

    /**
     * Handle the incomming administration message.
     *
     * @param con	The Connection the message came in on.
     * @param cmd_msg	The administration message
     * @param cmd_props The properties from the administration message
     */
    public boolean handle(IMQConnection con, Packet cmd_msg,
				       Hashtable cmd_props) {

	if ( DEBUG ) {
            logger.log(Logger.DEBUG, this.getClass().getName() + ": " +
                "Pausing: " + cmd_props);
        }

	    String pauseTarget = (String)cmd_props.get(MessageType.JMQ_PAUSE_TARGET);
	    String service = (String)cmd_props.get(MessageType.JMQ_SERVICE_NAME);
	    String destination = (String)cmd_props.get(MessageType.JMQ_DESTINATION);
        Integer type = (Integer)cmd_props.get(MessageType.JMQ_DEST_TYPE);

        Integer pauseType = (Integer)cmd_props.get(MessageType.JMQ_DEST_STATE);

        int status = Status.OK;
        String errMsg = null;

        assert service == null || destination == null;

	/*
	 * Compatibility with MQ 3.0.1
	 * If pause target not set, assume that the service is being
	 * paused.
	 */
	if (pauseTarget == null)  {
	    pauseTarget = MessageType.JMQ_SERVICE_NAME;
	}

        try {
	    if (MessageType.JMQ_SERVICE_NAME.equals(pauseTarget))  {
		if (service == null)  {
                    logger.log(Logger.INFO,
                       BrokerResources.I_PAUSING_ALL_SVCS);
		} else  {
                    logger.log(Logger.INFO,
                       BrokerResources.I_PAUSING_SVC,
                       service);
		}
                pauseService(true, service);
	    } else if (MessageType.JMQ_DESTINATION.equals(pauseTarget))  {
                logger.log(Logger.INFO,
                       BrokerResources.I_PAUSING_DST,
                       destination);
                int pauseval = (pauseType == null ? 
                   DestState.PAUSED : pauseType.intValue());

		if (destination == null)  {
                    Iterator itr = Destination.getAllDestinations();
                    while (itr.hasNext()) {
                        Destination d =(Destination)itr.next();

			/*
			 * Skip internal, admin, or temp destinations.
			 * Skipping temp destinations may need to be
			 * revisited.
			 */
			if (d.isInternal() || d.isAdmin() || d.isTemporary())  {
			    continue;
			}

                        d.pauseDestination(pauseval);
                    }
		} else  {
                    Destination d = Destination.getDestination(destination,
                      DestType.isQueue(type.intValue()));
                    if (d == null) {
                        String msg = Globals.getBrokerResources().getString(
                             BrokerResources.I_PAUSED_DST_NOT_EXIST,
                       (DestType.isQueue(type.intValue()) ? " queue:" :
                           " topic:") +destination );

                        errMsg = msg;
                        status = Status.NOT_FOUND;
                        logger.log(Logger.ERROR,msg);
                    } else {
			if (d.isInternal() || d.isAdmin())  {
                             errMsg = Globals.getBrokerResources().getString(
                                 BrokerResources.I_PAUSED_ADMIN,
                                (DestType.isQueue(type.intValue()) ? " queue:" :
                                 " topic:") +destination );
                            logger.log(Logger.INFO, errMsg);

                            status = Status.ERROR;
			} else  {
                            d.pauseDestination(pauseval);
			}
                    }
                }
            }
        } catch (IOException e) {
            logger.log(Logger.ERROR, rb.E_PAUSE_SERVICE, service, e);
            status = Status.ERROR;
            errMsg = rb.getString(rb.E_PAUSE_SERVICE, service) + ": " + e;
        } catch (BrokerException e) {
            logger.log(Logger.ERROR, rb.E_PAUSE_SERVICE, service, e);
            status = Status.ERROR;
            errMsg = rb.getString(rb.E_PAUSE_SERVICE, service) + ": " + e;
        } catch (IllegalArgumentException e) {
            errMsg = e.getMessage();
            status = Status.NOT_FOUND;
        }

	// Send reply
	Packet reply = new Packet(con.useDirectBuffers());
	reply.setPacketType(PacketType.OBJECT_MESSAGE);

	setProperties(reply, MessageType.PAUSE_REPLY, status, errMsg);

	parent.sendReply(con, cmd_msg, reply);
    return true;
    }

    /**
     * Pause/Resume a service. If service is null we pause/resume all nonADMIN 
     * services. 
     *
     * @throws IllegalArgumentException If serviceName is not a valid service
     *                                  name
     * @throws BrokerException If service can't be paused/resumed
     */
    public static void pauseService(boolean pause, String serviceName)
        throws BrokerException, IllegalArgumentException {

	ServiceManager sm = Globals.getServiceManager();
        Set activeServices = null;
        BrokerResources rb = Globals.getBrokerResources();

        if (serviceName != null && sm.getService(serviceName) == null) {
            throw new IllegalArgumentException(rb.getString(
                rb.X_NO_SUCH_SERVICE, serviceName));
        }

        if (pause) {
            if (serviceName == null) {
                sm.pauseAllActiveServices(ServiceType.NORMAL, true);
            } else {
                sm.pauseService(serviceName, true);
            }
        } else {
            if (serviceName == null) {
                sm.resumeAllActiveServices(ServiceType.NORMAL);
            } else {
                sm.resumeService(serviceName);
            }
        }
    }
}