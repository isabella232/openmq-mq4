/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2000-2011 Oracle and/or its affiliates. All rights reserved.
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
 */ 

package com.sun.messaging.jmq.jmsserver.cluster.ha;

import com.sun.messaging.jmq.util.UID;
import com.sun.messaging.jmq.io.MQAddress;
import com.sun.messaging.jmq.jmsserver.cluster.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.persist.Store;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.service.TakingoverTracker;
import com.sun.messaging.jmq.jmsserver.persist.TakeoverStoreInfo;

/**
 */
public class RepHAClusteredBrokerImpl extends ClusteredBrokerImpl
implements HAClusteredBroker 
{
    UID storeSession = null;

    public RepHAClusteredBrokerImpl(RepHAClusterManagerImpl parent,
                                    MQAddress url, boolean local, UID id) {
        super(parent, url, local, id);
    }

    public String toString() {
        if (!isLocalBroker()) {
            return "-"+getInstanceName()+getBrokerURL() + ":" + getState() +
                "[StoreSession:" + storeSession + ", BrokerSession:"+getBrokerSessionUID()+"]"+  ":"+
                 BrokerStatus.toString(getStatus());
        }
        return "*" +getInstanceName() + "@" + getBrokerURL() + ":" + getState() + 
                "[StoreSession:" + storeSession + ", BrokerSession:"+getBrokerSessionUID()+"]"+  ":"+
                 BrokerStatus.toString( getStatus());
                   
    }

    /**
     * Gets the UID associated with the store session.
     *
     * @return the store session uid (if known)
     */
    public synchronized UID getStoreSessionUID() {
            return storeSession;
    }

    public synchronized void setStoreSessionUID(UID uid) {
            storeSession = uid;
    }

    public synchronized String getNodeName() throws BrokerException {
        String instn = getInstanceName();
        UID storeSession = getStoreSessionUID();
        return Store.makeReplicationNodeName(instn, storeSession);
    }

    /**
     * Retrieves the id of the broker who has taken over this broker's store.
     *
     * @return the broker id of the takeover broker (or null if there is not
     *      a takeover broker).
     */
    public String getTakeoverBroker() throws BrokerException {
        return null;
    }

    public long getHeartbeat() throws BrokerException {
        return 0L;
    }
 
    public long updateHeartbeat() throws BrokerException {
        throw new BrokerException("Operation not supported");
    }

    public long updateHeartbeat(boolean reset) throws BrokerException {
        throw new BrokerException("Operation not supported");
    }

    /**
     * Attempt to take over the persistent state of the broker.
     * 
     * @param force force the takeover
     * @param tracker for tracking takingover stages
     * @throws IllegalStateException if this broker can not takeover.
     * @return data associated with previous broker
     */
    public TakeoverStoreInfo takeover(boolean force,
                                      Object extraInfo,
                                      TakingoverTracker tracker)
                                      throws BrokerException {

        String targetRepHostPort = (String)extraInfo;
        Store store = Globals.getStore(); 
        return store.takeoverBrokerStore(getInstanceName(),
                     storeSession, targetRepHostPort, tracker);
    }

    /**
     * Remove takeover broker ID and set state to OPERATING
     *
     * @throws Exception if operation fails
     */
    public void resetTakeoverBrokerReadyOperating() throws Exception {
        throw new BrokerException("Operation not supported");
    }

    /**
     * Set another broker's state to FAILOVER_PROCESSED if same store session
     *
     * @param storeSession the store session that the failover processed
     * @throws Exception if operation fails
     */
    public void setStateFailoverProcessed(UID storeSession) throws Exception {
    }

    /**
     * Set another broker's state to FAILOVER_FAILED if same broker session
     *
     * @param brokerSession the broker session that the failover failed
     * @throws Exception if operation fails
     */
    public void setStateFailoverFailed(UID brokerSession) throws Exception {
    }
}
