package org.jitsi.jicofo;

import net.java.sip.communicator.impl.protocol.jabber.extensions.jibri.*;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.util.*;
import org.jitsi.protocol.xmpp.*;
import org.jivesoftware.smack.packet.*;

/**
 *
 */
public class JibriDetector
    implements ChatRoomMemberPresenceListener,
    ChatRoomMemberPropertyChangeListener
{
    private final static Logger logger = Logger.getLogger(JibriDetector.class);

    private final ProtocolProviderHandler protocolHandler;

    private final String JIBRI_ROOM_NAME = "TheBreweryfe5a1a8993c07edc1a63";

    private final JitsiMeetServices meetServices;

    private OperationSetMultiUserChat muc;

    private ChatRoom chatRoom;

    public JibriDetector(ProtocolProviderHandler protocolHandler,
                         JitsiMeetServices meetServices)
    {
        if (protocolHandler == null)
            throw new NullPointerException("protocolHandler");

        if (meetServices == null)
            throw new NullPointerException("meetServices");

        this.protocolHandler = protocolHandler;
        this.meetServices = meetServices;
    }

    synchronized public void start()
    {
        this.muc = protocolHandler.getOperationSet(OperationSetMultiUserChat.class);

        try
        {
            String mucService = meetServices.getMucService();
            String roomName = JIBRI_ROOM_NAME + "@" + mucService;

            this.chatRoom = muc.createChatRoom(roomName, null);

            chatRoom.addMemberPresenceListener(this);
            chatRoom.addMemberPropertyChangeListener(this);

            chatRoom.join();

            logger.info("Joined JIBRI room: " + JIBRI_ROOM_NAME);
        }
        catch (OperationFailedException e)
        {
            logger.error("Failed to create room: " + JIBRI_ROOM_NAME, e);
        }
        catch (OperationNotSupportedException e)
        {
            logger.error("Failed to create room: " + JIBRI_ROOM_NAME, e);
        }
    }

    synchronized public void stop()
    {
        if (chatRoom != null)
        {
            chatRoom.removeMemberPresenceListener(this);

            chatRoom.removeMemberPropertyChangeListener(this);

            chatRoom.leave();
        }

        chatRoom = null;
    }

    @Override
    synchronized public void memberPresenceChanged(
        ChatRoomMemberPresenceChangeEvent presenceEvent)
    {
        if (ChatRoomMemberPresenceChangeEvent.MEMBER_JOINED
            .equals(presenceEvent.getEventType()))
        {
            onMemberJoined(presenceEvent.getChatRoomMember());
        }
        else
        {
            onMemberLeft(presenceEvent.getChatRoomMember());
        }
    }

    private void onMemberLeft(ChatRoomMember chatRoomMember)
    {
        onJibriUnavailable((XmppChatMember) chatRoomMember);
    }

    private void onMemberJoined(ChatRoomMember chatRoomMember)
    {
        XmppChatMember chatMember = (XmppChatMember) chatRoomMember;

        processMemberPresence(chatMember);
    }

    @Override
    synchronized public void chatRoomPropertyChanged(
        ChatRoomMemberPropertyChangeEvent memberPropertyEvent)
    {
        XmppChatMember member
            = (XmppChatMember) memberPropertyEvent.getSourceChatRoomMember();

        processMemberPresence(member);
    }

    private void processMemberPresence(XmppChatMember member)
    {
        Presence presence = member.getPresence();

        if (presence == null)
            return;

        JibriStatusPacketExt jibriStatus
            = (JibriStatusPacketExt) presence.getExtension(
                    JibriStatusPacketExt.ELEMENT_NAME,
                    JibriStatusPacketExt.NAMESPACE);

        if (jibriStatus == null)
            return;

        if (JibriStatusPacketExt.Status.IDLE.equals(jibriStatus.getStatus()))
        {
            onJibriAvailable(member);
        }
        else
        {
            onJibriUnavailable(member);
        }
    }

    private void onJibriAvailable(XmppChatMember member)
    {
        logger.info("On Jibri available: " + member.getJabberID());

        meetServices.jibriAvailable(member.getJabberID());
    }

    private void onJibriUnavailable(XmppChatMember member)
    {
        logger.info("On Jibri unavailable: " + member.getJabberID());

        meetServices.jibriUnavailable(member.getJabberID());
    }
}
