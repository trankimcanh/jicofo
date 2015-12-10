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
package org.jitsi.jicofo;

import java.util.*;
import java.util.concurrent.*;
import com.twilio.sdk.*;
import com.twilio.sdk.resource.list.*;
import com.twilio.sdk.resource.instance.*;
import com.twilio.sdk.resource.factory.*;
import org.apache.http.*;
import org.jitsi.util.*;
import org.apache.http.message.*;

/**
 * @author George Politis
 */
public class TwilioCallControlManager
    implements CallControlManager
{
    /**
     */
    private final ExecutorService pool = Executors.newFixedThreadPool(10);

    /**
     * The logger used by this instance.
     */
    private final static Logger logger
        = Logger.getLogger(TwilioCallControlManager.class);

    /**
     * The name of configuration property that specifies the user name used by
     * the focus to login to XMPP server.
     */
    public static final String TWILIO_ACCOUNT_SID_PNAME
        = "org.jitsi.jicofo.TWILIO_ACCOUNT_SID";

    /**
     * The name of configuration property that specifies the user name used by
     * the focus to login to XMPP server.
     */
    public static final String TWILIO_AUTH_TOKEN_PNAME
        = "org.jitsi.jicofo.TWILIO_AUTH_TOKEN";

    public static final String TWILIO_TRUNK_SID_PNAME
        = "org.jitsi.jicofo.TWILIO_TRUNK_SID";

    /**
     * This is a map of allocated phone numbers.
     */
    private Map<String, CallControl> allocated
        = new HashMap<String, CallControl>();

    /**
     * This is a list of available phone numbers.
     */
    private List<String> available = new ArrayList<String>();

    /**
     */
    private final TwilioRestClient client;

    private final String trunkSid;
    /**
     * Ctor.
     */
    public TwilioCallControlManager(
            String accountSid, String authToken, String trunkSid)
    {
        client = new TwilioRestClient(accountSid, authToken);
        this.trunkSid = trunkSid;
    }

    /**
     */
    class CallControlRequest
            implements Runnable
    {

        final private JitsiMeetConference conference;
        final private Consumer<CallControl> successCallback;
        final private Consumer<Throwable> errorCallback;

        public CallControlRequest(
            final JitsiMeetConference conference,
            final Consumer<CallControl> successCallback,
            final Consumer<Throwable> errorCallback)
        {
            this.conference = conference;
            this.successCallback = successCallback;
            this.errorCallback = errorCallback;
        }

        public void run()
        {

            CallControl callControl;
            synchronized (TwilioCallControlManager.this)
            {
                String number;
                try
                {
                    number = getNextAvailableNumber();
                }
                catch (TwilioRestException e)
                {
                    this.errorCallback.accept(e);
                    return;
                }

                String pin = String.valueOf(Math.round(Math.random() * 1000));
                String room = conference.getRoomName()
                    .substring(0, conference.getRoomName().indexOf('@'));

                callControl = new CallControl(room, number, pin);
                allocated.put(number, callControl);
            }
            this.successCallback.accept(callControl);
        }
    }

    private synchronized String getNextAvailableNumber()
        throws TwilioRestException
    {
        if (available.size() == 0 && allocated.size() == 0)
        {
            if (logger.isDebugEnabled())
            {
                logger.debug("It seems like we're not initialized. Fetching " +
                        "the available phone numbers from Twilio");
            }

            Account mainAccount = client.getAccount();
            IncomingPhoneNumberList incomingPhoneNumbers
                = mainAccount.getIncomingPhoneNumbers();

            for (IncomingPhoneNumber incomingPhoneNumber
                    : incomingPhoneNumbers)
            {
                String phoneNumber = incomingPhoneNumber.getPhoneNumber();
                available.add(phoneNumber);
                if (logger.isDebugEnabled())
                {
                    logger.debug("Adding " + phoneNumber);
                }
            }
        }

        if (available.size() == 0)
        {
            logger.debug("It seems like we don't have any available number." +
                    " Requesting a new one.");

            // first get available
            Account mainAccount = client.getAccount();
            Iterator<AvailablePhoneNumber> availableToAllocateNumbers
                = mainAccount.getAvailablePhoneNumbers().iterator();
            if (availableToAllocateNumbers.hasNext())
            {
                List<NameValuePair> params = new ArrayList<NameValuePair>();
                params.add(new BasicNameValuePair("TrunkSid", trunkSid));
                params.add(new BasicNameValuePair("PhoneNumber",
                    availableToAllocateNumbers.next().getPhoneNumber()));

                IncomingPhoneNumberFactory factory
                    = client.getAccount().getIncomingPhoneNumberFactory();

                IncomingPhoneNumber incomingPhoneNumber =
                    factory.create(params);
                available.add(incomingPhoneNumber.getPhoneNumber());
            }
        }

        if (available.size() == 0)
        {
            logger.warn("Oh oh, this is not good. We still not have any " +
                    "available numbers. Giving up :-(");
            return null;
        }

        return available.remove(0);
    }

    public void requestCallControl(
            JitsiMeetConference conference,
            Consumer<CallControl> successCallback,
            Consumer<Throwable> errorCallback)
    {
        pool.execute(new CallControlRequest(
                    conference, successCallback, errorCallback));
    }

    public synchronized void releaseCallControl(
            JitsiMeetConference conference)
    {
        Iterator<Map.Entry<String, CallControl>> it
            = allocated.entrySet().iterator();
        while (it.hasNext())
        {
            Map.Entry<String, CallControl> pair = it.next();
            CallControl callControl = pair.getValue();
            if (callControl.getRoom() != null
                    && callControl.getRoom().equals(conference.getRoomName()
                        .substring(0, conference.getRoomName().indexOf('@'))))
            {
                it.remove();
                available.add(callControl.getNumber());
                break;
            }
        }
    }

    public synchronized CallControl getCallControlByPhone(String phone)
    {
        return allocated.get(phone);
    }
}
