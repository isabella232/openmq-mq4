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

import com.sun.messaging.jmq.io.MQAddress;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.util.log.*;
import com.sun.messaging.jmq.util.UID;
import java.util.*;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.config.*;
import com.sun.messaging.jmq.jmsserver.persist.Store;
import com.sun.messaging.jmq.jmsserver.persist.StoreManager;
import com.sun.messaging.jmq.jmsserver.cluster.*;
import com.sun.messaging.jmq.jmsserver.multibroker.BrokerInfo;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.Globals;

// XXX FOR TEST CLASS
import java.io.*;


/**
 * This class extends ClusterManagerImpl and is used to obtain and
 * distribute cluster information in an HA cluster.
 */

public class RepHAClusterManagerImpl extends ClusterManagerImpl 
implements ClusterManager
{

    /**
     * The brokerid associated with the local broker.
     * The local broker is running in the current vm.
     */

    private String localBrokerId = null;

    private UID localStoreSessionUID = null;


   /**
    */
   public RepHAClusterManagerImpl() 
       throws BrokerException
   {
       super();
   }


   /**
    * Returns if the cluster is "highly available".
    *
    * @return true if the cluster is HA
    * @throws RuntimeException if called before the cluster has
    *         been initialized by calling ClusterManager.setMQAddress
    * @see ClusterManager#setMQAddress
    */
   public boolean isHA() {
       if (!initialized)
           throw new RuntimeException("Cluster not initialized");

       return false;
   }


   /**
    * Reload the cluster properties from config 
    *
    */
   public void reloadConfig() throws BrokerException {
       if (!initialized)
           throw new RuntimeException("Cluster not initialized");

       String[] props = { CLUSTERURL_PROPERTY };
       config.reloadProps(Globals.getConfigName(), props, false);
   }

   /**
    * Method which initializes the broker cluster. (Called by
    * ClusterManager.setMQAddress()).
    *
    * @param address the address for the portmapper
    *
    * @see ClusterManager#setMQAddress
    * @throws BrokerException if something goes wrong during intialzation 
    */
   public String initialize(MQAddress address) 
        throws BrokerException
   {
        logger.log(Logger.DEBUG, "initializingCluster at " + address);

        // make sure master broker is not set
        String mbroker = config.getProperty(CONFIG_SERVER);

        if (mbroker != null) {
            logger.log(Logger.WARNING, 
                   Globals.getBrokerResources().getKString(
                   BrokerResources.W_HA_MASTER_BROKER_NOT_ALLOWED, 
                   CONFIG_SERVER+"="+mbroker));
        }

        if (!StoreManager.isConfiguredBDBStore()) {
            throw new BrokerException(
                 Globals.getBrokerResources().getKString(
                 BrokerResources.E_HA_CLUSTER_INVALID_STORE_TYPE, Store.BDB_STORE_TYPE));
        }
        return super.initialize(address);
   }

   protected ClusteredBroker newClusteredBroker(MQAddress URL,
                                   boolean isLocal, UID sid)
                                   throws BrokerException {
       RepHAClusteredBrokerImpl b = new RepHAClusteredBrokerImpl(this, URL, isLocal, sid);
       return b;
   }

   /**
    * Retrieve the broker that creates the specified store session ID.
    * @param uid store session ID
    * @return the broker ID
    */
   public String getStoreSessionCreator(UID uid)
   {
       return null;
   }

   protected ClusteredBroker updateBrokerOnActivation(ClusteredBroker broker,
                                                      Object userData) {
       ((RepHAClusteredBrokerImpl)broker).setStoreSessionUID(
           ((BrokerInfo)userData).getBrokerAddr().getStoreSessionUID());
       return broker;
   }

   protected ClusteredBroker updateBrokerOnDeactivation(ClusteredBroker broker,
                                                      Object userData) {
       return broker;
   }

   public ClusteredBroker getBrokerByNodeName(String nodeName) 
   throws BrokerException {

       if (!initialized) {
           throw new RuntimeException("Cluster not initialized");
       }
       RepHAClusteredBrokerImpl cb = null;
       synchronized (allBrokers) {
           Iterator itr = allBrokers.values().iterator();
           while (itr.hasNext()) {

               cb = (RepHAClusteredBrokerImpl)itr.next();
               String instn = cb.getInstanceName();
               UID ss = cb.getStoreSessionUID();
               if (instn != null && ss != null) {
                   if (Store.makeReplicationNodeName(
                       instn, ss).equals(nodeName)) {
                       return cb;
                   }
               }
           }
       }
       return null;
   }

   /**
    * Gets the session UID associated with the local broker
    *
    * @return the broker session uid (if known)
    */
   public UID getStoreSessionUID()
   {
       if (localStoreSessionUID == null) {
           localStoreSessionUID = ((RepHAClusteredBrokerImpl)getLocalBroker()).getStoreSessionUID();
       }
       return localStoreSessionUID;
   }

}
