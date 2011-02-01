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
 * @(#)GetConfigChangesHandler.java	1.5 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.multibroker.raptor.handlers;

import java.io.*;
import com.sun.messaging.jmq.util.*;
import com.sun.messaging.jmq.jmsserver.util.*;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.jmsserver.core.*;
import com.sun.messaging.jmq.jmsserver.multibroker.raptor.*;

public class GetConfigChangesHandler extends GPacketHandler {
    private static boolean DEBUG = false;

    public GetConfigChangesHandler(RaptorProtocol p) {
        super(p);
    }

    public void handle(BrokerAddress sender, GPacket pkt) {
        if (DEBUG)
            logger.log(logger.DEBUG, "GetConfigChangesHandler");

        if (pkt.getType() == ProtocolGlobals.G_GET_CONFIG_CHANGES_REQUEST) {
            handleGetConfigChanges(sender, pkt);
        }
        else if (pkt.getType() ==
            ProtocolGlobals.G_GET_CONFIG_CHANGES_REPLY) {
            handleGetConfigChangesReply(sender, pkt);
        }
        else {
            logger.log(logger.WARNING, "GetConfigChangesHandler " +
                "Internal error : Cannot handle this packet :" +
                pkt.toLongString());
        }
    }

    public void handleGetConfigChanges(BrokerAddress sender, GPacket pkt) {
        long timestamp = ((Long) pkt.getProp("TS")).longValue();
        p.receiveConfigChangesRequest(sender, timestamp);
    }

    public void handleGetConfigChangesReply(BrokerAddress sender, GPacket pkt) {

        int status = ((Integer)pkt.getProp("S")).intValue();
        long timestamp = ((Long) pkt.getProp("TS")).longValue();
        
        String emsg = null;

        int c = 0; 
        byte[] buf = null;
        if (status == ProtocolGlobals.G_SUCCESS) {
            c = ((Integer) pkt.getProp("C")).intValue();

            if (pkt.getPayload() != null) {
                buf = pkt.getPayload().array();
            }
        } else {
            emsg = (String)pkt.getProp("reason");
            if (emsg == null) {
                emsg = Status.getString(status);
            }
        }

        p.receiveConfigChangesReply(sender, timestamp, c, buf, emsg);
    }
}


/*
 * EOF
 */
