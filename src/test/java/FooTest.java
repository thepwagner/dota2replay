import com.google.common.io.CountingInputStream;
import com.google.common.io.Files;
import com.google.protobuf.ByteString;
import com.google.protobuf.CodedInputStream;
import org.junit.Test;
import org.xerial.snappy.Snappy;

import java.io.*;

/**
 * Created with IntelliJ IDEA.
 * User: pwagner
 * Date: 11/13/2013
 * Time: 7:48 PM
 * To change this template use File | Settings | File Templates.
 */
public class FooTest
{


    public int returnInt(byte[] buf)
    {
        int b;
        int count = 0;
        int result = 0;
        int index = 0;
        do
        {
            if (count == 5)
            {
                // If we get here it means that the fifth bit had its
                // high bit set, which implies corrupt data.
                return result;
            }
            else if (index >= buf.length)
            {
                return result;
            }

            b = buf[index++];
            result |= (b & 0x7F) << (7 * count);
            ++count;
        } while ((b & 0x80) != 0);

        return result;
    }

    public int returnInt(InputStream in) throws IOException
    {
        int b;
        int count = 0;
        int result = 0;
        int index = 0;
        do
        {
            if (count == 5)
            {
                // If we get here it means that the fifth bit had its
                // high bit set, which implies corrupt data.
                return result;
            }

            b = in.read();
            System.out.println("Read " + b);
            result |= (b & 0x7F) << (7 * count);
            ++count;
        } while ((b & 0x80) != 0);

        return result;
    }

    public static int readVarInt32(final InputStream input) throws IOException
    {
        int result = 0;
        int offset = 0;
        for (; offset < 32; offset += 7)
        {
            final int b = safeRead(input);
            result |= (b & 0x7f) << offset;
            if ((b & 0x80) == 0)
            {
                return result;
            }
        }
        throw new EOFException();
    }

    private static int safeRead(final InputStream input) throws IOException
    {
        final int b = input.read();
        if (b == -1)
        {
            throw new EOFException();
        }
        return b;
    }

    @Test
    public void f() throws Exception
    {
        byte[] bytes = Files.toByteArray(new File("src/test/resources/382617734.dem"));
        CodedInputStream codedIn = CodedInputStream.newInstance(bytes);

        // The file should begin with a static header:
        for (int i = 0; i < 8; i++)
        {
            codedIn.readRawByte();
        }

        // After that is the file offset:
        int fileInfoOffset = codedIn.readFixed32();
        System.out.println("File info offset " + fileInfoOffset);


        while (!codedIn.isAtEnd())
        {
            boolean compressed = false;

            int packedCommand = codedIn.readRawVarint32();

            if ((packedCommand & Demo.EDemoCommands.DEM_IsCompressed_VALUE) != 0)
            {
                packedCommand = packedCommand & ~Demo.EDemoCommands.DEM_IsCompressed_VALUE;
                compressed = true;
            }
            Demo.EDemoCommands command = Demo.EDemoCommands.valueOf(packedCommand);
            //System.out.println("Command: " + packedCommand + "," + command);

            int tick = codedIn.readRawVarint32();
            ByteString messageBytes = codedIn.readBytes();

            if (compressed)
            {
                byte[] decompressedBytes = Snappy.uncompress(messageBytes.toByteArray());
                messageBytes = ByteString.copyFrom(decompressedBytes);
            }

            if (command != null)
            {
                switch (command)
                {
                    case DEM_FileHeader:
                        Demo.CDemoFileHeader fileHeader = Demo.CDemoFileHeader.parseFrom(messageBytes);
//                        System.out.println("Server name: " + fileHeader.getServerName());
                        break;


                    case DEM_Packet:
                    case DEM_SignonPacket:
                        Demo.CDemoPacket demoPacket = Demo.CDemoPacket.parseFrom(messageBytes);

                        CodedInputStream packetIn = CodedInputStream.newInstance(demoPacket.getData().toByteArray());
                        while (!packetIn.isAtEnd())
                        {
                            int packetCmd = packetIn.readRawVarint32();
                            ByteString packetBytes = packetIn.readBytes();

                            Netmessages.NET_Messages netMessage = Netmessages.NET_Messages.valueOf(packetCmd);
                            if (netMessage != null)
                            {
                                //System.out.println("Packet command: " + packetCmd + "," + netMessage);
                                switch (netMessage)
                                {
                                    case net_Tick:
                                        Netmessages.CNETMsg_Tick netTick = Netmessages.CNETMsg_Tick.parseFrom(packetBytes);
                                        break;
                                }
                            }
                            else
                            {
                                Netmessages.SVC_Messages svcMessage = Netmessages.SVC_Messages.valueOf(packetCmd);

                                if (svcMessage != null)
                                {
                                    //System.out.println("Packet command: " + packetCmd + "," + svcMessage);
                                    switch (svcMessage)
                                    {
                                        case svc_ServerInfo:
                                            Netmessages.CSVCMsg_ServerInfo serverInfo = Netmessages.CSVCMsg_ServerInfo.parseFrom(packetBytes);
                                            break;

                                        case svc_UserMessage:
                                            Netmessages.CSVCMsg_UserMessage userMessage = Netmessages.CSVCMsg_UserMessage.parseFrom(packetBytes);

                                            int userMessageTypeInt = userMessage.getMsgType();
                                            //System.out.println("User message type: " + userMessageTypeInt);
                                            Usermessages.EBaseUserMessages userMessageType = Usermessages.EBaseUserMessages.valueOf(userMessageTypeInt);
                                            if (userMessageType != null)
                                            {

                                            }
                                            else
                                            {
                                                DotaUsermessages.EDotaUserMessages dotaUserMessage = DotaUsermessages.EDotaUserMessages.valueOf(userMessageTypeInt);
                                                if (dotaUserMessage != null)
                                                {
                                                    switch (dotaUserMessage)
                                                    {
                                                        case DOTA_UM_ChatEvent:
                                                            DotaUsermessages.CDOTAUserMsg_ChatEvent chatEvent = DotaUsermessages.CDOTAUserMsg_ChatEvent.parseFrom(userMessage.getMsgData());

                                                            DotaUsermessages.DOTA_CHAT_MESSAGE type = chatEvent.getType();

                                                            switch (type)
                                                            {
                                                                case CHAT_MESSAGE_HERO_KILL:
                                                                    System.out.println("Kill! " + chatEvent.getPlayerid1() + " killed by " + chatEvent.getPlayerid2());
                                                                    break;
                                                            }
//                                                            System.out.println("Chat event: " + type);
                                                            break;
                                                    }
                                                }
                                            }
                                            break;
                                    }
                                }
                            }
                        }

                }
            }


        }
        int totalBytesRead = codedIn.getTotalBytesRead();
        System.out.println("Read " + totalBytesRead);

    }
}
