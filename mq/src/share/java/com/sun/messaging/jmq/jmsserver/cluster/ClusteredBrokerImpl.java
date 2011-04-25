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

package com.sun.messaging.jmq.jmsserver.cluster;

import com.sun.messaging.jmq.io.MQAddress;
import com.sun.messaging.jmq.util.UID;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;

   /**
    * Non-HA implementation of ClusteredBroker.
    */
public class ClusteredBrokerImpl implements ClusteredBroker 
{

    protected Logger logger = Globals.getLogger();   
    protected BrokerResources br = Globals.getBrokerResources();

        /**
         * Name associated with this broker. For non-ha clusters
         * it is of the form broker# and is not the same across
         * all brokers in the cluster (although it is unique on
         * this broker).
         */
        String brokerName = null;

        /**
         * The portmapper for this broker.
         */
        MQAddress address = null;

        /**
         * The instance name of this broker
         */
        transient String instanceName = null;

        /**
         * Is this the local (in this vm) broker.
         */
        boolean local = false;

        /**
         * Is this broker setup by confguration (vs dynamic).
         */
        boolean configed = false;

        /**
         * Current status of the broker.
         */
        Integer status = new Integer(BrokerStatus.BROKER_UNKNOWN);

        /**
         * Current state of the broker.
         */
        BrokerState state = BrokerState.INITIALIZING;

        /** 
         * Protocol version of this broker.
         */
        Integer version = new Integer(0);


        /**
         * Broker SessionUID for this broker.
         * This uid changes on each restart of the broker.
         */
         UID brokerSessionUID = null;

         /**
          * has brokerID been generated
          */
         boolean isgen = false;

         ClusterManagerImpl parent = null;
      
        /** 
         * Create a instace of ClusteredBroker.
         *
         * @param url the portampper address of this broker
         * @param local is this broker local
         */
        public ClusteredBrokerImpl(ClusterManagerImpl parent,
                                   MQAddress url, boolean local, UID id)
        {
            this.parent = parent;
            this.local = local;
            this.address = url;
            brokerSessionUID = id;
            synchronized (this) {
                if (local) {
                    this.brokerName = Globals.getBrokerID();
                    this.instanceName = Globals.getConfigName();
                }
                if (this.brokerName == null) {
                    isgen = true;
                    parent.brokerindx ++;
                    this.brokerName = "broker" + parent.brokerindx;
                }
            }
        }

        private ClusteredBrokerImpl() {
        }

        public boolean equals(Object o) {
            if (! (o instanceof ClusteredBroker)) 
                return false;
            return this.getBrokerName().equals(((ClusteredBroker)o).getBrokerName());
        }

        public int hashCode() {
             return this.getBrokerName().hashCode();
        }


        /** 
         * String representation of this broker.
         */
        public String toString() {
            if (!local)
                return brokerName + "(" + address + ")";
             return brokerName + "* (" + address + ")";
                   
        }

        /**
         * a unique identifier assigned to the broker
         * (randomly assigned).<P>
         *
         * This name is only unique to this broker. The
         * broker at this URL may be assigned a different name
         * on another broker in the cluster.
         *
         * @return the name of the broker
         */
        public String getBrokerName()
        {
             return brokerName;
        }
    
        /**
         * the URL to the portmapper of this broker.
         * @return the URL of this broker
         */
        public MQAddress getBrokerURL()
        {
             return address;
        }

        /**
         * @return the instance name of this broker, null if not available
         */
        public String getInstanceName() {
            return instanceName;
        }

        /**
         * @param Set the instance name of this broker, can be null
         */
        public void setInstanceName(String instName) {
             instanceName = instName;
        }
 
        /**
         * sets the URL to the portmapper of this broker.
         * @param address the URL of this broker
         * @throws UnsupportedOperationException if this change
         *         can not be made on this broker
         */
        public void setBrokerURL(MQAddress address) throws Exception
        {
             MQAddress oldaddress = this.address;
             this.address = address;
             parent.brokerChanged(ClusterReason.ADDRESS_CHANGED, 
                    this.getBrokerName(), oldaddress, this.address, null, null);
        }

    
        /**
         * Is this the address of the broker running in this
         * VM.
         * @return true if this is the broker running in the
         *         current vm
         */
        public boolean isLocalBroker()
        {
            return local;
        }
    
        /**
         * gets the status of the broker.
         *
         * @see BrokerStatus
         * @return the status of the broker
         */
        public synchronized int getStatus() {
            return status.intValue();
        } 
    
        /**
         * gets the protocol version of the broker .
         * @return the current cluster protocol version (if known)
         *        or 0 if not known
         */
        public synchronized int getVersion()
        {
            return (version == null ? 0 : version.intValue());
        }  
    
        /**
         * sets the protocol version of the broker .
         * @param version the current cluster protocol version (if known)
         *        or 0 if not known
         * @throws UnsupportedOperationException if the change is not allowed
         */
        public synchronized void setVersion(int version) throws Exception 

        {
            Integer oldversion = this.version;
            this.version = new Integer(version);
            parent.brokerChanged(ClusterReason.VERSION_CHANGED, 
                  this.getBrokerName(), oldversion, this.version, null, null);
        }  

    
        /**
         * sets the status of the broker (and notifies listeners).
         *
         * @param status the status to set
         * @param userData optional user data associated with the status change
         * @see ConfigListener
         */
        public void setStatus(int newstatus, Object userData)
        {
            Integer oldstatus = null;
            UID uid = null;

            // ok - for standalone case, adjust so that LINK_DOWN=DOWN
            if (BrokerStatus.getBrokerIsDown(newstatus))
                newstatus = BrokerStatus.setBrokerLinkIsDown(newstatus);
            else if (BrokerStatus.getBrokerLinkIsDown(newstatus))
                newstatus = BrokerStatus.setBrokerIsDown(newstatus);
            else if (BrokerStatus.getBrokerLinkIsUp(newstatus))
                newstatus = BrokerStatus.setBrokerIsUp(newstatus);
            else if (BrokerStatus.getBrokerIsUp(newstatus))
                newstatus = BrokerStatus.setBrokerLinkIsUp(newstatus);

            synchronized (this) {
                if (this.status.intValue() == newstatus)
                    return;
                oldstatus = this.status;
                this.status = new Integer(newstatus);
                uid = getBrokerSessionUID();
            }
            // notify
            parent.brokerChanged(ClusterReason.STATUS_CHANGED, 
                   this.getBrokerName(), oldstatus, this.status, 
                   uid, userData);

            // ok for non-HA we also can not expect notification that the state
            // has changed - deal w/ it here
            try {
                if (BrokerStatus.getBrokerIsUp(newstatus))
                    setState(BrokerState.OPERATING);
                if (BrokerStatus.getBrokerIsDown(newstatus))
                    setState(BrokerState.SHUTDOWN_COMPLETE);
            } catch (Exception ex) {
                logger.logStack(Logger.DEBUG,"Error setting state ", ex);
            }

        }

        /**
         * Updates the BROKER_UP bit flag on status.
         * 
         * @param userData optional user data associated with the status change
         * @param up setting for the bit flag (true/false)
         */
        public void setBrokerIsUp(boolean up, UID brokerSession, Object userData)
        {
        
            UID uid = brokerSession;
            Integer oldstatus = null;
            Integer newstatus = null;
            synchronized (this) {
                if (!up && !uid.equals(getBrokerSessionUID())) {
                    logger.log(logger.INFO, br.getKString(
                        BrokerResources.I_DOWN_STATUS_ON_BROKER_SESSION,
                        "[BrokerSession:"+uid+"]", this.toString()));
                    oldstatus = new Integer(BrokerStatus.BROKER_INDOUBT);
                    newstatus = BrokerStatus.setBrokerIsDown(oldstatus);

                } else {

                    oldstatus = this.status;
                    int newStatus = 0;
                    if (up) {
                        newStatus = BrokerStatus.setBrokerIsUp
                                        (this.status.intValue());
                    } else {
                        newStatus = BrokerStatus.setBrokerIsDown
                                        (this.status.intValue());
                    }
                    this.status = new Integer(newStatus);
                    uid = getBrokerSessionUID();
                    newstatus = this.status;
                }
            }
            // notify
            parent.brokerChanged(ClusterReason.STATUS_CHANGED, 
                  this.getBrokerName(), oldstatus, newstatus, uid, userData);
            try {
                if (up)
                    setState(BrokerState.OPERATING);
                else
                    setState(BrokerState.SHUTDOWN_COMPLETE);
            } catch (Exception ex) {
                logger.logStack(Logger.DEBUG,"Error setting state ", ex);
            }

        }

        /**
         * Updates the BROKER_LINK_UP bit flag on status.
         * 
         * @param userData optional user data associated with the status change
         * @param up setting for the bit flag (true/false)
         */
        public void setBrokerLinkUp(boolean up, Object userData)
        {
            // on non-HA clusters status should always be set to UP if
            // LINK_UP
        
            Integer oldstatus = null;
            UID uid = null;
            synchronized (this) {
                oldstatus = this.status;
                uid = getBrokerSessionUID();

                int newStatus = 0;
                if (up) {
                   newStatus = BrokerStatus.setBrokerLinkIsUp
                        (BrokerStatus.setBrokerIsUp(this.status.intValue()));
                } else {
                   newStatus = BrokerStatus.setBrokerLinkIsDown
                        (BrokerStatus.setBrokerIsDown(this.status.intValue()));
                }
                this.status = new Integer(newStatus);
            }
            // notify
            parent.brokerChanged(ClusterReason.STATUS_CHANGED, 
                  this.getBrokerName(), oldstatus, this.status,
                  uid, userData);
            try {
                if (up)
                    setState(BrokerState.OPERATING);
                else
                    setState(BrokerState.SHUTDOWN_COMPLETE);
            } catch (Exception ex) {
                logger.logStack(Logger.DEBUG,"Error setting state ", ex);
            }

        }


        /**
         * Updates the BROKER_INDOUBT bit flag on status.
         * 
         * @param userData optional user data associated with the status change
         * @param up setting for the bit flag (true/false)
         */
        public void setBrokerInDoubt(boolean up, Object userData)
        {
            UID uid = (UID)userData;
            Integer oldstatus = null;
            Integer newstatus = null;
            synchronized (this) {
                if (up && !uid.equals(getBrokerSessionUID())) {
                    logger.log(logger.INFO, br.getKString(
                        BrokerResources.I_INDOUBT_STATUS_ON_BROKER_SESSION,
                        "[BrokerSession:"+uid+"]", this.toString()));
                    oldstatus = new Integer(BrokerStatus.ACTIVATE_BROKER);
                    newstatus = BrokerStatus.setBrokerInDoubt(oldstatus);
                } else {
                    oldstatus = this.status;
                    int newStatus = 0;
                    uid = getBrokerSessionUID();
                    if (up) {
                        newStatus = BrokerStatus.setBrokerInDoubt
                                        (this.status.intValue());
                    } else {
                        newStatus = BrokerStatus.setBrokerNotInDoubt
                                        (this.status.intValue());
                    }
                    this.status = new Integer(newStatus);
                    newstatus =this.status;
                }
            }
            // notify
            parent.brokerChanged(ClusterReason.STATUS_CHANGED, 
                  this.getBrokerName(), oldstatus, newstatus, uid, userData);

        }

        /**
         * marks this broker as destroyed. This is equivalent to setting
         * the status of the broker to DOWN.
         *
         * @see BrokerStatus#DOWN
         */
        public void destroy() {
            synchronized (this) {
                status = new Integer(BrokerStatus.setBrokerIsDown(
                              status.intValue()));
            }
            if (!isConfigBroker()) {
                 parent.removeFromAllBrokers(getBrokerName());
            }
            parent.brokerChanged(ClusterReason.REMOVED, getBrokerName(),
                  this, null, getBrokerSessionUID(), null);
        }

        /**
         * gets the state of the broker .
         *
         * @throws BrokerException if the state can not be retrieve
         * @return the current state
         */
        public BrokerState getState()
        {
            return state;
        }

        /**
         * sets the state of the broker  (and notifies any listeners).
         * @throws IllegalAccessException if the broker does not have
         *               permission to change the broker (e.g. one broker
         *               is updating anothers state).
         * @throws IllegalStateException if the broker state changed
         *               unexpectedly.
         * @throws IllegalArgumentException if the state is not supported
         *               for this cluster type.
         * @param state the state to set for this broker
         * @see ConfigListener
         */
        public void setState(BrokerState state)
             throws IllegalAccessException, IllegalStateException,
                IllegalArgumentException
        {
            BrokerState oldState = this.state;
            this.state = state;
            parent.brokerChanged(ClusterReason.STATE_CHANGED, 
                   this.getBrokerName(), oldState, this.state, null,  null);
        }


        /**
         * Is the broker static or dynmically configured
         */
        public boolean isConfigBroker()
        {
             return configed;
        }

        /**
         * Is the broker static or dynmically configured
         */
        protected void setConfigBroker(boolean config)
        {
             configed = config;
        }


        public synchronized UID getBrokerSessionUID() {
            return brokerSessionUID;
        }

        public synchronized void setBrokerSessionUID(UID session) {
            brokerSessionUID = session;
        }

        public boolean isBrokerIDGenerated()
        {
            return isgen;
        }
}
