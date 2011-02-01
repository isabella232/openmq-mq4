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
 * @(#)ObjAdminEvent.java	1.18 06/28/07
 */ 

package com.sun.messaging.jmq.admin.apps.console.event;

import java.util.EventObject;
import java.util.Properties;
import java.util.Vector;
import com.sun.messaging.jmq.admin.objstore.ObjStore;
import com.sun.messaging.jmq.admin.objstore.ObjStoreManager;
import com.sun.messaging.jmq.admin.objstore.ObjStoreAttrs;

/**
 * Event class indicating some action related to
 * Administered Object Management is needed.
 *<P>
 * The fields of this event include the various pieces of information
 * needed for object management tasks.
 */
public class ObjAdminEvent extends AdminEvent {
    /*
     * ObjAdminEvent event types
     */
    public final static int	ADD_OBJSTORE		= 1;
    public final static int	UPDATE_OBJSTORE		= 2;
    public final static int	DELETE_OBJSTORE		= 3;
    public final static int	ADD_DESTINATION 	= 4;
    public final static int	UPDATE_DESTINATION 	= 5;
    public final static int	ADD_CONN_FACTORY	= 6;
    public final static int	UPDATE_CONN_FACTORY	= 7;
    public final static int	UPDATE_CREDENTIALS 	= 8;

    /*
     * Types of admin objects that can be created/updated.
     */
    public final static int     QUEUE			= 1;
    public final static int     TOPIC			= 2;
    public final static int     QCF			= 3;
    public final static int     TCF			= 4;
    public final static int     XAQCF			= 5;
    public final static int     XATCF			= 6;
    public final static int     CF			= 7;
    public final static int     XACF			= 8;

    private transient ObjStore		os = null;
    private ObjStoreAttrs	osa = null;
    private transient ObjStoreManager	osMgr = null;
    private String		lookupName = null;
    private String		type = null;
    private int   		destType;
    private int 		factoryType;
    private String		id = null;
    private boolean 		readOnly = false;
    private Properties		objProps;
    private boolean		okAction = true;
    private boolean		connect = true;
    private Vector 		missingInfo;

    /**
     * Creates an instance of ObjAdminEvent
     * @param source the object where the event originated
     */
    public ObjAdminEvent(Object source) {
	super(source);
    }

    /**
     * Creates an instance of ObjAdminEvent
     * @param source the object where the event originated
     * @type the event type
     */
    public ObjAdminEvent(Object source, int type) {
	super(source, type);
    }

    /*
     * Set object store attributes. This may be needed for
     * object store creation for example.
     * @param osa Object Store Attributes to set on this
     * event object.
     */
    public void setObjStoreAttrs(ObjStoreAttrs osa)  {
	this.osa = osa;
    }

    /*
     * Return object store attributes. 
     * @return Object Store Attributes.
     */
    public ObjStoreAttrs getObjStoreAttrs()  {
	return (this.osa);
    }

    /*
     * Set object store.
     * @param os Object Store to set on this event object.
     */
    public void setObjStore(ObjStore os)  {
	this.os = os;
    }
    /*
     * Returns the object store.
     * @return The Object Store.
     */
    public ObjStore getObjStore()  {
	return (this.os);
    }

    /*
     * Set new object store id (for an update).
     * @param id id Object Store to set on this event object.
     */
    public void setObjStoreID(String id)  {
	this.id = id;
    }

    /*
     * Get new object store id (for an update).
     * @return The Obj Store ID
     */
    public String getObjStoreID()  {
	return this.id;
    }

    public void setReadOnly(boolean readOnly)  {
	this.readOnly = readOnly;
    }

    public boolean isReadOnly()  {
	return this.readOnly;
    }

    /*
     * Set dest destination type.
     * @param type Type of destination
     */
    public void setDestinationType(int destType)  {
	this.destType = destType;
    }
    /*
     * Returns the destination type
     * @return The Destination Type
     */
    public int getDestinationType()  {
	return (this.destType);
    }

    /*
     * Set connectio factory type.
     * @param type Type of destination
     */
    public void setFactoryType(int factoryType)  {
	this.factoryType = factoryType;
    }
    /*
     * Returns the factory type
     * @return The Factory Type
     */
    public int getFactoryType()  {
	return (this.factoryType);
    }

    /*
     * Set lookup name.
     * @param lookupName The Lookup Name
     */
    public void setLookupName(String lookupName)  {
	this.lookupName = lookupName;
    }
    /*
     * Returns the lookup name.
     * @return The Lookup Name
     */
    public String getLookupName()  {
	return (this.lookupName);
    }

    /*
     * Set object store manager.
     * @param osMgr Object Store Manager to set on this event object.
     */
    public void setObjStoreManager(ObjStoreManager osMgr)  {
	this.osMgr = osMgr;
    }
    /*
     * Returns the object store manager.
     * @return The Object Store Manager.
     */
    public ObjStoreManager getObjStoreManager()  {
	return (osMgr);
    }

    /*
     * Set object properties.
     * @param objProps Properties to set on the Administered Object.
     */
    public void setObjProperties(Properties objProps)  {
	this.objProps = objProps;
    }
    /*
     * Returns the object properties;
     * @return The Object Properties;
     */
    public Properties getObjProperties()  {
	return (objProps);
    }

    /*
     * Set missing authorization info
     * @param missingInfo Vector of security properties that are missing.
     */
    public void setMissingAuthInfo(Vector missingInfo)  {
	this.missingInfo = missingInfo;
    }
    /*
     * Returns the Vector 
     * @return The Vector of Properties;
     */
    public Vector getMissingAuthInfo()  {
	return (missingInfo);
    }

    /*
     * Set whether this event is trigerred by an 'OK' action.
     * This information is used to determine whether the originating
     * dialog (if one was involved) needs to be hidden.
     *
     * @param b True if this is an 'OK' action, false
     *		otherwise.
     */
    public void setOKAction(boolean b)  {
	this.okAction = b;
    }
    /*
     * Returns whether this event is trigerred by an 'OK' action.
     * @return True if this is an 'OK' action, false
     *		otherwise.
     */
    public boolean isOKAction()  {
	return (okAction);
    }

    /*
     * Set whether to attempt to connect to the object store
     * when adding/updating it.
     *
     * @param connect True if try to connect, false
     *		otherwise.
     */
    public void setConnectAttempt(boolean connect)  {
	this.connect = connect;
    }
    /*
     * Returns whether or not to attempt to connect to the
     * object store when adding/updating it.
     * @return True if attempt to connect, false
     *		otherwise.
     */
    public boolean isConnectAttempt()  {
	return (connect);
    }
}
