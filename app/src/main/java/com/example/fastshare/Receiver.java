package com.example.fastshare;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Random;

public class Receiver {

    final private receiveActivity myActivity;

    Receiver(receiveActivity activity)
    {
        this.myActivity = activity;
    }

    public void receiveFile(Socket clientSocket, DataInputStream dis, DataOutputStream dos) {
        try {
            ArrayList<DataPacket> bufferQueue = new ArrayList<>();

            int tcpRecordSize = 520;
            int maxPacketSize = 500; //50 bytes
            int flag = 0; //1  means wait for ack
            int expected_sequence_number = 0;
            long startTime ;
            long currentTime;
            long timeout = 100 ;
            int totalBufferSize = maxPacketSize*20 ;
            int currentBufferLoad = 0;
            int bufferPackets = 0;
            int outOfOrderBufferLoad = 0;
            Short window_buffer = (short) (maxPacketSize*20);
            Short checkSum = 1;
            Short urgentPointer = 0;
            int header_syn_others = 0b0101000000000010;
            int header_syn_ack_others = 0b0101000000010010;
            int header_fin_others = 0b0101000000000001;
            int header_ack_others = 0b0101000000010000;
            int others = 0b0101000000000000;
            int header_fin_ack_others = 0b0101000000010001;
            String fileName;
            long fileLength = 0;

            byte[] handshaking = new byte[70];
            int totalBytesRead = dis.read(handshaking);
            TCP_Header current_header = readHeader(handshaking);
            byte[] fileNameArray = new byte[totalBytesRead-20];
            System.arraycopy(handshaking,20,fileNameArray,0,totalBytesRead-20);
            fileName = new String(fileNameArray, StandardCharsets.UTF_8);


            int finalTotalBytesRead = totalBytesRead;
            short finalCheckSum = checkSum;
            myActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try{
                        AlertDialog.Builder builder = new AlertDialog.Builder(myActivity);
                        builder.setMessage("Do you want to receive " + fileName + " ?");
                        builder.setPositiveButton("Allow", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                byte[] temp_header =  makeHeader((short)clientSocket.getLocalPort(),(short)clientSocket.getPort(),current_header.sequenceNumber+ finalTotalBytesRead -20,current_header.acknowledgementNumber,header_syn_ack_others,window_buffer, finalCheckSum,urgentPointer);
                                try {
                                    new Thread(new Runnable() {
                                        @Override
                                        public void run() {
                                            try {
                                                dos.write(temp_header,0,20);
                                            } catch (IOException e) {
                                                e.printStackTrace();
                                            }
                                        }
                                    }).start();
                                }
                                catch (Exception exception)
                                {
                                    Toast.makeText(myActivity,"Exception caught " + exception.toString(),Toast.LENGTH_LONG).show();
                                }
                                dialog.cancel();
                            }
                        });
                        builder.setNegativeButton("Deny", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                // Perform action on "Deny" click

                                dialog.cancel();
                            }
                        });
                        builder.setCancelable(false);
                        builder.setTitle("Ask Permission");
                        AlertDialog dialog = builder.create();

                        dialog.show();

                    }
                    catch (Exception exception)
                    {
                        Toast.makeText(myActivity,"Excception Occurred" + exception.toString(),Toast.LENGTH_SHORT).show();
                    }
                }
            });

            TCP_Header current_header2 = readHeader(handshaking);
            totalBytesRead = dis.read(handshaking);
            byte[] fileLengthArray = new byte[20];
            System.arraycopy(handshaking,20,fileLengthArray,0,totalBytesRead-20);
            fileLength = getFileLength(fileLengthArray);


            myActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(myActivity,"Please stay in this page until file is fully received",Toast.LENGTH_LONG).show();
                }
            });

//            expected_sequence_number = current_header2.sequenceNumber + 8;








            byte[] content = new byte[tcpRecordSize];
            byte[] message = new byte[maxPacketSize];
            byte[] header = new byte[20];

            TCP_Header tcp_header = current_header2;
            int firstSequenceNumber  = 0;
            long mainFileLength = fileLength ;


            File file = new File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    fileName);
            FileOutputStream fileOutputStream = new FileOutputStream(file);

            int packetNumber = 0;

            startTime = System.currentTimeMillis();

            while(true)
            {
                currentTime = System.currentTimeMillis();
                if ((currentTime - startTime) > timeout && flag!=0)
                {
                    System.out.println("Flag = " + flag);
                    Random random = new Random();
                    int temp = random.nextInt(10);
                    if(temp>8)
                    {
                        System.out.println("Halfed buffersize");
                        currentBufferLoad = currentBufferLoad - (bufferPackets/2)*maxPacketSize;
                        System.out.println(totalBufferSize - currentBufferLoad - outOfOrderBufferLoad);
                    }
                    else if(temp>6)
                    {
                        System.out.println("1/4 buffer size");
                        currentBufferLoad = currentBufferLoad - (bufferPackets/4)*maxPacketSize;
                        System.out.println(totalBufferSize - currentBufferLoad - outOfOrderBufferLoad);
                    }
                    else
                    {
                        currentBufferLoad = 0;
                        System.out.println(totalBufferSize - currentBufferLoad - outOfOrderBufferLoad);
                    }



                    byte[] temp_header = makeHeader(tcp_header.sourcePort, tcp_header.destPort,
                            expected_sequence_number, tcp_header.acknowledgementNumber, tcp_header.others,
                            (short)(totalBufferSize-currentBufferLoad - outOfOrderBufferLoad), tcp_header.checkSum, tcp_header.urgentPointer);

                    System.arraycopy(temp_header,0,header,0,20);

                    System.out.println("FileLength = " + fileLength);

                    //every acknowledgement has 5% chance of getting lost
                    int value = random.nextInt(90);
                    if(value >= 95)
                    {
                        System.out.println("Acknowledgement Lost");
                        startTime = System.currentTimeMillis();
                        currentBufferLoad = 0;
                        bufferPackets =0;
                        continue ;
                    }
                    else
                    {
                        dos.write(header,0,20);

                        if( (firstSequenceNumber + mainFileLength) == expected_sequence_number)
                        {
                            break;
                        }

                        TCP_Header abcdefgh = readHeader(header) ;

                        System.out.println("Timeout");
                        System.out.println("Acknowledgement sent of (expected)" + expected_sequence_number );
                        System.out.println("Acknowledgement sent of (real)" + abcdefgh.sequenceNumber);



                        startTime = System.currentTimeMillis();
                        currentBufferLoad = 0;
                        bufferPackets =0;
                    }


                }

                if(fileLength<=0)
                {
                    continue ;
                }

                if (flag == 0)
                {
                    if(dis.available() > 0)
                    {
                        packetNumber++;
                        totalBytesRead = dis.read(content);
                        tcp_header = readHeader(content);
                        firstSequenceNumber = tcp_header.sequenceNumber ;
                        System.out.println("First Sequence Number = " + tcp_header.sequenceNumber);
                        System.out.println("Packet Received with sequence number " + tcp_header.sequenceNumber);
                        Log.d("Message","First Sequence Number = " + tcp_header.sequenceNumber);
                        Log.d("Message","Packet Received with sequence number " + tcp_header.sequenceNumber);


                        System.arraycopy(content, 20, message, 0, totalBytesRead - 20);
                        fileOutputStream.write(message, 0, totalBytesRead - 20);
                        fileLength -= (totalBytesRead - 20);
                        expected_sequence_number = tcp_header.sequenceNumber + (totalBytesRead - 20);

                        currentBufferLoad += (totalBytesRead-20);
                        bufferPackets++;

                        flag = 1;
                    }
                }
                else
                {
                    if(dis.available()>0)
                    {
                        packetNumber++;
                        totalBytesRead = dis.read(content);
                        tcp_header = readHeader(content);
                        System.out.println("Total bytes read = " + totalBytesRead);
                        System.arraycopy(content, 20, message, 0, totalBytesRead - 20);

                        System.out.println("Packet Received with sequence number " + tcp_header.sequenceNumber
                                + "and expected sequence Number = " + expected_sequence_number);

                        Log.d("Message","Packet Received with sequence number " + tcp_header.sequenceNumber
                                + "and expected sequence Number = " + expected_sequence_number);

                        checkSum = makeCheckSum(message,totalBytesRead-20);

                        //3% of data packets will be treated as corrupted dataPackets
                        Random random = new Random();
                        int value = random.nextInt(90);
                        if(value >= 97)
                        {
                            checkSum = 0;
                        }

                        if(!checkCorrupt(checkSum,tcp_header.checkSum))
                        {
                            System.out.println("CheckSum Matched.CheckSum = " + checkSum);
                        }
                        else
                        {
                            System.out.println("CheckSum didn't match. Expected = " + tcp_header.checkSum + ". Real = " + checkSum);
                            Log.d("Message","CheckSum didn't match. Expected = " + tcp_header.checkSum + ". Real = " + checkSum);
                            random = new Random();
                            int temp = random.nextInt(10);

                            if(temp>8)
                            {
                                currentBufferLoad = currentBufferLoad - (bufferPackets/2)*maxPacketSize;
                            }
                            else if(temp>6)
                            {
                                currentBufferLoad = currentBufferLoad - (bufferPackets/4)*maxPacketSize;
                            }
                            else
                            {
                                currentBufferLoad = 0;
                            }


                            byte[] temp_header = makeHeader(tcp_header.sourcePort, tcp_header.destPort,
                                    expected_sequence_number, tcp_header.acknowledgementNumber, tcp_header.others,
                                    (short)(totalBufferSize-currentBufferLoad - outOfOrderBufferLoad), tcp_header.checkSum, tcp_header.urgentPointer);

                            System.arraycopy(temp_header,0,header,0,20);
                            dos.write(header,0,20);

                            TCP_Header abcdefgh = readHeader(header) ;
                            System.out.println("Corrupted Data!!!");
                            System.out.println("Acknowledgement sent of (expected)" + expected_sequence_number );
                            System.out.println("Acknowledgement sent of (real)" + abcdefgh.sequenceNumber);

                            Log.d("Message","Corrupted Data!!!");
                            Log.d("Message","Acknowledgement sent of (expected)" + expected_sequence_number );
                            Log.d("Message","Acknowledgement sent of (real)" + abcdefgh.sequenceNumber);

                            startTime = System.currentTimeMillis();
                            currentBufferLoad = 0;
                            bufferPackets =0;

                            continue ;
                        }

                        if (expected_sequence_number == tcp_header.sequenceNumber) {

                            fileOutputStream.write(message, 0, totalBytesRead - 20);
                            fileLength -= (totalBytesRead - 20);
                            expected_sequence_number = expected_sequence_number + (totalBytesRead - 20);
                            currentBufferLoad += (totalBytesRead-20);
                            bufferPackets ++;

                            while (!bufferQueue.isEmpty() && fileLength>0) {
                                DataPacket dataPacket = bufferQueue.get(0);
                                if(dataPacket.sequenceNumber == expected_sequence_number)
                                {
                                    byte[] tempByteArray = new byte[maxPacketSize] ;
                                    System.arraycopy(dataPacket.message,0,tempByteArray,0,dataPacket.message_length);

                                    fileOutputStream.write(tempByteArray);
                                    fileLength -= dataPacket.message_length;
                                    bufferQueue.remove(0);
                                    outOfOrderBufferLoad -= dataPacket.message_length; ;
                                    expected_sequence_number = expected_sequence_number + dataPacket.message_length ;
                                }
                                else
                                {
                                    break;
                                }
                            }
                        }
                        else if(tcp_header.sequenceNumber > expected_sequence_number)
                        {
                            System.out.println("Sequence Unmatched. Expected Sequence Number = " + expected_sequence_number);
                            System.out.println("Current sequence Number = " + tcp_header.sequenceNumber);
                            System.out.println("Problem Detected");

                            Log.d("Message","Sequence Unmatched. Expected Sequence Number = " + expected_sequence_number);
                            Log.d("Message","Current sequence Number = " + tcp_header.sequenceNumber);
                            Log.d("Message","Problem Detected");

                            DataPacket dataPacket;
                            dataPacket = new DataPacket();
                            dataPacket.sequenceNumber = tcp_header.sequenceNumber;
                            System.arraycopy(message,0,dataPacket.message,0,totalBytesRead-20);
                            dataPacket.message_length = totalBytesRead - 20;
                            bufferQueue.add(dataPacket);
                            outOfOrderBufferLoad += (totalBytesRead - 20);

                            random = new Random();
                            int temp = random.nextInt(10);

                            if(temp>8)
                            {
                                currentBufferLoad = currentBufferLoad - (bufferPackets/2)*maxPacketSize;
                            }
                            else if(temp>6)
                            {
                                currentBufferLoad = currentBufferLoad - (bufferPackets/4)*maxPacketSize;
                            }
                            else
                            {
                                currentBufferLoad = 0;
                            }


                            byte[] temp_header = makeHeader(tcp_header.sourcePort, tcp_header.destPort,
                                    expected_sequence_number, tcp_header.acknowledgementNumber, tcp_header.others,
                                    (short)(totalBufferSize-currentBufferLoad - outOfOrderBufferLoad), tcp_header.checkSum, tcp_header.urgentPointer);

                            System.arraycopy(temp_header,0,header,0,20);
                            dos.write(header,0,20);

                            TCP_Header abcdefgh = readHeader(header) ;
                            System.out.println("Wrong Sequence Number");
                            System.out.println("Acknowledgement sent of (expected)" + expected_sequence_number );
                            System.out.println("Acknowledgement sent of (real)" + abcdefgh.sequenceNumber);


                            Log.d("Message","Wrong Sequence Number");
                            Log.d("Message","Acknowledgement sent of (expected)" + expected_sequence_number );
                            Log.d("Message","Acknowledgement sent of (real)" + abcdefgh.sequenceNumber);

                            startTime = System.currentTimeMillis();
                            currentBufferLoad = 0;
                            bufferPackets =0;

                        }
                    }
                }
            }


            //TCP connection closure
            dis.read(header);

            header = makeHeader((short)clientSocket.getLocalPort(),(short)clientSocket.getPort(),
                    expected_sequence_number, tcp_header.acknowledgementNumber,header_fin_ack_others , tcp_header.window_buffer,
                    (short) 1, tcp_header.urgentPointer);

            dos.write(header,0,20);

            header = makeHeader((short)clientSocket.getLocalPort(),(short)clientSocket.getPort(),
                    expected_sequence_number, tcp_header.acknowledgementNumber,header_fin_others , tcp_header.window_buffer,
                    (short) 1, tcp_header.urgentPointer);

            dos.write(header,0,20);

            dis.read(header);



            fileOutputStream.close();
            dis.close();
            dos.close();
            clientSocket.close();

            System.out.println("File Received Successfully");



            myActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    AlertDialog.Builder builder = new AlertDialog.Builder(myActivity);
                    builder.setMessage("File is successfully transferred");
                    builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();
                        }
                    });
                    builder.setTitle("File Transfer");
                    builder.setCancelable(false);
                    AlertDialog dialog = builder.create();
                    dialog.show();
                }
            });


        }
        catch (Exception e)
        {
            System.out.println("IOException in receiveFile ");
            e.printStackTrace();
            myActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(myActivity,e.toString(),Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private static boolean checkCorrupt(short checkSumEvaluated, short checkSumSent) {
        int temp = checkSumEvaluated + checkSumSent ;
        System.out.println("Temp = " + temp);
        if(temp == (short)0xffff)
        {
            return false ;
        }
        else
        {
            return true ;
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

        System.out.println("CheckSum = " + checkSum);
        return (short) checkSum ;
    }

    public static long getFileLength(byte[] message)
    {
        ByteBuffer byteBuffer = ByteBuffer.wrap(message);
        return byteBuffer.getLong();
    }

    private static TCP_Header readHeader(byte[] acknowledgement) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(acknowledgement);
        TCP_Header tcp_header = new TCP_Header();
        tcp_header.destPort = byteBuffer.getShort();
        tcp_header.sourcePort = byteBuffer.getShort();
        tcp_header.sequenceNumber = byteBuffer.getInt();
        tcp_header.acknowledgementNumber = byteBuffer.getInt();

        tcp_header.others = byteBuffer.getShort();
        tcp_header.others &= 0b1111111111111111;

        tcp_header.window_buffer = byteBuffer.getShort();
        tcp_header.checkSum = byteBuffer.getShort();
        tcp_header.urgentPointer = byteBuffer.getShort();

        if(tcp_header.sequenceNumber >1000000)
        {
            System.out.println("Problem in read header");
        }

        return tcp_header;
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

        if(sequenceNumber > 1000000)
        {
            System.out.println("Problem in makeHeader");
        }

        return byteBuffer.array();
    }
}
