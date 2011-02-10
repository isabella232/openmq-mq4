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
 * @(#)BrokerCmdProperties.java	1.32 06/28/07
 */ 

package com.sun.messaging.jmq.admin.apps.broker;

import java.util.Properties;
import java.util.Enumeration;

/**
 * This class encapsulates the information that the user
 * has provided to perform any JMQ Broker Administration
 * task. It contains properties that describe:
 * <UL>
 * <LI>the type of command
 * <LI>the command argument
 * <LI>the destination type
 * <LI>the target name
 * <LI>the target attributes
 * <LI>etc..
 * </UL>
 *
 * This class has a number of convenience methods to extract
 * the information above. Currently, each of these methods
 * has a get() and a get(commandIndex) version. The version
 * that takes a commandIndex is currently not supported.
 * It is for handling the case where multiple commands are
 * stored in one BrokerCmdProperties object.
 *
 * @see		BrokerCmdOptions
 */
public class BrokerCmdProperties extends Properties
			implements BrokerCmdOptions  {
    
    public BrokerCmdProperties()  {
	super();
    }

    /**
     * Returns the command string. e.g. <EM>list</EM>.
     *
     * @return	The command string
     */
    public String getCommand()  {
	return (getCommand(-1));
    }
    /**
     * Returns the command string. e.g. <EM>list</EM>.
     *
     * @param	commandIndex	Index for specifyng which
     *		command (for the case where multiple commands
     *		exist in the same BrokerCmdProperties object).
     *				
     * @return	The command string
     */
    public String getCommand(int commandIndex)  {
	if (commandIndex == -1)  {
	    return (getProperty(PROP_NAME_CMD));
	}

	return (null);
    }

    /**
     * Returns the command argument string. e.g. <EM>svc</EM>.
     *
     * @return	The command argument string
     */
    public String getCommandArg()  {
	return (getCommandArg(-1));
    }
    /**
     * Returns the command argument string. e.g. <EM>svc</EM>.
     *
     * @param	commandIndex	Index for specifyng which
     *		command (for the case where multiple commands
     *		exist in the same BrokerCmdProperties object).
     *				
     * @return	The command argument string
     */
    public String getCommandArg(int commandIndex)  {
	if (commandIndex == -1)  {
	    return (getProperty(PROP_NAME_CMDARG));
	}

	return (null);
    }

    /**
     * Returns the number of commands.
     *
     * @return	The number of commands.
     */
    public int getCommandCount()  {
	return (1);
    }

    /**
     * Returns the destination type
     *
     * @return	The destination type
     */
    public String getDestType()  {
	return (getDestType(-1));
    }
    /**
     * Returns the destination type
     *
     * @param	commandIndex	Index for specifyng which
     *		command (for the case where multiple commands
     *		exist in the same BrokerCmdProperties object).
     *				
     * @return	The destination type
     */
    public String getDestType(int commandIndex)  {
	if (commandIndex == -1)  {
	    return (getProperty(PROP_NAME_OPTION_DEST_TYPE));
	}

	return (null);
    }

    /**
     * Returns the destination name
     *
     * @return	The destination name
     */
    public String getDestName()  {
	return (getDestName(-1));
    }
    /**
     * Returns the destination name
     *
     * @param	commandIndex	Index for specifyng which
     *		command (for the case where multiple commands
     *		exist in the same BrokerCmdProperties object).
     *				
     * @return	The destination name
     */
    public String getDestName(int commandIndex)  {
	if (commandIndex == -1)  {
	    return (getProperty(PROP_NAME_OPTION_DEST_NAME));
	}

	return (null);
    }

    /**
     * Returns the service name
     *
     * @return	The service name
     */
    public String getServiceName()  {
	return (getServiceName(-1));
    }
    /**
     * Returns the service name
     *
     * @param	commandIndex	Index for specifyng which
     *		command (for the case where multiple commands
     *		exist in the same BrokerCmdProperties object).
     *				
     * @return	The service name
     */
    public String getServiceName(int commandIndex)  {
	if (commandIndex == -1)  {
	    return (getProperty(PROP_NAME_OPTION_SVC_NAME));
	}

	return (null);
    }

    /**
     * Returns the client ID
     *
     * @return  The client ID
     */
    public String getClientID()  {
        return (getClientID(-1));
    }
    /**
     * Returns the client ID
     *
     * @param   commandIndex    Index for specifyng which
     *          command (for the case where multiple commands
     *          exist in the same BrokerCmdProperties object).
     *                          
     * @return  The client ID
     */
    public String getClientID(int commandIndex)  {
        if (commandIndex == -1)  {
            return (getProperty(PROP_NAME_OPTION_CLIENT_ID));
        }

        return (null);
    }

    /**
     * Returns the object lookup name.
     *
     * @return	The object lookup name.
     */
    public String getTargetName()  {
	return (getTargetName(-1));
    }
    /**
     * Returns the object lookup name.
     *
     * @param	commandIndex	Index for specifyng which
     *		command (for the case where multiple commands
     *		exist in the same BrokerCmdProperties object).
     *				
     * @return	The object lookup name.
     */
    public String getTargetName(int commandIndex)  {
	if (commandIndex == -1)  {
	    return (getProperty(PROP_NAME_OPTION_TARGET_NAME));
	}

	return (null);
    }

    /**
     * Returns the broker host:port.
     *
     * @return  The broker host:port.
     */
    public String getBrokerHostPort()  {
        return (getBrokerHostPort(-1));
    }
    /**
     * Returns the broker host:port.
     *
     * @param   commandIndex    Index for specifyng which
     *          command (for the case where multiple commands
     *          exist in the same BrokerCmdProperties object).
     *                          
     * @return  The broker host:port.
     */
    public String getBrokerHostPort(int commandIndex)  {
        if (commandIndex == -1)  {
            return (getProperty(PROP_NAME_OPTION_BROKER_HOSTPORT));
        }

        return (null);
    }

    /**
     * Returns the admin user id.
     *
     * @return	The admin user id.
     */
    public String getAdminUserId()  {
	return (getAdminUserId(-1));
    }
    /**
     * Returns the admin user id.
     *
     * @param	commandIndex	Index for specifyng which
     *		command (for the case where multiple commands
     *		exist in the same BrokerCmdProperties object).
     *
     * @return	The admin user id.
     */
    public String getAdminUserId(int commandIndex)  {
	if (commandIndex == -1)  {
	    return (getProperty(PROP_NAME_OPTION_ADMIN_USERID));
	}

	return (null);
    }

    /**
     * Returns the admin passwd.
     *
     * @return	The admin passwd.
     */
    public String getAdminPasswd()  {
	return (getAdminPasswd(-1));
    }
    /**
     * Returns the admin passwd.
     *
     * @param	commandIndex	Index for specifyng which
     *		command (for the case where multiple commands
     *		exist in the same BrokerCmdProperties object).
     *
     * @return	The admin passwd.
     */
    public String getAdminPasswd(int commandIndex)  {
	if (commandIndex == -1)  {
	    return (getProperty(PROP_NAME_OPTION_ADMIN_PASSWD));
	}

	return (null);
    }

    /**
     * Returns the admin passfile (file containing admin
     * password).
     *
     * @return	The passfile.
     */
    public String getAdminPassfile()  {
	String s = getProperty(PROP_NAME_OPTION_ADMIN_PASSFILE);
	return (s);
    }

    /**
     * Returns a Properties object containing the properties
     * specified for the target object. The properties are
     * normalized.
     *
     * @return	A Properties object containing properties
     *		for the target object.
     */
    public Properties getTargetAttrs()  {
	return (getTargetAttrs(-1));
    }
    /**
     * Returns a Properties object containing the properties
     * specified for the target object. The properties are
     * normalized.
     *
     * @param	commandIndex	Index for specifyng which
     *		command (for the case where multiple commands
     *		exist in the same BrokerCmdProperties object).
     *				
     * @return	A Properties object containing properties
     *		for the target object.
     */
    public Properties getTargetAttrs(int commandIndex)  {
	Properties	props = new Properties();
	String		targetAttrs = PROP_NAME_OPTION_TARGET_ATTRS + ".";
	int		targetAttrsLen = targetAttrs.length();

	if (commandIndex == -1)  {
            for (Enumeration e = propertyNames();  e.hasMoreElements() ;) {
		String propName = (String)e.nextElement();

		if (propName.startsWith(targetAttrs))  {
		    String newPropName, value;

		    newPropName = propName.substring(targetAttrsLen);
		    value = getProperty(propName);

		    props.put(newPropName, value);
		}
            }
	    
	    return (props);
	}

	return (null);
    }

    public void setTargetAttr(String targetAttr, String value)  {
	String	targetAttrs = PROP_NAME_OPTION_TARGET_ATTRS + ".";

	/*
	 * Introduce new property:
	 *	target.attrs.<targetAttr>=<value>
	 */
	setProperty((targetAttrs + targetAttr), value);
    }

    public void removeTargetAttr(String targetAttr)  {
	String	targetAttrs = PROP_NAME_OPTION_TARGET_ATTRS + ".";

	/*
	 * Remove property:
	 *	target.attrs.<targetAttr>
	 */
	remove(targetAttrs + targetAttr);
    }


    /**
     * Returns a Properties object containing the system
     * properties to set.
     *
     * @return	A Properties object containing system properties
     *		to set
     */
    public Properties getSysProps()  {
	return (getSysProps(-1));
    }
    /**
     * Returns a Properties object containing the system
     * properties to set.
     *
     * @param	commandIndex	Index for specifyng which
     *		command (for the case where multiple commands
     *		exist in the same BrokerCmdProperties object).
     *				
     * @return	A Properties object containing system properties
     *		to set
     */
    public Properties getSysProps(int commandIndex)  {
	Properties	props = new Properties();
	String		targetAttrs = PROP_NAME_OPTION_SYS_PROPS + ".";
	int		targetAttrsLen = targetAttrs.length();

	if (commandIndex == -1)  {
            for (Enumeration e = propertyNames();  e.hasMoreElements() ;) {
		String propName = (String)e.nextElement();

		if (propName.startsWith(targetAttrs))  {
		    String newPropName, value;

		    newPropName = propName.substring(targetAttrsLen);
		    value = getProperty(propName);

		    props.put(newPropName, value);
		}
            }
	    
	    return (props);
	}

	return (null);
    }




    /**
     * Returns whether force mode was specified by the user.
     * Force mode is when no user interaction will be needed.
     * i.e. if storing an object, and an object with the same
     * lookup name already exists, no overwrite confirmation
     * will be asked, the object is overwritten.
     *
     * @return	true if force mode is set, false if force mode
     *		was not set.
     */
    public boolean forceModeSet()  {
	String s = getProperty(PROP_NAME_OPTION_FORCE);

	if (s == null)  {
	    return (false);
	}

	if (s.equalsIgnoreCase(Boolean.TRUE.toString()))  {
	    return (true);
	} else if (s.equalsIgnoreCase(Boolean.FALSE.toString()))  {
	    return (false);
	}

	return (false);
    }

    /**
     * Returns the input filename specified by the user.
     *
     * @return	Input filename specified by the user
     */
    public String getInputFileName()  {
	return(getProperty(PROP_NAME_OPTION_INPUTFILE));
    }

    /**
     * Returns whether "-adminkey" was used.
     * This is to support the private "-adminkey" option
     * It is used to support authentication when shutting down the
     * broker via the NT services' "Stop" command.
     *
     * @return	True if "-adminkey" was used. False otherwise.
     */
    public boolean isAdminKeyUsed()  {
	String s = getProperty(PROP_NAME_OPTION_ADMINKEY);

	if (s == null)  {
	    return (false);
	}

	if (s.equalsIgnoreCase(Boolean.TRUE.toString()))  {
	    return (true);
	} else if (s.equalsIgnoreCase(Boolean.FALSE.toString()))  {
	    return (false);
	}

	return (false);
    }

    /**
     * Returns the metric interval in seconds.
     * This has a default value of 5 seconds.
     *
     * @return	The metric interval in seconds.
     */
    public long getMetricInterval()  {
	String s = getProperty(PROP_NAME_OPTION_METRIC_INTERVAL);
	long ret;

	if (s == null)  {
	    return (DEFAULT_METRIC_INTERVAL);
	}

	try  {
	    ret = Long.parseLong(s);
	} catch (NumberFormatException nfe)  {
	    ret = DEFAULT_METRIC_INTERVAL;
	}

	return (ret);
    }

    /**
     * Returns the metric type.
     *
     * @return	The metric type
     */
    public String getMetricType()  {
	String s = getProperty(PROP_NAME_OPTION_METRIC_TYPE);

	return (s);
    }

    /**
     * Returns the single attribute name.
     * This is to support <.getattr -attr> hidden command.
     *
     * @return  The single attribute name
     */
    public String getSingleTargetAttr()  {
        String s = getProperty(PROP_NAME_OPTION_SINGLE_TARGET_ATTR);

        return (s);
    }

    /**
     * Returns whether debug mode was specified.
     * This is not a public/documented mode. It's main use
     * is as a back door to get debug information.
     *
     * @return	true if debug mode is set, false if debug mode
     *		was not set.
     */
    public boolean debugModeSet()  {
	String s = getProperty(PROP_NAME_OPTION_DEBUG);

	if (s == null)  {
	    return (false);
	}

	if (s.equalsIgnoreCase(Boolean.TRUE.toString()))  {
	    return (true);
	} else if (s.equalsIgnoreCase(Boolean.FALSE.toString()))  {
	    return (false);
	}

	return (false);
    }

    /**
     * Returns whether admin debug mode was specified.
     * This is not a public/documented mode. It's main use
     * is as a back door to get debug information about the
     * admin connection made to the broker.
     *
     * @return	true if admin debug mode is set, false if 
     *		admin debug mode was not set.
     */
    public boolean adminDebugModeSet()  {
	String s = getProperty(PROP_NAME_OPTION_ADMIN_DEBUG);

	if (s == null)  {
	    return (false);
	}

	if (s.equalsIgnoreCase(Boolean.TRUE.toString()))  {
	    return (true);
	} else if (s.equalsIgnoreCase(Boolean.FALSE.toString()))  {
	    return (false);
	}

	return (false);
    }

    /**
     * Returns whether no-check mode was specified.
     * This is not a public/documented mode. It's main use
     * is as a back door to force imqcmd to accept undocumented
     * attributes to be set.
     *
     * @return	true if no-check mode is set, false if no-check mode
     *		was not set.
     */
    public boolean noCheckModeSet()  {
	String s = getProperty(PROP_NAME_OPTION_NOCHECK);

	if (s == null)  {
	    return (false);
	}

	if (s.equalsIgnoreCase(Boolean.TRUE.toString()))  {
	    return (true);
	} else if (s.equalsIgnoreCase(Boolean.FALSE.toString()))  {
	    return (false);
	}

	return (false);
    }

    /**
     * Returns whether show-temporary-destination mode is set.
     *
     * @return  true if show-temporary-destination mode is set, 
     *          false if show-temporary-destination mode was not set.
     */
    public boolean showTempDestModeSet()  {
        String s = getProperty(PROP_NAME_OPTION_TEMP_DEST);

        if (s == null)  {
            return (false);
        }

        if (s.equalsIgnoreCase(Boolean.TRUE.toString()))  {
            return (true);
        } else if (s.equalsIgnoreCase(Boolean.FALSE.toString()))  {
            return (false);
        }

        return (false);
    }

    /**
     * Returns whether the "use ssl transport" flag is set.
     *
     * @return  true if "use ssl transport" flag is set, 
     *          false if it is not.
     */
    public boolean useSSLTransportSet()  {
        String s = getProperty(PROP_NAME_OPTION_SSL);

        if (s == null)  {
            return (false);
        }

        if (s.equalsIgnoreCase(Boolean.TRUE.toString()))  {
            return (true);
        } else if (s.equalsIgnoreCase(Boolean.FALSE.toString()))  {
            return (false);
        }

        return (false);
    }

    /**
     * Returns the number of metric samples.
     *
     * @return	The number of metric samples.
     */
    public int getMetricSamples()  {
	String s = getProperty(PROP_NAME_OPTION_METRIC_SAMPLES);
	int ret;

	if (s == null)  {
	    return (-1);
	}

	try  {
	    ret = Integer.parseInt(s);
	} catch (NumberFormatException nfe)  {
	    ret = -1;
	}

	return (ret);
    }

    /**
     * Returns the receive timeout.
     *
     * @return	The receive timeout.
     */
    public int getReceiveTimeout()  {
	String s = getProperty(PROP_NAME_OPTION_RECV_TIMEOUT);
	int ret;

	if (s == null)  {
	    return (-1);
	}

	try  {
	    ret = Integer.parseInt(s);
	} catch (NumberFormatException nfe)  {
	    ret = -1;
	}

	return (ret);
    }

    /**
     * Returns the number of 'receive()' retries.
     *
     * @return	The number of 'receive()' retries.
     */
    public int getNumRetries()  {
	String s = getProperty(PROP_NAME_OPTION_NUM_RETRIES);
	int ret;

	if (s == null)  {
	    return (-1);
	}

	try  {
	    ret = Integer.parseInt(s);
	} catch (NumberFormatException nfe)  {
	    ret = -1;
	}

	return (ret);
    }

    /**
     * Returns the Service name. Normally, -n/getTargetName()
     * is used for that, but for 'imqcmd list cxn', it
     * is necessary to create a new option/property
     * for service since -n will be for specifying the
     * connection id.
     *
     * @return	The service name.
     */
    public String getService()  {
	String s = getProperty(PROP_NAME_OPTION_SERVICE);
	return (s);
    }

    /**
     * Returns the pause type if specified.
     *
     * @return	The pause type
     */
    public String getPauseType()  {
	String s = getProperty(PROP_NAME_OPTION_PAUSE_TYPE);
	return (s);
    }

    /**
     * Returns the reset type if specified.
     *
     * @return	The reset type
     */
    public String getResetType()  {
	String s = getProperty(PROP_NAME_OPTION_RESET_TYPE);
	return (s);
    }

    /**
     * Returns whether the noFailover flag was set.
     * This is relevant only when a shutdown command
     * is used.
     *
     * @return	True if nofailover was specified, False if
     * it wasn't.
     */
    public boolean noFailoverSet()  {
	String s = getProperty(PROP_NAME_OPTION_NO_FAILOVER);

	if (s == null)  {
	    return (false);
	}

	if (s.equalsIgnoreCase(Boolean.TRUE.toString()))  {
	    return (true);
	} else if (s.equalsIgnoreCase(Boolean.FALSE.toString()))  {
	    return (false);
	}

	return (false);
    }

    /**
     * Returns the grace period/time before shutdown
     *
     * @return	The grace period/time before shutdown
     */
    public int getTime()  {
	String s = getProperty(PROP_NAME_OPTION_TIME);
	int ret;

	if (s == null)  {
	    return (-1);
	}

	try  {
	    ret = Integer.parseInt(s);
	} catch (NumberFormatException nfe)  {
	    ret = -1;
	}

	return (ret);
    }

    /**
     * Returns the shutdown wait interval in milliseconds.
     * (uses the same option (-int) as metric interval.
     * This option has a default value of 100 milliseconds.
     *
     * @return	The shutdown wait interval in milliseconds.
     */
    public long getShutdownWaitInterval()  {
	String s = getProperty(PROP_NAME_OPTION_METRIC_INTERVAL);
	long ret;

	if (s == null)  {
	    return (DEFAULT_SHUTDOWN_WAIT_INTERVAL);
	}

	try  {
	    ret = Long.parseLong(s);
	} catch (NumberFormatException nfe)  {
	    ret = DEFAULT_SHUTDOWN_WAIT_INTERVAL;
	}

	return (ret);
    }

    public Long getStartMsgIndex()  {
	String s = getProperty(PROP_NAME_OPTION_START_MSG_INDEX);
	long ret;

	if (s == null)  {
	    return (null);
	}

	try  {
	    ret = Long.parseLong(s);
	} catch (NumberFormatException nfe)  {
	    ret = 0;
	}

	return (new Long(ret));
    }

    public Long getMaxNumMsgsRetrieved()  {
	String s = getProperty(PROP_NAME_OPTION_MAX_NUM_MSGS_RET);
	long ret;

	if (s == null)  {
	    return (null);
	}

	try  {
	    ret = Long.parseLong(s);
	} catch (NumberFormatException nfe)  {
	    ret = 0;
	}

	return (new Long(ret));
    }

    public String getMsgID()  {
	String s = getProperty(PROP_NAME_OPTION_MSG_ID);

	return (s);
    }
}
