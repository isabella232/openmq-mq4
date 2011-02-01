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
 * @(#)BasicConnectionFactory.java	1.8 06/28/07
 */ 

package com.sun.messaging;

import com.sun.messaging.jmq.jmsclient.UnifiedConnectionImpl;
import com.sun.messaging.jmq.ClientConstants;
import java.util.Properties;
import javax.jms.*;
import java.io.IOException;

/**
 * A <code>BasicConnectionFactory</code> encapsulates Sun MQ specific configuration information
 * for Sun MQ <code>ConnectionFactory</code> objects and is used to create Connections with
 * a Sun MQ Java Message Service (JMS) provider.
 *
 * @see         javax.jms.ConnectionFactory javax.jms.ConnectionFactory
 * @see         com.sun.messaging.ConnectionConfiguration com.sun.messaging.ConnectionConfiguration
 */
public class BasicConnectionFactory extends com.sun.messaging.AdministeredObject implements javax.jms.ConnectionFactory {

    /** The default basename for AdministeredObject initialization */
    private static final String defaultsBase = "ConnectionFactory";

    /** The default Connection Handler classname for clients */
    private static final String DEFAULT_IMQ_CONNECTION_HANDLER =
                    "com.sun.messaging.jmq.jmsclient.protocol.tcp.TCPStreamHandler";

    /** The default ConnectionHandler Label */
    private static final String DEFAULT_IMQ_CONNECTION_HANDLER_LABEL = "Connection Handler Classname";

    /** The default Username and Password for Sun MQ client authentication */
    private static final String DEFAULT_IMQ_USERNAME_PASSWORD = "guest";

    /** The default Username Label */
    private static final String DEFAULT_IMQ_USERNAME_LABEL = "Default Username";

    /** The default Password Label */
    private static final String DEFAULT_IMQ_PASSWORD_LABEL = "Default Password";

    /** The default Acknowledgement Timeout for responses from the MQ Message Service */
    private static final String DEFAULT_IMQ_ACKTIMEOUT = "0";

    /** The default AckTimeout Label*/
    private static final String DEFAULT_IMQ_ACKTIMEOUT_LABEL = "Acknowledgement Timeout";

    /** The default valuse for JMSXProperties */
    private static final boolean DEFAULT_JMSXPROP_VALUE = false;

    /** Indicate that admin connections are created */
    private transient boolean adminConnectionCreated = false;

    /** Indicate that admin connections created use a special key */
    private transient boolean adminKeyUsed = false;

    /** The type of connections created */
    private transient String connectionType = ClientConstants.CONNECTIONTYPE_NORMAL;
 
    /**
     * Constructs a BasicConnectionFactory with the default configuration.
     * 
     */
    public BasicConnectionFactory() {
        super(defaultsBase);
    }
 
    /**
     * Constructs a BasicConnectionFactory with the specified configuration.
     * 
     */
    protected BasicConnectionFactory(String defaultsBase) {
        super(defaultsBase);
    }

     private void readObject(java.io.ObjectInputStream ois)
        throws IOException, ClassNotFoundException
    {
        ois.defaultReadObject();
        adminConnectionCreated = false;
        adminKeyUsed = false;
        connectionType = ClientConstants.CONNECTIONTYPE_NORMAL;
    }

 
    /**
     * Creates a Connection with the default user identity. The default user identity
     * is defined by the <code>ConnectionFactory</code> properties
     * <code><b>imqDefaultUsername</b></code> and <code><b>imqDefaultPassword</b></code>
     * 
     * @return a newly created Connection.
     * 
     * @exception JMSException if a JMS error occurs.
     * @see ConnectionConfiguration#imqDefaultUsername
     * @see ConnectionConfiguration#imqDefaultPassword
     */  
    public Connection createConnection() throws JMSException {
        return createConnection(getProperty(ConnectionConfiguration.imqDefaultUsername),
                                     getProperty(ConnectionConfiguration.imqDefaultPassword));
    }

    /**
     * Creates a Connection with a specified user identity.
     * 
     * @param username the caller's user name
     * @param password the caller's password
     * 
     * @return a newly created connection.
     * 
     * @exception JMSException if a JMS error occurs.
     */  
    public Connection createConnection(String username, String password) throws JMSException {
        return new UnifiedConnectionImpl(getCurrentConfiguration(), username, password, getConnectionType());
    }

    /**
     * Sets the type of connections created by this <code>BasicConnectionFactory</code>.
     *   
     * @param type The type of connections created by this
     *             <code>BasicConnectionFactory</code>.
     */  
    public final void setConnectionType(String type) {
        if (ClientConstants.CONNECTIONTYPE_ADMINKEY.equals(type) ||
            ClientConstants.CONNECTIONTYPE_ADMIN.equals(type)) {
            connectionType = type;
        } else {
            connectionType = ClientConstants.CONNECTIONTYPE_NORMAL;
        }
    }
 
    /**
     * Returns the type of connections created by this <code>BasicConnectionFactory</code>.
     *   
     * @return The type of connections created by this
     *             <code>BasicConnectionFactory</code>.
     */  
    public final String getConnectionType() {
        //Protect against NPE if a BasicConnectionFactory Object is restored
        //using a non-standard way
        if (connectionType == null) {
            connectionType = ClientConstants.CONNECTIONTYPE_NORMAL;
        }
        return connectionType;
    }
 
    /**
     * Returns a pretty printed version of the provider specific
     * information for this ConnectionFactory object.
     *
     * @return the pretty printed string.
     */
    public String toString() {
        return ("Oracle GlassFish(tm) Server MQ ConnectionFactory" + super.toString());
    }

    /**
     * Sets the minimum <code>BasicConnectionFactory</code> configuration defaults
     * required to connect to the Sun MQ Message Service.
     */  
    public void setDefaultConfiguration() {
        configuration = new Properties();
        configurationTypes = new Properties();
        configurationLabels = new Properties();

        configuration.put(ConnectionConfiguration.imqConnectionHandler,
                                DEFAULT_IMQ_CONNECTION_HANDLER);
        configurationTypes.put(ConnectionConfiguration.imqConnectionHandler,
                                AO_PROPERTY_TYPE_PROPERTYOWNER);
        configurationLabels.put(ConnectionConfiguration.imqConnectionHandler,
                                DEFAULT_IMQ_CONNECTION_HANDLER_LABEL);
 
        configuration.put(ConnectionConfiguration.imqDefaultUsername,
                                DEFAULT_IMQ_USERNAME_PASSWORD);
        configurationTypes.put(ConnectionConfiguration.imqDefaultUsername,
                                AO_PROPERTY_TYPE_STRING);
        configurationLabels.put(ConnectionConfiguration.imqDefaultUsername,
                                DEFAULT_IMQ_USERNAME_LABEL);
 
        configuration.put(ConnectionConfiguration.imqDefaultPassword,
                                DEFAULT_IMQ_USERNAME_PASSWORD);
        configurationTypes.put(ConnectionConfiguration.imqDefaultPassword,
                                AO_PROPERTY_TYPE_STRING);
        configurationLabels.put(ConnectionConfiguration.imqDefaultPassword,
                                DEFAULT_IMQ_PASSWORD_LABEL);
 
        configuration.put(ConnectionConfiguration.imqAckTimeout,
                                DEFAULT_IMQ_ACKTIMEOUT);
        configurationTypes.put(ConnectionConfiguration.imqAckTimeout,
                                AO_PROPERTY_TYPE_INTEGER);
        configurationLabels.put(ConnectionConfiguration.imqAckTimeout,
                                DEFAULT_IMQ_ACKTIMEOUT_LABEL);
    }

}
