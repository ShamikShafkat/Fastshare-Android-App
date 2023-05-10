package com.example.fastshare;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.widget.Toast;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Random;

public class Sender {

    private final sendActivity activity;
    ProgressDialog progressDialog;

    Sender(sendActivity activity){
        this.activity = activity;
    }

    public void sendFile(Socket socket, DataInputStream dis, DataOutputStream dos,String filepath,String fileName)
    {
        try {
            Random random = new Random();
            int sequenceNumber = random.nextInt(10000);

            ArrayList<DataPacket> bufferQueue = new ArrayList<>();
            int ackNumber = 0;
            int header_syn_others = 0b0101000000000010;
            int header_syn_ack_others = 0b0101000000010010;
            int header_fin_others = 0b0101000000000001;
            int header_fin_ack_others = 0b0101000000010001;
            int header_ack_others = 0b0101000000010000;
            int others = 0b0101000000000000;
            Short window_buffer = 0;
            Short checkSum = 1;
            Short urgentPointer = 0;
            int cwnd = 1;
            int ssthresshold = 8;
            int tcpRecordSize = 520;
            int maxPacketSize = 500; //50 bytes
            int flag = 0; //1  means wait for ack
            int dupAck = 0;
            double timeOut = 250;
            long startTime = 0;
            long currentTime = 0;
            int expected_sequenceNumber = 0;
            int previous_ack_number = 0;
            double estimatedRTT = 140;
            double sampleRTT = 200;
            double alpha = 0.125;
            double beta = 0.25;
            double DevRTT = 20;
            boolean slowStart = true ;

            File file = new File(filepath);
            FileInputStream fileInputStream = new FileInputStream(file);
            long fileSize = file.length();
            long mainFileSize = fileSize ;

            byte[] header = makeHeader((short)socket.getLocalPort(),(short)socket.getPort(),sequenceNumber, ackNumber,header_syn_others,window_buffer,checkSum,urgentPointer);
            byte[] fileNameData = fileName.getBytes(StandardCharsets.UTF_8);
            byte[] packet = new byte[80];
            System.arraycopy(header,0,packet,0,20);
            System.arraycopy(fileNameData,0,packet,20,fileName.length());
            dos.write(packet,0,20+fileName.length());
            expected_sequenceNumber = sequenceNumber + fileName.length();
            byte[] readheader_array = new byte[20];

            startTime = System.currentTimeMillis();
            boolean syn_ack_flag = false;

            int finalExpected_sequenceNumber = expected_sequenceNumber;
            while(System.currentTimeMillis()-startTime <= 120*1000)
            {
                if(dis.available()>0)
                {
                    dis.read(readheader_array);
                    TCP_Header syn_ack_header = readHeader(readheader_array);
                    sequenceNumber = syn_ack_header.sequenceNumber;
                    window_buffer = syn_ack_header.window_buffer;
                    header = makeHeader((short)socket.getLocalPort(),(short)socket.getPort(),sequenceNumber, ackNumber,header_syn_ack_others,window_buffer,checkSum,urgentPointer);

                    byte[] fileLengthData = getFileLength(fileSize);
                    System.arraycopy(header,0,packet,0,20);
                    System.arraycopy(fileLengthData,0,packet,20,8);
                    dos.write(packet,0,20+8);
                    syn_ack_flag = true;
                    sequenceNumber += 8;
                    expected_sequenceNumber = sequenceNumber;
                    break;
                }
            }

            if(!syn_ack_flag)
            {
                activity.runOnUiThread(new Runnable() {
                    public void run() {
                        Toast.makeText(activity, "Receiver is not listening to you.Please try again", Toast.LENGTH_LONG).show();
                    }
                });

//                dis.close();
//                dos.close();
//                socket.close();
            }

            activity.runOnUiThread(new Runnable() {
                public void run() {
                    progressDialog = ProgressDialog.show(activity, "File Transfer", "Please wait...");
                }
            });


            Thread.sleep(500);






            int firstSequenceNumber = sequenceNumber ;

            byte[] fileData = new byte[maxPacketSize] ;
            int bytes = 0;

            int packetNumber = 0;
            TCP_Header tcp_header = null ;


            while(true)
            {
                if(flag == 0)
                {
                    for(int i = 1 ;i<=cwnd;i++)
                    {
                        packetNumber++;
                        startTime = System.currentTimeMillis() ;

                        if( (bytes = fileInputStream.read(fileData)) != -1 )
                        {
                            System.out.println("Number of bytes = " + bytes);

                            DataPacket dataPacket;
                            dataPacket = new DataPacket();
                            dataPacket.sequenceNumber = sequenceNumber ;
                            System.arraycopy(fileData,0,dataPacket.message,0,bytes);
                            dataPacket.checkSum = makeCheckSum(fileData,bytes) ;
                            bufferQueue.add(dataPacket) ;

                            header = makeHeader((short)socket.getLocalPort(),(short)socket.getPort(),
                                    sequenceNumber, ackNumber, others,window_buffer,dataPacket.checkSum,urgentPointer);

                            packet = new byte[tcpRecordSize];
                            System.arraycopy(header,0,packet,0,20);
                            System.arraycopy(fileData,0,packet,20,bytes);



                            dos.write(packet,0,20+bytes);
                            System.out.println("Packet " + packetNumber +" Sent. SequenceNumber = " +
                                    sequenceNumber + ". Bytes Send : " + bytes);



                            fileSize -= bytes;
                            expected_sequenceNumber = sequenceNumber + bytes ;
                            sequenceNumber = expected_sequenceNumber ;

                        }
                    }

                    flag = 1;

                }
                else if(flag == 1)
                {
                    //check timeout condition
                    currentTime = System.currentTimeMillis() ;
                    if((currentTime-startTime) > timeOut)
                    {
                        System.out.println("Timeout Occurred(1)");
                        System.out.println("Timeout Occurred(1)");
                        System.out.println("Timeout Occurred(1)");

                        //new timer
                        timeOut *= 2 ;
                        startTime = System.currentTimeMillis() ;


                        if(tcp_header == null)
                        {
                            header = makeHeader((short)socket.getLocalPort(),(short)socket.getPort(),
                                    bufferQueue.get(0).sequenceNumber,ackNumber,others,window_buffer,bufferQueue.get(0).checkSum,urgentPointer);
                        }
                        else
                        {
                            header = makeHeader((short)socket.getLocalPort(),(short)socket.getPort(),
                                    bufferQueue.get(0).sequenceNumber,tcp_header.acknowledgementNumber,others,
                                    tcp_header.window_buffer,bufferQueue.get(0).checkSum, tcp_header.urgentPointer );
                        }

                        packet = new byte[tcpRecordSize];
                        System.arraycopy(header, 0, packet, 0, 20);
                        System.arraycopy(bufferQueue.get(0).message, 0, packet, 20, bufferQueue.get(0).message_length);

                        System.out.println("Packet Length = " + packet.length);
                        dos.write(packet, 0, 20 + bufferQueue.get(0).message_length);
                        System.out.println("Packet " + packetNumber + " Sent. SequenceNumber = " +
                                bufferQueue.get(0).sequenceNumber + ". Bytes Send : " + bufferQueue.get(0).message_length);

                        //fast retransmit
                        while(true)
                        {
                            currentTime = System.currentTimeMillis() ;
                            if((currentTime -startTime) > timeOut)
                            {
                                //new timer
                                currentTime = System.currentTimeMillis() ;
                                sampleRTT = currentTime - startTime ;
                                estimatedRTT = (1-alpha) * estimatedRTT + alpha*sampleRTT ;
                                DevRTT = (1-beta) * DevRTT + beta * Math.abs(sampleRTT -estimatedRTT) ;
                                timeOut = estimatedRTT + 4 * DevRTT ;
                                System.out.println("New Timeout = " + timeOut);
                                startTime = System.currentTimeMillis() ;
                                dos.write(packet, 0, 20 + bufferQueue.get(0).message_length);
                                System.out.println("Packet " + packetNumber + " Sent. SequenceNumber = " +
                                        bufferQueue.get(0).sequenceNumber + ". Bytes Send : " + bufferQueue.get(0).message_length);
                                continue;
                            }
                            byte[] acknowledgement = new byte[20];
                            if(dis.available()>0)
                            {
                                dis.read(acknowledgement);
                                TCP_Header retransmitHeader = readHeader(acknowledgement);
                                if(retransmitHeader.sequenceNumber > bufferQueue.get(0).sequenceNumber)
                                {
                                    System.out.println("Acknowledgement received " +
                                            "with sequence number(FRT) = " + retransmitHeader.sequenceNumber);

                                    while (!bufferQueue.isEmpty()) {
                                        DataPacket dataPacket = bufferQueue.get(0) ;
                                        if(dataPacket.sequenceNumber < retransmitHeader.sequenceNumber)
                                        {
                                            bufferQueue.remove(0);
                                        }
                                        else
                                        {
                                            break;
                                        }
                                    }

                                    ssthresshold = cwnd/2;
                                    cwnd = 1;
                                    previous_ack_number = retransmitHeader.sequenceNumber ;
                                    dupAck = 0;
                                    currentTime = System.currentTimeMillis() ;
                                    sampleRTT = currentTime - startTime ;
                                    estimatedRTT = (1-alpha) * estimatedRTT + alpha*sampleRTT ;
                                    DevRTT = (1-beta) * DevRTT + beta * Math.abs(sampleRTT -estimatedRTT) ;
                                    timeOut = estimatedRTT + 4 * DevRTT ;
                                    System.out.println("New Timeout = " + timeOut);
                                    startTime = System.currentTimeMillis();
                                    if(ssthresshold == 0)
                                    {
                                        ssthresshold = 1;
                                    }
                                    flag = 2;
                                    break;
                                }
                            }
                        }

                        System.out.println("Fast Retransmit end for timeout(1)");

                        continue;
                    }


                    if(dis.available() > 0)
                    {
                        System.out.println("Inside Available");

                        byte[] acknowledgement = new byte[20];
                        dis.read(acknowledgement);


                        tcp_header = readHeader(acknowledgement);
                        System.out.println("Acknowledgement received with sequenceNumber(1) = " + tcp_header.sequenceNumber);


                        if(expected_sequenceNumber != tcp_header.sequenceNumber)
                        {
                            System.out.println("Sequence Number not matched in first handle");

                            while (!bufferQueue.isEmpty()) {
                                DataPacket tempPacket = bufferQueue.get(0);
                                if(tempPacket.sequenceNumber < tcp_header.sequenceNumber)
                                {
                                    bufferQueue.remove(0);
                                }
                                else
                                {
                                    break;
                                }
                            }

                            if(previous_ack_number == tcp_header.sequenceNumber)
                            {
                                System.out.println("DupAck received with sequence Number = " + previous_ack_number);

                                dupAck++;
                                if(dupAck == 3)
                                {
                                    System.out.println("Three dupAck found");
                                    System.out.println("Three dupAck found");
                                    System.out.println("Three dupAck found");


                                    header = makeHeader(tcp_header.sourcePort, tcp_header.destPort,
                                            bufferQueue.get(0).sequenceNumber, tcp_header.acknowledgementNumber,others,
                                            tcp_header.window_buffer,bufferQueue.get(0).checkSum, tcp_header.urgentPointer);

                                    packet = new byte[tcpRecordSize];
                                    System.arraycopy(header, 0, packet, 0, 20);
                                    System.arraycopy(bufferQueue.get(0).message, 0, packet, 20,bufferQueue.get(0).message_length);

                                    System.out.println("Packet Length = " + packet.length);
                                    dos.write(packet, 0, 20 + bufferQueue.get(0).message_length);
                                    System.out.println("Packet " + packetNumber + " Sent. SequenceNumber = " +
                                            bufferQueue.get(0).sequenceNumber + ". Bytes Send : " + bufferQueue.get(0).message_length);


                                    //fast retransmit
                                    while(true)
                                    {
                                        currentTime = System.currentTimeMillis() ;
                                        if((currentTime -startTime) > timeOut)
                                        {
                                            //new timer
                                            currentTime = System.currentTimeMillis() ;
                                            sampleRTT = currentTime - startTime ;
                                            estimatedRTT = (1-alpha) * estimatedRTT + alpha*sampleRTT ;
                                            DevRTT = (1-beta) * DevRTT + beta * Math.abs(sampleRTT -estimatedRTT) ;
                                            timeOut = estimatedRTT + 4 * DevRTT ;
                                            System.out.println("New Timeout = " + timeOut);
                                            startTime = System.currentTimeMillis() ;
                                            dos.write(packet, 0, 20 + bufferQueue.get(0).message_length);
                                            System.out.println("Packet " + packetNumber + " Sent. SequenceNumber = " +
                                                    bufferQueue.get(0).sequenceNumber + ". Bytes Send : " + bufferQueue.get(0).message_length);
                                            continue;
                                        }
                                        if(dis.available()>0)
                                        {
                                            dis.read(acknowledgement);
                                            TCP_Header retransmitHeader = readHeader(acknowledgement);
                                            if(retransmitHeader.sequenceNumber > tcp_header.sequenceNumber)
                                            {
                                                System.out.println("Acknowledgement received " +
                                                        "with sequence number(FRT) = " + retransmitHeader.sequenceNumber);

                                                while (!bufferQueue.isEmpty()) {
                                                    DataPacket dataPacket = bufferQueue.get(0) ;
                                                    if(dataPacket.sequenceNumber < retransmitHeader.sequenceNumber)
                                                    {
                                                        bufferQueue.remove(0);
                                                    }
                                                    else
                                                    {
                                                        break;
                                                    }
                                                }

                                                ssthresshold = cwnd/2;
                                                cwnd = 1;
                                                previous_ack_number = retransmitHeader.sequenceNumber ;
                                                dupAck = 0;
                                                currentTime = System.currentTimeMillis() ;
                                                sampleRTT = currentTime - startTime ;
                                                estimatedRTT = (1-alpha) * estimatedRTT + alpha*sampleRTT ;
                                                DevRTT = (1-beta) * DevRTT + beta * Math.abs(sampleRTT -estimatedRTT) ;
                                                timeOut = estimatedRTT + 4 * DevRTT ;
                                                System.out.println("New Timeout = " + timeOut);
                                                startTime = System.currentTimeMillis();
                                                flag = 2;
                                                break;
                                            }
                                        }
                                        else
                                        {

                                        }
                                    }

                                    System.out.println("Fast retransmit done first handler");
                                    continue;

                                }

                            }
                            else
                            {
                                System.out.println("First ack of duplicate. First Handler");
                                dupAck = 0;
                                previous_ack_number = tcp_header.sequenceNumber ;
                                currentTime = System.currentTimeMillis() ;
                                sampleRTT = currentTime - startTime ;
                                estimatedRTT = (1-alpha) * estimatedRTT + alpha*sampleRTT ;
                                DevRTT = (1-beta) * DevRTT + beta * Math.abs(sampleRTT -estimatedRTT) ;
                                timeOut = estimatedRTT + 4 * DevRTT ;
                                System.out.println("New Timeout = " + timeOut);
                                startTime = System.currentTimeMillis() ;
                            }

                            continue;
                        }

                        if (cwnd >= ssthresshold)
                        {
                            cwnd += 1;
                        }
                        else
                        {
                            cwnd *= 2;
                            if(cwnd > ssthresshold)
                            {
                                cwnd = ssthresshold ;
                            }
                        }


                        sequenceNumber = tcp_header.sequenceNumber;
                        previous_ack_number = tcp_header.sequenceNumber ;
                        dupAck = 0;

                        //remove acked packets
                        for(;!bufferQueue.isEmpty();)
                        {
                            DataPacket tempPacket = bufferQueue.get(0);
                            if(tempPacket.sequenceNumber < sequenceNumber)
                            {
                                bufferQueue.remove(0);
                            }
                            else
                            {
                                break;
                            }
                        }


                        int totalBytesSent = 0;
                        currentTime = System.currentTimeMillis() ;
                        sampleRTT = currentTime - startTime ;
                        estimatedRTT = (1-alpha) * estimatedRTT + alpha*sampleRTT ;
                        DevRTT = (1-beta) * DevRTT + beta * Math.abs(sampleRTT -estimatedRTT) ;
                        timeOut = estimatedRTT + 4 * DevRTT ;
                        System.out.println("SampleRTT = " + sampleRTT);
                        System.out.println("EstimatedRTT = " + estimatedRTT);
                        System.out.println("DevRTT = " + DevRTT);
                        System.out.println("New Timeout = " + timeOut);
                        startTime = System.currentTimeMillis();

                        if(fileSize <= 0)
                        {
                            System.out.println("Inside FileSize < 0");
                            if(tcp_header.sequenceNumber == (firstSequenceNumber + mainFileSize))
                            {
                                System.out.println("Last Acknowledgement Found");
                                break;
                            }
                            continue;
                        }

                        int i;
                        System.out.println("Congestion Window Size = " + cwnd);
                        System.out.println("Ssthresshold = " + ssthresshold);
                        System.out.println("Window Buffer Available = " + tcp_header.window_buffer);
                        for (i = 1; i <= cwnd && (tcp_header.window_buffer-totalBytesSent)>=maxPacketSize; i++)
                        {
                            if( (currentTime-startTime) > timeOut)
                            {
                                System.out.println("Timeout Occurred");
                                System.out.println("Timeout Occurred");
                                System.out.println("Timeout Occurred");

                                //new timer
                                currentTime = System.currentTimeMillis() ;
                                sampleRTT = currentTime - startTime ;
                                estimatedRTT = (1-alpha) * estimatedRTT + alpha*sampleRTT ;
                                DevRTT = (1-beta) * DevRTT + beta * Math.abs(sampleRTT -estimatedRTT) ;
                                timeOut = estimatedRTT + 4 * DevRTT ;
                                System.out.println("New Timeout = " + timeOut);
                                startTime = System.currentTimeMillis() ;

                                header = makeHeader(tcp_header.sourcePort, tcp_header.destPort,
                                        bufferQueue.get(0).sequenceNumber, tcp_header.acknowledgementNumber,others,
                                        tcp_header.window_buffer,bufferQueue.get(0).checkSum, tcp_header.urgentPointer);

                                packet = new byte[tcpRecordSize];
                                System.arraycopy(header, 0, packet, 0, 20);
                                System.arraycopy(bufferQueue.get(0).message, 0, packet, 20, bufferQueue.get(0).message_length);

                                System.out.println("Packet Length = " + packet.length);
                                dos.write(packet, 0, 20 + bufferQueue.get(0).message_length);
                                System.out.println("Packet " + packetNumber + " Sent. SequenceNumber = " +
                                        bufferQueue.get(0).sequenceNumber + ". Bytes Send : " + bufferQueue.get(0).message_length);


                                //fast retransmit
                                while(true)
                                {
                                    currentTime = System.currentTimeMillis() ;
                                    if((currentTime -startTime) > timeOut)
                                    {
                                        //new timer
                                        currentTime = System.currentTimeMillis() ;
                                        sampleRTT = currentTime - startTime ;
                                        estimatedRTT = (1-alpha) * estimatedRTT + alpha*sampleRTT ;
                                        DevRTT = (1-beta) * DevRTT + beta * Math.abs(sampleRTT -estimatedRTT) ;
                                        timeOut = estimatedRTT + 4 * DevRTT ;
                                        System.out.println("New Timeout = " + timeOut);
                                        startTime = System.currentTimeMillis() ;
                                        dos.write(packet, 0, 20 + bufferQueue.get(0).message_length);
                                        System.out.println("Packet " + packetNumber + " Sent. SequenceNumber = " +
                                                bufferQueue.get(0).sequenceNumber + ". Bytes Send : " + bufferQueue.get(0).message_length);
                                        continue;
                                    }

                                    if(dis.available()>0)
                                    {
                                        dis.read(acknowledgement);
                                        TCP_Header retransmitHeader = readHeader(acknowledgement);
                                        if(retransmitHeader.sequenceNumber > bufferQueue.get(0).sequenceNumber)
                                        {
                                            System.out.println("Acknowledgement received " +
                                                    "with sequence number(FRT) = " + retransmitHeader.sequenceNumber);

                                            while (!bufferQueue.isEmpty()) {
                                                DataPacket dataPacket = bufferQueue.get(0) ;
                                                if(dataPacket.sequenceNumber < retransmitHeader.sequenceNumber)
                                                {
                                                    bufferQueue.remove(0);
                                                }
                                                else
                                                {
                                                    break;
                                                }
                                            }

                                            ssthresshold = cwnd/2;
                                            cwnd = 1;
                                            i = 0;
                                            previous_ack_number = retransmitHeader.sequenceNumber ;
                                            dupAck = 0;
                                            currentTime = System.currentTimeMillis() ;
                                            sampleRTT = currentTime - startTime ;
                                            estimatedRTT = (1-alpha) * estimatedRTT + alpha*sampleRTT ;
                                            DevRTT = (1-beta) * DevRTT + beta * Math.abs(sampleRTT -estimatedRTT) ;
                                            timeOut = estimatedRTT + 4 * DevRTT ;
                                            System.out.println("New Timeout = " + timeOut);
                                            startTime = System.currentTimeMillis();
                                            if(ssthresshold == 0)
                                            {
                                                ssthresshold = 1;
                                            }
                                            flag = 2;
                                            break;
                                        }
                                    }
                                }

                                System.out.println("Fast Retransmit end for timeout");
                                continue;
                            }


                            if(dis.available() > 0)
                            {
                                dis.read(acknowledgement);
                                TCP_Header temp_header = readHeader(acknowledgement);

                                System.out.println("Acknowledgement received with sequenceNumber = " + temp_header.sequenceNumber);

                                while (!bufferQueue.isEmpty()) {
                                    DataPacket tempPacket = bufferQueue.get(0);
                                    if(tempPacket.sequenceNumber < temp_header.sequenceNumber)
                                    {
                                        bufferQueue.remove(0);
                                    }
                                    else
                                    {
                                        break;
                                    }
                                }

                                if(previous_ack_number == temp_header.sequenceNumber)
                                {
                                    System.out.println("DupAck received with sequence Number = " + previous_ack_number);

                                    dupAck++;
                                    if(dupAck == 3)
                                    {
                                        System.out.println("Three dupAck found");
                                        System.out.println("Three dupAck found");
                                        System.out.println("Three dupAck found");


                                        header = makeHeader(temp_header.sourcePort, temp_header.destPort,
                                                bufferQueue.get(0).sequenceNumber, temp_header.acknowledgementNumber,others,
                                                temp_header.window_buffer,bufferQueue.get(0).checkSum, temp_header.urgentPointer);

                                        packet = new byte[tcpRecordSize];
                                        System.arraycopy(header, 0, packet, 0, 20);
                                        System.arraycopy(bufferQueue.get(0).message, 0, packet, 20, bufferQueue.get(0).message_length);

                                        System.out.println("Packet Length = " + packet.length);
                                        dos.write(packet, 0, 20 + bufferQueue.get(0).message_length);
                                        System.out.println("Packet " + packetNumber + " Sent. SequenceNumber = " +
                                                bufferQueue.get(0).sequenceNumber + ". Bytes Send : " + bufferQueue.get(0).message_length);


                                        //fast retransmit
                                        while(true)
                                        {
                                            currentTime = System.currentTimeMillis() ;
                                            if((currentTime -startTime) > timeOut)
                                            {
                                                //new timer
                                                currentTime = System.currentTimeMillis() ;
                                                sampleRTT = currentTime - startTime ;
                                                estimatedRTT = (1-alpha) * estimatedRTT + alpha*sampleRTT ;
                                                DevRTT = (1-beta) * DevRTT + beta * Math.abs(sampleRTT -estimatedRTT) ;
                                                timeOut = estimatedRTT + 4 * DevRTT ;
                                                System.out.println("New Timeout = " + timeOut);
                                                startTime = System.currentTimeMillis() ;
                                                dos.write(packet, 0, 20 + bufferQueue.get(0).message_length);
                                                System.out.println("Packet " + packetNumber + " Sent. SequenceNumber = " +
                                                        bufferQueue.get(0).sequenceNumber + ". Bytes Send : " + bufferQueue.get(0).message_length);
                                                continue;
                                            }

                                            if(dis.available()>0)
                                            {
                                                dis.read(acknowledgement);
                                                TCP_Header retransmitHeader = readHeader(acknowledgement);
                                                if(retransmitHeader.sequenceNumber > temp_header.sequenceNumber  )
                                                {
                                                    System.out.println("Acknowledgement received " +
                                                            "with sequence number(FRT) = " + retransmitHeader.sequenceNumber);


                                                    while (!bufferQueue.isEmpty()) {
                                                        DataPacket dataPacket = bufferQueue.get(0) ;
                                                        if(dataPacket.sequenceNumber < retransmitHeader.sequenceNumber)
                                                        {
                                                            bufferQueue.remove(0);
                                                        }
                                                        else
                                                        {
                                                            break;
                                                        }
                                                    }

                                                    ssthresshold = cwnd/2;
                                                    cwnd = 1;
                                                    i = 0;
                                                    previous_ack_number = retransmitHeader.sequenceNumber ;
                                                    dupAck = 0;
                                                    currentTime = System.currentTimeMillis() ;
                                                    sampleRTT = currentTime - startTime ;
                                                    estimatedRTT = (1-alpha) * estimatedRTT + alpha*sampleRTT ;
                                                    DevRTT = (1-beta) * DevRTT + beta * Math.abs(sampleRTT -estimatedRTT) ;
                                                    timeOut = estimatedRTT + 4 * DevRTT ;
                                                    System.out.println("New Timeout = " + timeOut);
                                                    startTime = System.currentTimeMillis();
                                                    if(ssthresshold ==0 )
                                                    {
                                                        ssthresshold = 1;
                                                    }

                                                    break;
                                                }
                                            }
                                        }

                                        continue;

                                    }

                                }
                                else
                                {
                                    System.out.println("First ack of duplicate. Second Handler");

                                    dupAck = 0;
                                    previous_ack_number = temp_header.sequenceNumber ;

                                    currentTime = System.currentTimeMillis() ;
                                    sampleRTT = currentTime - startTime ;
                                    estimatedRTT = (1-alpha) * estimatedRTT + alpha*sampleRTT ;
                                    DevRTT = (1-beta) * DevRTT + beta * Math.abs(sampleRTT -estimatedRTT) ;
                                    timeOut = estimatedRTT + 4 * DevRTT ;
                                    System.out.println("New Timeout = " + timeOut);
                                    startTime = System.currentTimeMillis() ;
                                }
                            }

                            packetNumber++;

                            if ((bytes = fileInputStream.read(fileData)) != -1)
                            {
                                DataPacket dataPacket;
                                dataPacket = new DataPacket();
                                dataPacket.sequenceNumber = sequenceNumber ;
                                System.arraycopy(fileData,0,dataPacket.message,0,bytes);
                                dataPacket.message_length = bytes ;
                                dataPacket.checkSum = makeCheckSum(fileData,bytes);
                                bufferQueue.add(dataPacket) ;

                                header = makeHeader(tcp_header.sourcePort, tcp_header.destPort,
                                        sequenceNumber, tcp_header.acknowledgementNumber,others,
                                        tcp_header.window_buffer,dataPacket.checkSum, tcp_header.urgentPointer);

                                packet = new byte[tcpRecordSize];
                                System.arraycopy(header, 0, packet, 0, 20);
                                System.arraycopy(fileData, 0, packet, 20, bytes);

                                System.out.println("Packet Length = " + packet.length);

                                //packet loss generation
                                boolean error_flag = false ;
//                                int value = random.nextInt(100);
//                                if(cwnd > ssthresshold)
//                                {
//                                    if(cwnd<=10)
//                                    {
//                                        if(value>=97)
//                                        {
//                                            error_flag =true ;
//                                        }
//                                        else
//                                        {
//                                            error_flag = false ;
//                                        }
//                                    }
//                                    else if(cwnd <= 14)
//                                    {
//                                        if(value>=95)
//                                        {
//                                            error_flag = true ;
//                                        }
//                                        else
//                                        {
//                                            error_flag = false;
//                                        }
//                                    }
//                                    else
//                                    {
//                                        if(value >=90)
//                                        {
//                                            error_flag = true ;
//                                        }
//                                        else
//                                        {
//                                            error_flag = false ;
//                                        }
//                                    }
//                                }
//                                else
//                                {
//                                    if(value == 99)
//                                    {
//                                        error_flag = true ;
//                                    }
//                                    else
//                                    {
//                                        error_flag = false;
//                                    }
//                                }

                                if(error_flag)
                                {
                                    System.out.println("Data not send. CWND = " + cwnd + ". i = " + i);
                                }
                                else
                                {
                                    dos.write(packet, 0, 20 + bytes);
                                    System.out.println("Packet " + packetNumber + " Sent. SequenceNumber = " +
                                            sequenceNumber + ". Bytes Send : " + bytes);
                                }

                                fileSize -= bytes;
                                totalBytesSent += bytes;
                                expected_sequenceNumber = sequenceNumber + bytes ;
                                sequenceNumber += bytes ;

                            }
                            else
                            {
                                System.out.println("Problem in file read");
                                break;
                            }
                        }

                        System.out.println("Transmission Completed. Packets sent = " + (i-1));
                    }
                }
                else if(flag == 2)
                {
                    for(int i = 1 ;i<=cwnd;i++)
                    {
                        packetNumber++;

                        if( (bytes = fileInputStream.read(fileData)) != -1 )
                        {
                            System.out.println("Number of bytes = " + bytes);
                            System.out.println("Packet after fast retransmit");
                            System.out.println("Packet after fast retransmit");
                            System.out.println("Packet after fast retransmit");


                            DataPacket dataPacket;
                            dataPacket = new DataPacket();
                            dataPacket.sequenceNumber = expected_sequenceNumber ;
                            System.arraycopy(fileData,0,dataPacket.message,0,bytes);
                            dataPacket.message_length = bytes ;
                            dataPacket.checkSum = makeCheckSum(fileData,bytes);
                            bufferQueue.add(dataPacket) ;

                            header = makeHeader((short)socket.getLocalPort(),(short)socket.getPort(),
                                    expected_sequenceNumber, tcp_header.acknowledgementNumber,others, tcp_header.window_buffer,
                                    dataPacket.checkSum, tcp_header.urgentPointer);

                            packet = new byte[tcpRecordSize];
                            System.arraycopy(header,0,packet,0,20);
                            System.arraycopy(fileData,0,packet,20,bytes);


                            dos.write(packet,0,20+bytes);
                            System.out.println("Packet " + packetNumber +" Sent. SequenceNumber = " +
                                    expected_sequenceNumber + ". Bytes Send : " + bytes);

                            fileSize -= bytes;
                            expected_sequenceNumber = sequenceNumber + bytes ;
                            sequenceNumber += bytes ;

                        }
                    }

                    flag = 1;
                }
                else
                {

                }

            }


            Thread.sleep(500);

            //tcp connection closure
            header = makeHeader((short)socket.getLocalPort(),(short)socket.getPort(),
                    expected_sequenceNumber, tcp_header.acknowledgementNumber,header_fin_others , tcp_header.window_buffer,
                    (short) 1, tcp_header.urgentPointer);

            dos.write(header,0,20);

            dis.read(header);

            dis.read(header);

            header = makeHeader((short)socket.getLocalPort(),(short)socket.getPort(),
                    expected_sequenceNumber, tcp_header.acknowledgementNumber,header_fin_ack_others , tcp_header.window_buffer,
                    (short) 1, tcp_header.urgentPointer);

            dos.write(header,0,20);

            Thread.sleep(1000);



            activity.runOnUiThread(new Runnable() {
                public void run() {
                    progressDialog.dismiss();
                }
            });

            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(activity,"File sent successfully",Toast.LENGTH_LONG).show();
                }
            });

            fileInputStream.close();
            dis.close();
            dos.close();
            socket.close();
        }
        catch (FileNotFoundException exception)
        {

            activity.runOnUiThread(new Runnable() {
                public void run() {
                    progressDialog.dismiss();
                }
            });
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(activity,"File not found",Toast.LENGTH_LONG).show();
                }
            });

            exception.printStackTrace();

        }
        catch (IOException e) {

            activity.runOnUiThread(new Runnable() {
                public void run() {
                    progressDialog.dismiss();
                }
            });
            e.printStackTrace();
        }
        catch (Exception exception)
        {
            exception.printStackTrace();
        }

    }

    private static short makeCheckSum(byte[] fileData,int bytes)
    {
        if (bytes % 2 == 1)
        {
            fileData[bytes] = 0;
            bytes++ ;
        }

        int checkSum = 0;

        for (int i = 0; i < bytes; i += 2)
        {
            int ans = (fileData[i] << 8) + fileData[i+1];
            checkSum += ans;

            if (checkSum > 0xffff) {
                checkSum = (checkSum & 0xffff) + 1;
            }
        }

        checkSum = (short)((~checkSum) & 0xffff) ;
        System.out.println("CheckSum = " + checkSum);
        return (short) checkSum ;
    }

    private static TCP_Header readHeader(byte[] acknowledgement) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(acknowledgement);
        TCP_Header tcp_header = new TCP_Header();
        tcp_header.destPort = byteBuffer.getShort();
        tcp_header.sourcePort = byteBuffer.getShort();
        tcp_header.sequenceNumber = byteBuffer.getInt();
        tcp_header.acknowledgementNumber = byteBuffer.getInt();

        tcp_header.others = byteBuffer.getShort();
        tcp_header.others &= 0b1111111111101111;

        tcp_header.window_buffer = byteBuffer.getShort();
        tcp_header.checkSum = byteBuffer.getShort();
        tcp_header.urgentPointer = byteBuffer.getShort();

        if(tcp_header.sequenceNumber > 10000000)
        {
            System.out.println("Problem in read header");
        }

        return tcp_header;
    }

    private static byte[] getFileLength(long length) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(13);
        byteBuffer.putLong(length);
        return byteBuffer.array();
    }

    public static byte[] makeHeader(Short sourcePort,Short destPort, int sequenceNumber,int ackNumber, int others, Short window_buffer, Short checkSum, Short urgentPointer)
    {
        ByteBuffer byteBuffer = ByteBuffer.allocate(20);
        byteBuffer.putShort(sourcePort);
        byteBuffer.putShort(destPort);
        byteBuffer.putInt(sequenceNumber);
        byteBuffer.putInt(ackNumber);
        byteBuffer.putShort((short)others);
        byteBuffer.putShort(window_buffer);
        byteBuffer.putShort(checkSum);
        byteBuffer.putShort(urgentPointer);

        return byteBuffer.array();
    }


}

class TCP_Header{
    Short sourcePort;
    Short destPort;
    int sequenceNumber;
    int acknowledgementNumber ;
    int others = 0;
    Short window_buffer ;
    Short checkSum ;
    Short urgentPointer ;
}

class DataPacket {
    int sequenceNumber;
    byte[] message = new byte[500];
    int message_length ;
    short checkSum ;
}

