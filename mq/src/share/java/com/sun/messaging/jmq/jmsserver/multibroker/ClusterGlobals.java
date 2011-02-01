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
 * @(#)ClusterGlobals.java	1.12 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.multibroker;

import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.core.ClusterBroadcast;

public class ClusterGlobals 
{
    /** Falcon MessageBus protocol version */
    public static final int VERSION_300 = ClusterBroadcast.VERSION_300;
    public static final int VERSION_210 = ClusterBroadcast.VERSION_210; 

    public static final String TOPOLOGY_PROPERTY = Globals.IMQ + ".topology";

    /** JMS message. */
    public static final int MB_MESSAGE_DATA = 1;

    /** Broker-to-broker acknowledgement for a JMS message */
    public static final int MB_MESSAGE_ACK = 2;

    /** Interest change notification */
    public static final int MB_INTEREST_UPDATE = 3;

    /** Client closed notification */
    public static final int MB_CLIENT_CLOSED = 4;

    /**
     * Election protocol : Request to lock a resource such as durable name
     */
    public static final int MB_LOCK_REQUEST = 5;

    /** Election protocol : Response. */
    public static final int MB_LOCK_RESPONSE = 6;

    /** Configuration Change Event Logging : Request */
    public static final int MB_CONFIG_CHANGE_EVENT = 7;

    /** Configuration Change Event Logging : Response */
    public static final int MB_CONFIG_CHANGE_EVENT_ACK = 8;

    /** Retrieve configuration change records : Request */
    public static final int MB_GET_CONFIG_CHANGES_REQUEST = 9;

    /** Retrieve configuration change records : Response */
    public static final int MB_GET_CONFIG_CHANGES_RESPONSE = 10;

    /** Destination change notification */
    public static final int MB_DESTINATION_UPDATE = 11;

    /** Reset replicated persistent state objects (durables, destinations) */
    public static final int MB_RESET_PERSISTENCE = 12;

    /** Restart cluster */
    public static final int MB_RESTART_CLUSTER = 13;

    /** Request a full interest update. */
    public static final int MB_REQUEST_INTEREST_UPDATE = 14;

    /** Falcon HA: Periodic HA Active update ping. */
    public static final int MB_HA_ACTIVE_UPDATE = 15;

    //
    // Acknowledgement types for the MB_MESSAGE_ACK packet.
    //

    /** Message sent acknowledgement. For recording statistics??? */
    public static final int MB_MSG_SENT = 0;

    /** Client is no longer interested in this message. */
    public static final int MB_MSG_IGNORED = 1;

    /** Message has been delivered to the client */
    public static final int MB_MSG_DELIVERED = 2;

    /** Message consumed by the client */
    public static final int MB_MSG_CONSUMED = 3;
    public static final int MB_MSG_UNDELIVERABLE = 4;
    public static final int MB_MSG_DEAD = 5;
    public static final int MB_MSG_TXN_PREPARE = 9;
    public static final int MB_MSG_TXN_ROLLEDBACK = 10;
    public static final int MB_MSG_TXN_ACK_RN = 6; /* reserved */
    public static final int MB_MSG_TXN_PREPARE_RN = 7; /* reserved */
    public static final int MB_MSG_TXN_ROLLEDBACK_RN = 8; /* reserved */

    public static String getAckTypeString(int ackType) {
        switch (ackType) {
            case MB_MSG_SENT: return "MSG_SENT";
            case MB_MSG_IGNORED: return "MSG_IGNORED";
            case MB_MSG_DELIVERED: return "MSG_DELIVERED";
            case MB_MSG_CONSUMED: return "MSG_CONSUMED";
            case MB_MSG_UNDELIVERABLE: return "MSG_UNDELIVERABLE";
            case MB_MSG_DEAD: return "MSG_DEAD";
            case MB_MSG_TXN_PREPARE: return "MSG_TXN_PREPARE";
            case MB_MSG_TXN_ROLLEDBACK: return "MSG_TXN_ROLLEDBACK";
            default: return "UNKNOWN";
        }
    }

    //
    // Interest update types for MB_INTEREST_UPDATE messages.
    //

    /** New interest OR durable attach */
    public static final int MB_NEW_INTEREST = 1;

    /** Interest removed */
    public static final int MB_REM_INTEREST = 2;

    /** Durable interest detached */
    public static final int MB_DURABLE_DETACH = 3;

    /** New primary interest for failover queue */
    public static final int MB_NEW_PRIMARY_INTEREST = 4;

    /** New primary interest for failover queue */
    public static final int MB_REM_DURABLE_INTEREST = 5;

    //
    // Election protocol constants for MB_LOCK_REQUEST and
    // MB_LOCK_RESPONSE packets.
    //

    /** Lock request timed out */
    public static final int MB_LOCK_TIMEOUT = -1;

    /** No conflict, permission granted */
    public static final int MB_LOCK_SUCCESS = 0;

    /** Resource already locked, permission denied */
    public static final int MB_LOCK_FAILURE = 1;

    /** Locking conflict, use binary exponential backoff */
    public static final int MB_LOCK_BACKOFF = 2;

    /** Abort and retry resource lock operation */
    public static final int MB_LOCK_TRY_AGAIN = 3;

    // lockResponseStrings is indexed by the above constants.
    public static String[] lockResponseStrings = {
        "Lock granted.",
        "Lock denied.",
        "Lock collision - binary exponential backoff.",
    };

    //
    // Cluster configuration event log stuff -
    // 

    /** Waiting for the central broker's response */
    public static final int MB_EVENT_LOG_WAITING = 0;

    /** Event logged successfully */
    public static final int MB_EVENT_LOG_SUCCESS = 1;

    /** Event could not be logged */
    public static final int MB_EVENT_LOG_FAILURE = 2;

    //
    // Destination update types for MB_DESTINATION_UPDATE messages.
    //

    /** New destination */
    public static final int MB_NEW_DESTINATION = 1;

    /** Destination destroyed */
    public static final int MB_REM_DESTINATION = 2;

    public static final int MB_UPD_DESTINATION = 3;


    public static final int MB_LOCK_MAX_ATTEMPTS = 10;
    public static final int MB_RESOURCE_LOCKING = 0;
    public static final int MB_RESOURCE_LOCKED = 1;
    public static final int MB_EVENT_LOG_CLOCK_SKEW_TOLERANCE =
        120 * 1000; // 2 Minutes clock skew tolerance...

    public static final String CFGSRV_BACKUP_PROPERTY =
        Globals.IMQ + ".cluster.masterbroker.backup";

    public static final String CFGSRV_RESTORE_PROPERTY =
        Globals.IMQ + ".cluster.masterbroker.restore";


    public static final String STORE_PROPERTY_LASTCONFIGSERVER =
                                      "MessageBus.lastConfigServer";
    public static final String STORE_PROPERTY_LASTREFRESHTIME =
                                      "MessageBus.lastRefreshTime";

    public static final String STORE_PROPERTY_LASTSEQ =
                             "ShareConfigRecord.lastSequenceNumber";
    public static final String STORE_PROPERTY_LAST_RESETUUID =
                             "ShareConfigRecord.lastResetUUID";
}
