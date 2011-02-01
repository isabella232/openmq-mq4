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
 * @(#)LowPriorityComparator.java	1.6 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.core;

import java.util.Comparator;
import com.sun.messaging.jmq.jmsserver.core.PacketReference;

class LowPriorityComparator implements Comparator
{
    public int compare(Object o1, Object o2) {
        if (o1 instanceof PacketReference && o2 instanceof PacketReference) {
                PacketReference ref1 = (PacketReference) o1;
                PacketReference ref2 = (PacketReference) o2;

                if (ref1.equals(ref2))
                    return 0;

                // compare priority
                long dif = ref1.getPriority() - ref2.getPriority();

                if (dif == 0)
                    dif = ref1.getTimestamp() - ref2.getTimestamp();

                // then sequence
                if (dif == 0)
                    dif = ref1.getSequence() - ref2.getSequence();

                if (dif == 0)
                    dif = ref1.getCreateTime() - ref2.getCreateTime();

                if (dif < 0) return -1;
                if (dif > 0) return 1;
                return o1.hashCode() - o2.hashCode();
        } else {
            assert false;
            return o1.hashCode() - o2.hashCode();
        }
    }
    public boolean equals(Object o) {
        return super.equals(o);
    }
    public int hashCode() {
        return super.hashCode();
    }
    
}
