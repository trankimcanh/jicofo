package org.jitsi.jicofo.recording;

import net.java.sip.communicator.impl.protocol.jabber.extensions.colibri.*;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jibri.*;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.util.Logger;
import org.jitsi.jicofo.*;
import org.jitsi.protocol.xmpp.*;
import org.jivesoftware.smack.packet.*;

/**
 * Created by pdomas on 09/12/15.
 */
public class JibriRecorder
    extends Recorder
    implements JitsiMeetServices.JibriListener
{
    static private final net.java.sip.communicator.util.Logger logger
        = Logger.getLogger(JibriRecorder.class);

    private final JitsiMeetConference conference;

    private final OperationSetJitsiMeetTools meetTools;

    private JibriIq.Status jibriStatus = JibriIq.Status.UNDEFINED;

    public JibriRecorder(JitsiMeetConference conference,
                         OperationSetDirectSmackXmpp xmpp)
    {
        super(null, xmpp);

        this.conference = conference;


        ProtocolProviderService protocolService
            = conference.getXmppProvider();

        this.meetTools
            = protocolService.getOperationSet(
                    OperationSetJitsiMeetTools.class);

        conference.getServices().addJibriListener(this);

        setJibriStatus(
            conference.getServices().selectJibri() != null ?
                JibriIq.Status.OFF : JibriIq.Status.UNDEFINED);
    }

    @Override
    public boolean isRecording()
    {
        return JibriIq.Status.ON.equals(jibriStatus);
    }

    @Override
    public boolean setRecording(String from, String token,
                                ColibriConferenceIQ.Recording.State doRecord,
                                String path)
    {
        // NOT USED

        return false;
    }

    @Override
    public boolean accept(Packet packet)
    {
        return packet instanceof JibriIq;
    }

    @Override
    synchronized public void processPacket(Packet packet)
    {
        JibriIq iq = (JibriIq) packet;

        String from = iq.getFrom();

        if (recorderComponentJid != null &&
            (from.equals(recorderComponentJid) ||

            (from +"/").startsWith(recorderComponentJid)))
        {
            processJibriIqFromJibri(iq);
        }
        else
        {
            String roomName = MeetExtensionsHandler.getRoomNameFromMucJid(from);

            logger.info("Got Jibri packet for room: " + roomName);

            if (roomName == null)
            {
                return;
            }

            if (!conference.getRoomName().equals(roomName))
            {
                logger.info("Ignored packet because my room name is: " + conference.getRoomName());
                return;
            }

            XmppChatMember chatMember
                = (XmppChatMember) conference.findMember(from);

            if (chatMember == null)
            {
                logger.error("ERROR chat member not found for: " + from);
                return;
            }

            processJibriIqFromMeet(iq, chatMember);
        }
    }

    private void processJibriIqFromMeet(JibriIq iq, XmppChatMember sender)
    {
        JibriIq.Action action = iq.getAction();

        if (JibriIq.Action.UNDEFINED.equals(action))
            return;

        logger.info(
            "Jibri request from " + sender.getContactAddress() +
            " iq: " + iq.toXML());

        // start ?
        if (JibriIq.Action.START.equals(action) &&
            JibriIq.Status.OFF.equals(jibriStatus) &&
            recorderComponentJid == null)
        {
            if (!verifyModeratorRole(iq))
            {
                logger.info("Ignoring Jibri request from non-moderator.");
                return;
            }

            // Check if we have Jibri available
            String jibriJid = conference.getServices().selectJibri();
            if (jibriJid == null)
            {
                sendErrorResponse(
                    iq, XMPPError.Condition.service_unavailable, null);
                return;
            }

            JibriIq startIq = new JibriIq();
            startIq.setTo(jibriJid);
            startIq.setType(IQ.Type.SET);
            startIq.setAction(JibriIq.Action.START);
            startIq.setStreamId(iq.getStreamId());
            startIq.setUrl(conference.getRoomName());
            startIq.setFollowEntity(iq.getFollowEntity());

            logger.info("Starting Jibri recording: " + startIq.toXML());

            IQ startReply
                = (IQ) xmpp.getXmppConnection()
                        .sendPacketAndGetReply(startIq);

            logger.info("Start response: " + startReply.toXML());

            if (startReply == null)
            {
                sendErrorResponse(iq, XMPPError.Condition.request_timeout, null);
                return;
            }

            if (IQ.Type.RESULT.equals(startReply.getType()))
            {
                recorderComponentJid = jibriJid;

                setJibriStatus(JibriIq.Status.PENDING);

                sendResultResponse(iq);
                return;
            }
            else
            {
                XMPPError error = startReply.getError();
                if (error == null)
                {
                    error
                        = new XMPPError(XMPPError.Condition.interna_server_error);
                }
                sendPacket(IQ.createErrorResponse(iq, error));
                return;
            }
        }
        // stop ?
        else if (JibriIq.Action.STOP.equals(action) &&
            recorderComponentJid != null &&
            (JibriIq.Status.ON.equals(jibriStatus) ||
             JibriIq.Status.PENDING.equals(jibriStatus)))
        {
            if (!verifyModeratorRole(iq))
                return;

            XMPPError error = sendStopIQ();
            if (error == null)
            {
                error = new XMPPError(XMPPError.Condition.interna_server_error);
            }
            sendPacket(IQ.createErrorResponse(iq, error));
            return;
        }

        // Bad request
        sendErrorResponse(iq, XMPPError.Condition.bad_request, "Frig off !");
    }

    private boolean verifyModeratorRole(JibriIq iq)
    {
        String from = iq.getFrom();
        ChatRoomMemberRole role = conference.getRoleForMucJid(from);

        if (role == null)
        {
            // Only room members are allowed to send requests
            sendErrorResponse(iq, XMPPError.Condition.forbidden, null);
            return false;
        }

        if (ChatRoomMemberRole.MODERATOR.compareTo(role) < 0)
        {
            // Moderator permission is required
            sendErrorResponse(iq, XMPPError.Condition.not_allowed, null);
            return false;
        }
        return true;
    }

    private void sendPacket(Packet packet)
    {
        xmpp.getXmppConnection().sendPacket(packet);
    }

    private void sendResultResponse(IQ request)
    {
        sendPacket(
            IQ.createResultIQ(request));
    }

    private void sendErrorResponse(IQ request,
                                   XMPPError.Condition condition,
                                   String msg)
    {
        sendPacket(
            IQ.createErrorResponse(
                request,
                new XMPPError(condition, msg)
            )
        );
    }

    private void processJibriIqFromJibri(JibriIq iq)
    {
        // We have something from Jibri - let's update recording status
        JibriIq.Status status = iq.getStatus();
        if (!JibriIq.Status.UNDEFINED.equals(status))
        {
            logger.info("Updating status from Jibri: " + iq.toXML()
                + " for " + conference.getRoomName());

            setJibriStatus(status);
        }
    }

    synchronized private void setJibriStatus(JibriIq.Status newStatus)
    {
        jibriStatus = newStatus;

        RecordingStatus recordingStatus = new RecordingStatus();

        recordingStatus.setStatus(newStatus);

        logger.info(
            "Publish new Jibri status: " + recordingStatus.toXML() +
            " in: " + conference.getRoomName());

        ChatRoom2 chatRoom2 = conference.getChatRoom();

        // Publish that in the presence
        if (chatRoom2 != null)
        {
            meetTools.sendPresenceExtension(
                chatRoom2,
                recordingStatus);
        }
    }

    @Override
    public void dispose()
    {
        logger.info("DISPOSE FOR : " + conference.getRoomName());

        XMPPError error = sendStopIQ();
        if (error != null)
        {
            logger.error("Error when sending stop request: " + error.toXML());
        }

        conference.getServices().removeJibriListener(this);

        super.dispose();
    }

    private XMPPError sendStopIQ()
    {
        if (recorderComponentJid == null)
            return null;

        JibriIq stopRequest = new JibriIq();

        stopRequest.setType(IQ.Type.SET);
        stopRequest.setTo(recorderComponentJid);
        stopRequest.setAction(JibriIq.Action.STOP);

        logger.info("Trying to stop: " + stopRequest.toXML());

        IQ stopReply
            = (IQ) xmpp.getXmppConnection()
                    .sendPacketAndGetReply(stopRequest);

        logger.info("Stop response: " + stopReply.toXML());

        if (stopReply == null)
        {
            return new XMPPError(XMPPError.Condition.request_timeout, null);
        }

        if (IQ.Type.RESULT.equals(stopReply.getType()))
        {
            setJibriStatus(JibriIq.Status.OFF);

            recorderComponentJid = null;
            return null;
        }
        else
        {
            XMPPError error = stopReply.getError();
            if (error == null)
            {
                error
                    = new XMPPError(XMPPError.Condition.interna_server_error);
            }
            return error;
        }
    }

    @Override
    public void onJibriStatusChanged(String jibriJid, boolean idle)
    {
        // If we're recording then we listen to status coming from our Jibri
        // through IQs
        if (recorderComponentJid != null)
            return;

        String jibri = conference.getServices().selectJibri();
        if (jibri != null)
        {
            logger.info("Recording enabled");
            setJibriStatus(JibriIq.Status.OFF);
        }
        else
        {
            logger.info("Recording disabled - all jibris are busy");
            setJibriStatus(JibriIq.Status.UNDEFINED);
        }
    }

    @Override
    public void onJibriOffline(String jibriJid)
    {
        if (jibriJid.equals(recorderComponentJid))
        {
            logger.warn("Our recorder went offline: " + recorderComponentJid);
            recorderComponentJid = null;
        }

        String jibri = conference.getServices().selectJibri();
        if (jibri == null && recorderComponentJid == null)
        {
            logger.info("Recording disabled");
            setJibriStatus(JibriIq.Status.UNDEFINED);
        }
    }
}
