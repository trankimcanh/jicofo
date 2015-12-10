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

        if (from.equals(recorderComponentJid))
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
        JibriIq.Status newStatus = iq.getStatus();

        logger.info(
            "Jibri request from " + sender.getContactAddress() +
            " iq: " + iq.toXML());

        // start ?
        if (newStatus.equals(JibriIq.Status.ON) &&
            JibriIq.Status.OFF.equals(jibriStatus) &&
            recorderComponentJid == null)
        {
            if (!verifyModeratorRole(iq))
                return;

            // Check if we have Jibri available
            String jibriJid = conference.getServices().selectJibri();
            if (jibriJid == null)
            {
                sendErrorResponse(
                    iq, XMPPError.Condition.service_unavailable, null);
                return;
            }

            recorderComponentJid = jibriJid;

            JibriIq startIq = new JibriIq();
            startIq.setTo(jibriJid);
            startIq.setType(IQ.Type.SET);
            startIq.setAction(JibriIq.Action.START);
            startIq.setStreamId(iq.getStreamId());

            logger.info("Starting Jibri recording: " + startIq);

            IQ startReply
                = (IQ) xmpp.getXmppConnection()
                        .sendPacketAndGetReply(startIq);

            logger.info("Start response: " + startReply);

            if (IQ.Type.RESULT.equals(startReply.getType()))
            {
                setJibriStatus(JibriIq.Status.PENDING);

                sendResultResponse(iq);
                return;
            }
            else
            {
                sendErrorResponse(
                    iq, XMPPError.Condition.interna_server_error, null);
                return;
            }
        }
        // stop ?
        else if (newStatus.equals(JibriIq.Status.OFF) &&
            recorderComponentJid != null &&
            (JibriIq.Status.ON.equals(jibriStatus) ||
             JibriIq.Status.PENDING.equals(jibriStatus)))
        {
            if (!verifyModeratorRole(iq))
                return;

            JibriIq stopRequest = new JibriIq();

            stopRequest.setType(IQ.Type.SET);
            stopRequest.setTo(recorderComponentJid);
            stopRequest.setAction(JibriIq.Action.STOP);

            logger.info("Trying to stop: " + stopRequest.toXML());

            Packet stopReply
                = xmpp.getXmppConnection().sendPacketAndGetReply(stopRequest);

            logger.info("Stop response: " + stopReply);

            sendResultResponse(iq);
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
        String from = iq.getFrom();
        if (!from.equals(recorderComponentJid))
        {
            logger.info("Ingored IQ from Jibri: " + iq);
            return;
        }

        // We have something from Jibri - let's update recording status
        setJibriStatus(iq.getStatus());
    }

    synchronized private void setJibriStatus(JibriIq.Status newStatus)
    {
        jibriStatus = newStatus;

        RecordingStatus recordingStatus = new RecordingStatus();

        recordingStatus.setStatus(newStatus);

        logger.info("Publish new Jibri status: " + recordingStatus.toXML());

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
        conference.getServices().removeJibriListener(this);

        super.dispose();
    }

    @Override
    public void onJibriStatusChanged(String jibriJid, boolean online)
    {
        // Recording in progress
        if (recorderComponentJid != null)
        {
            return;
        }

        if (online)
        {
            if (JibriIq.Status.UNDEFINED.equals(jibriStatus))
            {
                logger.info("Recording enabled");
                setJibriStatus(JibriIq.Status.OFF);
            }
        }
        else
        {
            String jibri = conference.getServices().selectJibri();
            if (jibri == null &&
                !JibriIq.Status.UNDEFINED.equals(jibriStatus))
            {
                logger.info("Recording disabled");
                setJibriStatus(JibriIq.Status.UNDEFINED);
            }
        }
    }
}
