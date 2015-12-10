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
/**
 * @author George Politis
 */
public class CallControl
{
    private final String number;

    private final String pin;

    private final String room;

    public CallControl(String room, String number, String pin)
    {
        this.room = room;
        this.number = number;
        this.pin = pin;
    }

    public String getRoom()
    {
        return this.room;
    }

    public String getPin()
    {
        return this.pin;
    }

    public String getNumber()
    {
        return this.number;
    }
}

