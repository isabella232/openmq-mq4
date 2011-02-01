#include <jni.h>
#include <unistd.h>
#include <sys/resource.h>
#include "com_sun_messaging_jmq_util_Rlimit.h"

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
 * @(#)com_sun_messaging_jmq_util_Rlimit.c	1.3 07/02/07
 */ 

/*
 * Class:     Rlimit
 * Method:    nativeGetRlimit
 */

JNIEXPORT jobject JNICALL Java_com_sun_messaging_jmq_util_Rlimit_nativeGetRlimit (JNIEnv *env, jobject obj, jint resource )  {

    int rcode;
    struct rlimit rl;
    jclass limitClass = NULL;
    jobject limitObject = NULL;
    jfieldID id = NULL;;

    /*
     * XXX REVISIT 3/19/02 dipol: 'resource' is defined by the Rlimit class
     * and uses the Solaris values. Other versions of Unix may use different
     * values (and Linux does). If we ever port this to other versions of
     * Unix then we must map the passed resource value to the appropriate
     * native value.
     */
    rcode = getrlimit((int)resource, &rl);

    if (rcode != 0) {
        /* should throw an exception */
        return NULL;
    }

    limitClass =
        (*env)->FindClass(env, "com/sun/messaging/jmq/util/Rlimit$Limits");

    if (limitClass != NULL) {
        limitObject = (*env)->AllocObject(env, limitClass);
    } else {
        return NULL;
    }

    if (limitObject != NULL) {
        id = (*env)->GetFieldID(env, limitClass, "current", "J");
        if (id != NULL) {
            if (rl.rlim_cur == RLIM_INFINITY) {
                (*env)->SetLongField(env, limitObject, id,
                    (jlong)com_sun_messaging_jmq_util_Rlimit_RLIM_INFINITY);
            } else {
                (*env)->SetLongField(env, limitObject, id,
                    (jlong)(rl.rlim_cur));
            }
        }
    
        id = (*env)->GetFieldID(env, limitClass, "maximum", "J");
        if (id != NULL) {
            if (rl.rlim_max == RLIM_INFINITY) {
                (*env)->SetLongField(env, limitObject, id,
                    (jlong)com_sun_messaging_jmq_util_Rlimit_RLIM_INFINITY);
            } else {
                (*env)->SetLongField(env, limitObject, id,
                    (jlong)(rl.rlim_max));
            }
        }
    }

    return limitObject;
}
