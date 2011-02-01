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
 * @(#)GenericDAOFactory.java	1.4 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.persist.jdbc;

import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.persist.jdbc.ext.TMLogRecordDAO;
import com.sun.messaging.jmq.jmsserver.persist.jdbc.ext.TMLogRecordDAOJMSBG;
import com.sun.messaging.jmq.jmsserver.persist.jdbc.ext.JMSBGDAO;
import com.sun.messaging.jmq.jmsserver.persist.jdbc.ext.JMSBGDAOImpl;

/**
 * Factory for generic implementation of DAO object.
 */
public class GenericDAOFactory extends DAOFactory {

    public VersionDAO getVersionDAO() throws BrokerException {

        if ( versionDAO == null ) {
            versionDAO = new VersionDAOImpl();
        }
        return versionDAO;
    }

    public BrokerDAO getBrokerDAO() throws BrokerException {

        if ( brokerDAO == null ) {
            brokerDAO = new BrokerDAOImpl();
        }
        return brokerDAO;
    }

    public StoreSessionDAO getStoreSessionDAO() throws BrokerException {

        if ( storeSessionDAO == null ) {
            storeSessionDAO = new StoreSessionDAOImpl();
        }
        return storeSessionDAO;
    }

    public PropertyDAO getPropertyDAO() throws BrokerException {

        if ( propertyDAO == null ) {
            propertyDAO = new PropertyDAOImpl();
        }
        return propertyDAO;
    }

    public MessageDAO getMessageDAO() throws BrokerException {

        if ( messageDAO == null ) {
            messageDAO = new MessageDAOImpl();
        }
        return messageDAO;
    }

    public DestinationDAO getDestinationDAO() throws BrokerException {

        if ( destinationDAO == null ) {
            destinationDAO = new DestinationDAOImpl();
        }
        return destinationDAO;
    }

    public ConsumerDAO getConsumerDAO() throws BrokerException {

        if ( consumerDAO == null ) {
            consumerDAO = new ConsumerDAOImpl();
        }
        return consumerDAO;
    }

    public ConsumerStateDAO getConsumerStateDAO() throws BrokerException {

        if ( consumerStateDAO == null ) {
            consumerStateDAO = new ConsumerStateDAOImpl();
        }
        return consumerStateDAO;
    }

    public ConfigRecordDAO getConfigRecordDAO() throws BrokerException {

        if ( configRecordDAO == null ) {
            configRecordDAO = new ConfigRecordDAOImpl();
        }
        return configRecordDAO;
    }

    public TransactionDAO getTransactionDAO() throws BrokerException {

        if ( transactionDAO == null ) {
            transactionDAO = new TransactionDAOImpl();
        }
        return transactionDAO;
    }

    public TMLogRecordDAO getTMLogRecordDAOJMSBG() throws BrokerException {

        if (tmLogRecordDAOJMSBG == null) {
            tmLogRecordDAOJMSBG = new TMLogRecordDAOJMSBG();
        }
        return tmLogRecordDAOJMSBG;
    }

    public JMSBGDAO getJMSBGDAO() throws BrokerException {

        if ( jmsbgDAO == null ) {
             jmsbgDAO = new JMSBGDAOImpl();
        }
        return jmsbgDAO;
    }
}
