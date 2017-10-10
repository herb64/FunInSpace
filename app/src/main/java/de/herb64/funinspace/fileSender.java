package de.herb64.funinspace;

import android.content.Context;
import android.util.Log;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.Enumeration;

/**
 * Created by herbert on 9/17/17.
 */

/*
This works together with a python socket receiver which stores the file content.


Interesting, this list is returned in AVD
09-17 08:17:11.248 3687-3687/de.herb64.funinspace I/HFCM: Found address: /::1%1%1
09-17 08:17:11.248 3687-3687/de.herb64.funinspace I/HFCM: Found address: /127.0.0.1
09-17 08:17:11.248 3687-3687/de.herb64.funinspace I/HFCM: Found address: /fe80::5054:ff:fe12:3456%eth0%2
09-17 08:17:11.248 3687-3687/de.herb64.funinspace I/HFCM: Found address: /fec0::5054:ff:fe12:3456%2%2
09-17 08:17:11.248 3687-3687/de.herb64.funinspace I/HFCM: Found address: /10.0.2.15

[herbert@devel1 ~]$ cd /home/herbert/Android/Sdk/platform-tools
[herbert@devel1 platform-tools]$ adb shell
root@generic_x86_64:/ # ifconfig eth0
eth0: ip 10.0.2.15 mask 255.255.255.0 flags [up broadcast running multicast]
root@generic_x86_64:/ #

https://developer.android.com/studio/run/emulator-networking.html

The development machine can be pinged on 10.0.2.2, see above google docs


see also
https://coderanch.com/t/596202/Transfer-File-Android-server-socket

make sure, that you start this within a thread to avoid networkinmanthread exception!

TODO: document that stuff and improve this later - this is just a really ugly implementation
 */

// access level can be package private ...
    // see https://docs.oracle.com/javase/tutorial/java/javaOO/accesscontrol.html
    // just no specifier
class fileSender implements Runnable {

    private String myIP;
    private Context ctx;
    private String destIP;
    private int destPort;
    private String fileToSend;
    private OutputStream out;
    private DataOutputStream dataout;   // might be a better alternative to send int, bool etc...
    private String logString;

    private static final int MAXLEN = 1024;

    fileSender(Context ctx, String destIP, int destPort, String fileToSend) {
        this.ctx = ctx;
        this.destIP = destIP;
        this.destPort = destPort;
        this.fileToSend = fileToSend;
        myIP = getMyIP();
    }

    @Override
    public void run() {
        logString = "";
        try {
            //SystemClock.sleep(4000);
            SocketAddress sockaddr = new InetSocketAddress(destIP, destPort);
            Socket sock = new Socket();
            sock.connect(sockaddr);
            if (sock.isConnected()) {
                //in = sock.getInputStream();
                // having a simple outputstream
                // out = sock.getOutputStream();
                // or alternative: dataoutputstream for more convenience - 21.09.2017
                try {
                    //create write stream to send information
                    dataout=new DataOutputStream(sock.getOutputStream());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                Log.i("HFCM", "connected...");
                try {
                    // 20.09.2017 - getting error "contains a path separator" if using path name
                    // http://www.praveenboyalapalli.com/2015/02/calling-openfileinputstring-name-gives-java-lang-illegalargumentexception-contains-a-path-separator/
                    //FileInputStream fileInputStream = ctx.openFileInput(fileToSend);
                    // ugly, we could better pass name instead path to the thread as well ...
                    // TODO this is for documentation, put into lalatex doc
                    File file2send = new File(fileToSend);
                    FileInputStream fileInputStream = new FileInputStream (file2send);
                    // bufferedreader and inputstreamreader - shouln't be necessary here... ???
                    // these are useful for line by line reading etc..
                    byte[] buff = new byte[MAXLEN];
                    try {
                        // fill stream with filenamelength, filename, filelength, filecontents
                        dataout.writeInt(fileToSend.length());
                        dataout.write(fileToSend.getBytes(), 0, fileToSend.length());
                        dataout.writeLong(file2send.length());
                        int nread = 0;
                        while (nread >= 0) {
                            nread = fileInputStream.read(buff, 0, MAXLEN);
                            if (nread > 0) {
                                dataout.write(buff, 0, nread); // limit last send size
                            }
                        }
                        dataout.flush();
                        logString = "OK";
                    } catch(Exception e) {
                        // see different exceptions here...
                        // https://developer.android.com/reference/java/io/FileInputStream.html#read(byte[],%20int,%20int)
                        logString = e.toString();
                        e.printStackTrace();
                    }
                } catch (FileNotFoundException e) {
                    logString = e.toString();
                    e.printStackTrace();
                }
                sock.close();
             }
        }catch (Exception e) {
            logString = e.toString();
            e.printStackTrace();
        }
    }

    // gets the ip address of your phone's network - not needed here for the receiver
    private String getMyIP() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    Log.i("HFCM", "Found address: " + inetAddress.toString());
                    //if (!inetAddress.isLoopbackAddress()) { return inetAddress.getHostAddress().toString(); }
                }
            }
        } catch (SocketException ex) {
            Log.e("ServerActivity", ex.toString());
        }
        return null;
    }

    // We are able to return information from thread - this is package private
    String getLogString() {
        return logString;
    }
}
