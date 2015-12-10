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

    /**
     * Ctor.
     */
    public TwilioCallControlManager(String accountSid, String authToken)
    {
        client = new TwilioRestClient(accountSid, authToken);
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
            String number = allocateNumber();
            String pin = String.valueOf((Math.random() * 999) + 100);
            String room = conference.getRoomName();

            CallControl callControl = new CallControl(room, number, pin);
            this.successCallback.accept(callControl);
        }
    }

    private void refreshPhoneNumbers()
    {
        Account mainAccount = client.getAccount();
        AvailablePhoneNumberList phoneNumbers
            = mainAccount.getAvailablePhoneNumbers();

        for (AvailablePhoneNumber number : phoneNumbers)
        {
            available.add(number.getPhoneNumber());
        }
    }

    private void createPhoneNumber()
    {
        List<NameValuePair> params = new ArrayList<NameValuePair>();

        IncomingPhoneNumberFactory factory
            = client.getAccount().getIncomingPhoneNumberFactory();

        try
        {
        IncomingPhoneNumber incomingPhoneNumber = factory.create(params);
        }
        catch (Exception e)
        {
        }
    }

    private synchronized String allocateNumber()
    {
        if (available.size() == 0)
        {
        }
        return null;
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
                    && callControl.getRoom().equals(conference.getRoomName()))
            {
                it.remove();
                available.add(callControl.getNumber());
            }
        }
    }

    public synchronized CallControl getCallControlByPhone(String phone)
    {
        return allocated.get(phone);
    }
}
