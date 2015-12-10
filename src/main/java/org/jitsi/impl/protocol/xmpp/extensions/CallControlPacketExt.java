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
 * A packet extension used to advertise the phone number and the pin for a
 * Jitsi Meet conference, when the SIP gateway is enabled.
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
     * Sets the phone number of the Jitsi Meet conference.
     *
     * @param phoneNumber the phone number of the Jitsi Meet Conference.
     */
    public void setPhoneNumber(String phoneNumber)
    {
        setAttribute("phone", phoneNumber);
    }

    /**
     * Returns the phone number of the Jitsi Meet conference.
     */
    public String getPhoneNumber()
    {
        return (String) getAttribute("phone");
    }
    /**
     * Sets the pin number of the Jitsi Meet conference.
     *
     * @param pin the name of the document to set.
     */
    public void setPin(String pin)
    {
        setAttribute("pin", pin);
    }

    /**
     * Returns the pin number of the Jitsi Meet conference.
     */
    public String getPin()
    {
        return (String) getAttribute("pin");
    }

    /**
     * Return new <tt>CallControlPacketExt</tt> instance for the given
     * <tt>CallControl</tt>.
     *
     * @param callControl the <tt>CallControl</tt> to convert into a
     * <tt>CallControlPacketExt</tt>.
     */
    public static CallControlPacketExt forCallControl(CallControl callControl)
    {
        CallControlPacketExt ext = new CallControlPacketExt();

        ext.setPhoneNumber(callControl.getNumber());
        ext.setPin(callControl.getPin());

        return ext;
    }
}
