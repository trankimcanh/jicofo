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

/**
 * @author George Politis
 */
public interface CallControlManager
{
    /**
     * Asynchronously requests a <tt>CallControl</tt> for a
     * <tt>JitsiMeetConference</tt>.
     *
     * @param conference
     * @param successCallback
     * @param errorCallback
     */
    void requestCallControl(
            JitsiMeetConference conference,
            Consumer<CallControl> successCallback,
            Consumer<Throwable> errorCallback);

    /**
     * Releases the <tt>CallControl</tt> associated to the
     * <tt>JitsiMeetConference</tt> passed as a parameter.
     *
     * @param conference
     */
    void releaseCallControl( JitsiMeetConference conference);

    /**
     * Returns the <tt>CallControl</tt> associated to the phone number passed
     * in as a parameter.
     *
     * @param phone
     * @return
     */
    CallControl getCallControlByPhone(String phone);
}
