/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jitsi.impl.protocol.xmpp.extensions;

import net.java.sip.communicator.impl.protocol.jabber.extensions.*;
import org.jitsi.jicofo.*;

/**
 * A packet extension used to advertise the name for shared Etherpad document
 * in Jitsi Meet conference.
 *
 * @author George Politis
 */
public class CallControlPacketExt
    extends AbstractPacketExtension
{
    /**
     * XML namespace of this packets extension.
     */
    public static final String NAMESPACE = "http://jitsi.org/jitmeet/call-control";

    /**
     * XML element name of this packets extension.
     */
    public static final String ELEMENT_NAME = "call-control";

    /**
     * Creates new instance of <tt>CallControlPacketExt</tt>.
     */
    public CallControlPacketExt()
    {
        super(NAMESPACE, ELEMENT_NAME);
    }

    /**
     * Sets the <tt>name</tt> of Etherpad document to be shared in Jitsi Meet
     * conference.
     *
     * @param name the name of the document to set.
     */
    public void setPhoneNumber(String phoneNumber)
    {
        setAttribute("phone", phoneNumber);
    }

    /**
     * Returns the name of shared Etherpad document.
     */
    public String getPhoneNumber()
    {
        return (String) getAttribute("phone");
    }
    /**
     * Sets the <tt>name</tt> of Etherpad document to be shared in Jitsi Meet
     * conference.
     *
     * @param name the name of the document to set.
     */
    public void setPin(String pin)
    {
        setAttribute("pin", pin);
    }

    /**
     * Returns the name of shared Etherpad document.
     */
    public String getPin()
    {
        return (String) getAttribute("pin");
    }

    /**
     * Return new Etherpad packet extension instance with given document
     * <tt>name</tt>.
     *
     * @param name the name of shared Etherpad document.
     */
    public static CallControlPacketExt forCallControl(CallControl callControl)
    {
        CallControlPacketExt ext = new CallControlPacketExt();

        ext.setPhoneNumber(callControl.getNumber());
        ext.setPin(callControl.getPin());

        return ext;
    }
}
