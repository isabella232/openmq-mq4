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

package com.sun.messaging.bridge.admin.bridgemgr;

import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Properties;
import java.util.Hashtable;

import com.sun.messaging.jmq.admin.bkrutil.BrokerAdminException;
import com.sun.messaging.jmq.admin.apps.broker.CommonCmdRunnerUtil;
import com.sun.messaging.jmq.admin.apps.broker.CommonCmdException;
import com.sun.messaging.jmq.admin.apps.console.event.AdminEventListener;
import com.sun.messaging.jmq.admin.apps.console.event.AdminEvent;
import com.sun.messaging.bridge.service.BridgeCmdSharedReplyData;
import com.sun.messaging.bridge.admin.util.AdminMessageType;
import com.sun.messaging.bridge.admin.bridgemgr.resources.BridgeAdminResources;



/** 
 * This class contains the logic to execute the user commands
 * specified in the BridgeMgrProperties object. It has one
 * public entry point which is the runCommands() method. It
 * is expected to display to the user if the command execution
 * was successful or not.
 * @see  ObjMgr
 *
 */
public class CmdRunner implements BridgeMgrOptions, AdminEventListener {
    private BridgeAdminResources ar = Globals.getBridgeAdminResources();
    private BridgeMgrProperties bridgeMgrProps;
    private BridgeAdmin admin;

    /**
     * Constructor
     */
    public CmdRunner(BridgeMgrProperties props) {
	this.bridgeMgrProps = props;
    } 

    /*
     * Run/execute the user commands specified in the BridgeMgrProperties object.
     */
    public int runCommand() {
	int exitcode = 0;

	/*
	 * Determine type of command and invoke the relevant run method
	 * to execute the command.
	 *
	 */
	String cmd = bridgeMgrProps.getCommand();
	if (cmd.equals(Cmd.LIST))  {
            exitcode = runCommand(bridgeMgrProps);
	} else if (cmd.equals(Cmd.PAUSE))  {
            exitcode = runCommand(bridgeMgrProps);
	} else if (cmd.equals(Cmd.RESUME))  {
            exitcode = runCommand(bridgeMgrProps);
	} else if (cmd.equals(Cmd.START))  {
            exitcode = runCommand(bridgeMgrProps);
	} else if (cmd.equals(Cmd.STOP))  {
            exitcode = runCommand(bridgeMgrProps);
	} else if (bridgeMgrProps.debugModeSet() && cmd.equals(Cmd.DEBUG))  {
            exitcode = runCommand(bridgeMgrProps);
	}
	return (exitcode);
    }

    private int runCommand(BridgeMgrProperties bridgeMgrProps) {
        BridgeAdmin 	broker;
	String		input = null;
	String 		yes, yesShort, no, noShort;

	yes = ar.getString(ar.Q_RESPONSE_YES);
	yesShort = ar.getString(ar.Q_RESPONSE_YES_SHORT);
	no = ar.getString(ar.Q_RESPONSE_NO);
	noShort = ar.getString(ar.Q_RESPONSE_NO_SHORT);

        broker = init();

        boolean force = bridgeMgrProps.forceModeSet();

	// Check for the target argument
	String cmd = bridgeMgrProps.getCommand();
	String commandArg = bridgeMgrProps.getCommandArg();
    String bn = bridgeMgrProps.getBridgeName();
    String bt = bridgeMgrProps.getBridgeType();
    String ln = bridgeMgrProps.getLinkName();
	boolean debugMode = bridgeMgrProps.debugModeSet();

    if (debugMode && cmd.equals(Cmd.DEBUG)) {
        if (broker == null)  {
            Globals.stdErrPrintln("Problem connecting to the broker");
            return (1);
        }
        if (!force) broker = (BridgeAdmin)CommonCmdRunnerUtil.promptForAuthentication(broker);
        String target = bridgeMgrProps.getTargetName();
        Properties optionalProps = bridgeMgrProps.getTargetAttrs();

        Globals.stdOutPrintln("Sending the following DEBUG message:"); 
        if (target != null) {
            BridgeMgrPrinter bmp = new BridgeMgrPrinter(2, 4, null, BridgeMgrPrinter.LEFT, false);
            String[] row = new String[2];
            row[0] = commandArg;
            row[1] = target;
            bmp.add(row);
            bmp.println();
        } else {
            BridgeMgrPrinter bmp = new BridgeMgrPrinter(1, 4,  null, BridgeMgrPrinter.LEFT, false);
            String[] row = new String[1];
            row[0] = commandArg;
            bmp.add(row);
            bmp.println();
        }
        if ((optionalProps != null) && (optionalProps.size() > 0))  {
            Globals.stdOutPrintln("Optional properties:");
            CommonCmdRunnerUtil.printAttrs(optionalProps, true, new BridgeMgrPrinter());
        }

        Globals.stdOutPrintln("To the broker specified by:");
        printBrokerInfo(broker);
        try {
             connectToBroker(broker);
             broker.sendDebugMessage(commandArg, target, optionalProps);
             Hashtable debugHash = broker.receiveDebugReplyMessage();
             if ((debugHash != null) && (debugHash.size() > 0))  {
                 Globals.stdOutPrintln("Data received back from broker:");
                 CommonCmdRunnerUtil.printDebugHash(debugHash);
             } else  {
                 Globals.stdOutPrintln("No additional data received back from broker.\n");
            }
            Globals.stdOutPrintln("DEBUG message sent successfully.");
        } catch (BrokerAdminException bae)  {
             handleBrokerAdminException(bae);
             return (1);
        }
	} else if (CmdArg.BRIDGE.equals(commandArg)) {

        if (broker == null)  {
            Globals.stdErrPrintln(ar.getString(ar.I_BGMGR_BRIDGE_CMD_FAIL, getLocalizedCmding(cmd)));
            return (1);
        }

        if (!force) broker = (BridgeAdmin)CommonCmdRunnerUtil.promptForAuthentication(broker);

        boolean single = false;
        boolean startRet = true;

	    if ((bn == null) || (bn.trim().equals("")))  {
	        if ((bt == null) || (bt.trim().equals("")))  {
                Globals.stdOutPrintln(ar.getString(ar.I_BGMGR_ALL_BRIDGES_CMD_ON_BKR, getLocalizedCmding(cmd)));
		    } else  {
                    Globals.stdOutPrintln(ar.getString(ar.I_BGMGR_ALL_TYPE_BRIDGES_CMD, getLocalizedCmding(cmd)));
                    printBridgeInfo(false);

                    Globals.stdOutPrintln(ar.getString(ar.I_BGMGR_SPECIFY_BKR));
		    }

	    } else  {
            Globals.stdOutPrintln(ar.getString(ar.I_BGMGR_BRIDGE_CMD, getLocalizedCmding(cmd) ));
            printBridgeInfo();
            Globals.stdOutPrintln(ar.getString(ar.I_BGMGR_SPECIFY_BKR));
            single = true;
	    }

        printBrokerInfo(broker);

            try {
                connectToBroker(broker);

            } catch (BrokerAdminException bae)  {
                handleBrokerAdminException(bae);
                if (single) {
                Globals.stdErrPrintln(ar.getString(ar.I_BGMGR_BRIDGE_CMD_FAIL, getLocalizedCmding(cmd)));
                } else {
                Globals.stdErrPrintln(ar.getString(ar.I_BGMGR_BRIDGES_CMD_FAIL, getLocalizedCmding(cmd)));
                }
                return (1);
            }

            if (cmd.equals(Cmd.LIST)) {
                force = true;
            }
            if (!force) {
                input = getUserInput(ar.getString(ar.Q_BRIDGE_CMD_OK, getLocalizedcmd(cmd)), noShort);
                Globals.stdOutPrintln("");
            }

            if (yesShort.equalsIgnoreCase(input) || yes.equalsIgnoreCase(input) || force) {
                try  {
                if (cmd.equals(Cmd.LIST)) {
                    broker.sendCommandMessage(cmd, bn, bt, null,
                                              AdminMessageType.Type.LIST, "LIST", 
                                              BridgeMgrStatusEvent.Type.LIST, 
                                              AdminMessageType.Type.LIST_REPLY, "LIST_REPLY", debugMode);
		            ArrayList<BridgeCmdSharedReplyData> data = broker.receiveListReplyMessage();
                    Iterator<BridgeCmdSharedReplyData> itr = data.iterator();
                    BridgeMgrPrinter bcp = null;
                    BridgeCmdSharedReplyData reply = null;
                    while (itr.hasNext()) {
                        reply = itr.next();
                        bcp = new BridgeMgrPrinter();
                        bcp.copy(reply);
                        bcp.println();
                        Globals.stdOutPrintln("");
                    }
                } else if (cmd.equals(Cmd.START)) {
                    broker.sendCommandMessage(cmd, bn, bt, null,
                                              AdminMessageType.Type.START, "START", 
                                              BridgeMgrStatusEvent.Type.START, 
                                              AdminMessageType.Type.START_REPLY, "START_REPLY");
		            startRet = broker.receiveCommandReplyMessage(cmd, AdminMessageType.Type.START_REPLY, "START_REPLY");
                } else if (cmd.equals(Cmd.STOP)) {
                    broker.sendCommandMessage(cmd, bn, bt, null,
                                              AdminMessageType.Type.STOP, "STOP", 
                                              BridgeMgrStatusEvent.Type.STOP, 
                                              AdminMessageType.Type.STOP_REPLY, "STOP_REPLY");
		            broker.receiveCommandReplyMessage(cmd, AdminMessageType.Type.STOP_REPLY, "STOP_REPLY");
                } else if (cmd.equals(Cmd.RESUME)) {
                    broker.sendCommandMessage(cmd, bn, bt, null,
                                              AdminMessageType.Type.RESUME, "RESUME", 
                                              BridgeMgrStatusEvent.Type.RESUME, 
                                              AdminMessageType.Type.RESUME_REPLY, "RESUME_REPLY");
		            broker.receiveCommandReplyMessage(cmd, AdminMessageType.Type.RESUME_REPLY, "RESUME_REPLY");
                } else if (cmd.equals(Cmd.PAUSE)) {
                    broker.sendCommandMessage(cmd, bn, bt, null,
                                              AdminMessageType.Type.PAUSE, "PAUSE", 
                                              BridgeMgrStatusEvent.Type.PAUSE, 
                                              AdminMessageType.Type.PAUSE_REPLY, "PAUSE_REPLY");
		            broker.receiveCommandReplyMessage(cmd, AdminMessageType.Type.PAUSE_REPLY, "PAUSE_REPLY");
                } else {
                    return 1;
                }

                if (single) {
                    if (cmd.equals(Cmd.START) && !startRet) {
                        Globals.stdOutPrintln(ar.getString(ar.I_BGMGR_BRIDGE_ASYNC_STARTED));
                    } else {
                        Globals.stdOutPrintln(ar.getString(ar.I_BGMGR_BRIDGE_CMD_SUC, getLocalizedcmded(cmd)));
                    }
                } else {
                    if (cmd.equals(Cmd.START) && !startRet) {
                        Globals.stdOutPrintln(ar.getString(ar.I_BGMGR_BRIDGES_ASYNC_STARTED));
                    } else {
                        Globals.stdOutPrintln(ar.getString(ar.I_BGMGR_BRIDGES_CMD_SUC, getLocalizedcmded(cmd)));
                    }
                }

                } catch (BrokerAdminException bae)  {
                    handleBrokerAdminException(bae);
                    if (single) {
                    Globals.stdErrPrintln(ar.getString(ar.I_BGMGR_BRIDGE_CMD_FAIL, getLocalizedCmding(cmd)));
                    } else {
                    Globals.stdErrPrintln(ar.getString(ar.I_BGMGR_BRIDGES_CMD_FAIL, getLocalizedCmding(cmd)));
                    }
                    return (1);
                }

            } else if (noShort.equalsIgnoreCase(input) || no.equalsIgnoreCase(input)) {
                if (single) {
                Globals.stdOutPrintln(ar.getString(ar.I_BGMGR_BRIDGE_CMD_NOOP, getLocalizedcmded(cmd)));
                } else {
                Globals.stdOutPrintln(ar.getString(ar.I_BGMGR_BRIDGES_CMD_NOOP, getLocalizedcmded(cmd)));
                }
                return (0);

            } else {
                Globals.stdOutPrintln(ar.getString(ar.I_UNRECOGNIZED_RES, input));
                Globals.stdOutPrintln("");
                if (single) {
                Globals.stdOutPrintln(ar.getString(ar.I_BGMGR_BRIDGE_CMD_NOOP, getLocalizedcmded(cmd)));
                } else {
                Globals.stdOutPrintln(ar.getString(ar.I_BGMGR_BRIDGES_CMD_NOOP, getLocalizedcmded(cmd)));
                }
                return (1);
            }

	} else if (CmdArg.LINK.equals(commandArg)) {

            if (broker == null)  {
                Globals.stdErrPrintln(ar.getString(ar.I_BGMGR_LINK_CMD_FAIL, getLocalizedCmding(cmd)));
                return (1);
            }

            if (!force) broker = (BridgeAdmin)CommonCmdRunnerUtil.promptForAuthentication(broker);

            Globals.stdOutPrintln(ar.getString(ar.I_BGMGR_LINK_CMD, getLocalizedCmding(cmd)));
            printLinkInfo();

            Globals.stdOutPrintln(ar.getString(ar.I_BGMGR_SPECIFY_BKR));
            printBrokerInfo(broker);

            try {
                connectToBroker(broker);

            } catch (BrokerAdminException bae)  {
                handleBrokerAdminException(bae);
                Globals.stdErrPrintln(ar.getString(ar.I_BGMGR_LINK_CMD_FAIL, getLocalizedCmding(cmd)));
                return (1);
            }

            if (cmd.equals(Cmd.LIST)) {
                force = true;
            }
            if (!force) {
                input = getUserInput(ar.getString(ar.Q_LINK_CMD_OK, getLocalizedcmd(cmd)), noShort);
                Globals.stdOutPrintln("");
            }

            boolean startRet = true;

            if (yesShort.equalsIgnoreCase(input) || yes.equalsIgnoreCase(input) || force) {
                try  {
                if (cmd.equals(Cmd.LIST)) {
                    broker.sendCommandMessage(cmd, bn, bt, ln,
                                              AdminMessageType.Type.LIST, "LIST", 
                                              BridgeMgrStatusEvent.Type.LIST, 
                                              AdminMessageType.Type.LIST_REPLY, "LIST_REPLY", debugMode);
		            ArrayList<BridgeCmdSharedReplyData> data = broker.receiveListReplyMessage();
                    Iterator<BridgeCmdSharedReplyData> itr = data.iterator();
                    BridgeMgrPrinter bcp = null;
                    BridgeCmdSharedReplyData reply = null;
                    while (itr.hasNext()) {
                        reply = itr.next();
                        bcp = new BridgeMgrPrinter();
                        bcp.copy(reply);
                        bcp.println();
                    }
                } else if (cmd.equals(Cmd.START)) {
                    broker.sendCommandMessage(cmd, bn, bt, ln,
                                              AdminMessageType.Type.START, "START", 
                                              BridgeMgrStatusEvent.Type.START, 
                                              AdminMessageType.Type.START_REPLY, "START_REPLY");
		            startRet = broker.receiveCommandReplyMessage(cmd, AdminMessageType.Type.START_REPLY, "START_REPLY");
                } else if (cmd.equals(Cmd.STOP)) {
                    broker.sendCommandMessage(cmd, bn, bt, ln,
                                              AdminMessageType.Type.STOP, "STOP", 
                                              BridgeMgrStatusEvent.Type.STOP, 
                                              AdminMessageType.Type.STOP_REPLY, "STOP_REPLY");
		            broker.receiveCommandReplyMessage(cmd, AdminMessageType.Type.STOP_REPLY, "STOP_REPLY");
                } else if (cmd.equals(Cmd.RESUME)) {
                    broker.sendCommandMessage(cmd, bn, bt, ln,
                                              AdminMessageType.Type.RESUME, "RESUME", 
                                              BridgeMgrStatusEvent.Type.RESUME, 
                                              AdminMessageType.Type.RESUME_REPLY, "RESUME_REPLY");
		            broker.receiveCommandReplyMessage(cmd, AdminMessageType.Type.RESUME_REPLY, "RESUME_REPLY");
                } else if (cmd.equals(Cmd.PAUSE)) {
                    broker.sendCommandMessage(cmd, bn, bt, ln,
                                              AdminMessageType.Type.PAUSE, "PAUSE", 
                                              BridgeMgrStatusEvent.Type.PAUSE, 
                                              AdminMessageType.Type.PAUSE_REPLY, "PAUSE_REPLY");
		            broker.receiveCommandReplyMessage(cmd, AdminMessageType.Type.PAUSE_REPLY, "PAUSE_REPLY");
                } else {
                    return 1;
                }

                if (cmd.equals(Cmd.START) && !startRet) {
                    Globals.stdOutPrintln(ar.getString(ar.I_BGMGR_LINK_ASYNC_STARTED));
                } else {
                    Globals.stdOutPrintln(ar.getString(ar.I_BGMGR_LINK_CMD_SUC, getLocalizedcmded(cmd)));
                }

                } catch (BrokerAdminException bae)  {
		        handleBrokerAdminException(bae);
                Globals.stdErrPrintln(ar.getString(ar.I_BGMGR_LINK_CMD_FAIL, getLocalizedCmding(cmd)));
                return (1);
                }
            } else if (noShort.equalsIgnoreCase(input) || no.equalsIgnoreCase(input)) {
                Globals.stdOutPrintln(ar.getString(ar.I_BGMGR_LINK_CMD_NOOP, getLocalizedcmded(cmd)));
                return (0);

            } else {
                Globals.stdOutPrintln(ar.getString(ar.I_UNRECOGNIZED_RES, input));
                Globals.stdOutPrintln("");
                Globals.stdOutPrintln(ar.getString(ar.I_BGMGR_LINK_CMD_NOOP, getLocalizedcmded(cmd)));
                return (1);
            }
	    }

        broker.close();

        return (0);
    }

    private BridgeAdmin init() {
	BridgeAdmin	broker;

	String  brokerHostPort = bridgeMgrProps.getBrokerHostPort(),
			brokerHostName = CommonCmdRunnerUtil.getBrokerHost(brokerHostPort),
			adminUser = bridgeMgrProps.getAdminUserId(),
			adminPasswd;
	int		brokerPort = -1,
			numRetries = bridgeMgrProps.getNumRetries(),
			receiveTimeout = bridgeMgrProps.getReceiveTimeout();
	boolean		useSSL = bridgeMgrProps.useSSLTransportSet();

	if (bridgeMgrProps.adminDebugModeSet())  {
	    BridgeAdmin.setDebug(true);
	}

	try  {
	    adminPasswd = getPasswordFromFileOrCmdLine(bridgeMgrProps);

	    broker = new BridgeAdmin(brokerHostPort,
					adminUser, adminPasswd, 
					(receiveTimeout * 1000), useSSL);

	    if (useSSL)  {
		broker.setSSLTransportUsed(true);
	    }
	    if (numRetries > 0)  {
		/*
		 * If the number of retries was specified, set it on the
		 * BridgeAdmin object.
		 */
		broker.setNumRetries(numRetries);
	    }
	} catch (BridgeMgrException bce)  {
	    handleBridgeMgrException(bce);

	    return (null);
	} catch (CommonCmdException cce)  {
	    handleBridgeMgrException(cce);

	    return (null);
	} catch (BrokerAdminException bae)  {
	    handleBrokerAdminException(bae);

	    return (null);
	}

    broker.setCheckShutdownReply(false);
	broker.addAdminEventListener(this);

	return (broker);
    }

    private void connectToBroker(BridgeAdmin broker) throws BrokerAdminException {
        broker.connect();
        broker.sendHelloMessage();
        broker.receiveHelloReplyMessage();
    }

    /*
     * Prints out the appropriate error message using 
     * Globals.stdErrPrintln()
     */
    private void handleBrokerAdminException(BrokerAdminException bae)  {
        CommonCmdRunnerUtil.printBrokerAdminException(bae,
                                   Option.BROKER_HOSTPORT,
                                   bridgeMgrProps.debugModeSet());
    }

    private void handleBridgeMgrException(CommonCmdException bce)  {
        CommonCmdRunnerUtil.printCommonCmdException(bce);
    }


    /**
     * Return user input. Return null if an error occurred.
     */
    private String getUserInput(String question)  {
	return (getUserInput(question, null));
    }

    /**
     * Return user input. Return <defaultResponse> if no response ("") was
     * given. Return null if an error occurred.
     */
    private String getUserInput(String question, String defaultResponse)  {
        return CommonCmdRunnerUtil.getUserInput(question, defaultResponse); 
    }    

    private void printBrokerInfo(BridgeAdmin broker) {
        CommonCmdRunnerUtil.printBrokerInfo(broker, new BridgeMgrPrinter());
    }

    private void printBridgeInfo() {
        printBridgeInfo(true);
    }

    private void printBridgeInfo(boolean printName) {
	BridgeMgrPrinter bcp = new BridgeMgrPrinter(1, 4, "-");
	String[] row = new String[1];
	String value, title;

	if (printName)  {
	    title = ar.getString(ar.I_BGMGR_BRIDGE_NAME);
	    value = bridgeMgrProps.getBridgeName();
	} else  {
	    title = ar.getString(ar.I_BGMGR_BRIDGE_TYPE);
	    value = bridgeMgrProps.getBridgeType();
	}

	row[0] = title;
	bcp.addTitle(row);

	row[0] = value;
	bcp.add(row);

	bcp.println();
    }

    private void printLinkInfo() {
	BridgeMgrPrinter bcp = new BridgeMgrPrinter(2, 4, "-");
	String[] row = new String[2];
	String ln = bridgeMgrProps.getLinkName(),
	        bn = bridgeMgrProps.getBridgeName();

	row[0] = ar.getString(ar.I_BGMGR_BRIDGE_NAME);
	row[1] = ar.getString(ar.I_BGMGR_LINK_NAME);
	bcp.addTitle(row);

	row[0] = bn;
	row[1] = ln;
	bcp.add(row);

	bcp.println();
    }

    /*
     * Get password from either the passfile or -p option.
     * In some future release, the -p option will go away
     * leaving the passfile the only way to specify the 
     * password (besides prompting the user for it).
     * -p has higher precendence compared to -passfile.
     */
    private String getPasswordFromFileOrCmdLine(BridgeMgrProperties bridgeMgrProps) 
		throws CommonCmdException  {
        String passwd = bridgeMgrProps.getAdminPasswd(),
	       passfile = bridgeMgrProps.getAdminPassfile();
	
	if (passwd != null)  {
	    return (passwd);
	}
	return CommonCmdRunnerUtil.getPasswordFromFile(passfile, PropName.PASSFILE_PASSWD, bridgeMgrProps);

    }


    public void adminEventDispatched(AdminEvent e)  {
    if (e instanceof BridgeMgrStatusEvent)  {
        BridgeMgrStatusEvent be = (BridgeMgrStatusEvent)e;
        int type = be.getType();

        if (type == BridgeMgrStatusEvent.BROKER_BUSY)  {
            CommonCmdRunnerUtil.printBrokerBusyEvent(be);
        }
    }
    }


    private String getLocalizedCmding(String cmd) {
	if (cmd.equals(Cmd.LIST))  {
           return ar.getString(ar.I_BGMGR_CMD_Listing);
	} else if (cmd.equals(Cmd.PAUSE))  {
           return ar.getString(ar.I_BGMGR_CMD_Pausing);
	} else if (cmd.equals(Cmd.RESUME))  {
           return ar.getString(ar.I_BGMGR_CMD_Resuming);
	} else if (cmd.equals(Cmd.START))  {
           return ar.getString(ar.I_BGMGR_CMD_Starting);
	} else if (cmd.equals(Cmd.STOP))  {
           return ar.getString(ar.I_BGMGR_CMD_Stopping);
	}
	return (cmd);
    }

    private String getLocalizedcmd(String cmd) {
	if (cmd.equals(Cmd.LIST))  {
           return ar.getString(ar.I_BGMGR_CMD_list);
	} else if (cmd.equals(Cmd.PAUSE))  {
           return ar.getString(ar.I_BGMGR_CMD_pause);
	} else if (cmd.equals(Cmd.RESUME))  {
           return ar.getString(ar.I_BGMGR_CMD_resume);
	} else if (cmd.equals(Cmd.START))  {
           return ar.getString(ar.I_BGMGR_CMD_start);
	} else if (cmd.equals(Cmd.STOP))  {
           return ar.getString(ar.I_BGMGR_CMD_stop);
	}
	return (cmd);
    }

    private String getLocalizedcmded(String cmd) {
	if (cmd.equals(Cmd.LIST))  {
           return ar.getString(ar.I_BGMGR_CMD_listed);
	} else if (cmd.equals(Cmd.PAUSE))  {
           return ar.getString(ar.I_BGMGR_CMD_paused);
	} else if (cmd.equals(Cmd.RESUME))  {
           return ar.getString(ar.I_BGMGR_CMD_resumed);
	} else if (cmd.equals(Cmd.START))  {
           return ar.getString(ar.I_BGMGR_CMD_started);
	} else if (cmd.equals(Cmd.STOP))  {
           return ar.getString(ar.I_BGMGR_CMD_stopped);
	}
	return (cmd);
    }

}
