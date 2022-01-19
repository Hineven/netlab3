package com.example.player.net;

import android.content.Context;
import android.net.Network;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.example.player.AppRes;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class AcquireTranscodedStream implements Runnable {

    Context context;
    Handler handler;
    NetRes net;
    AppRes res;
    public AcquireTranscodedStream (Context context, Handler handler, AppRes res, NetRes net) {
        this.context = context;
        this.handler = handler;
        this.res = res;
        this.net = net;
    }
    @Override
    public void run () {
        Message msg = new Message();
        try {
            msg.getData().putString("log", "transcode " + res.request_video_uri.toString());
            net.middlebox_os.write((res.request_video_uri.toString()+'\n').getBytes(StandardCharsets.UTF_8));
            net.middlebox_os.flush();
            byte [] buf = new byte[1024];
            int off = 0;
            while(true) {
                int nb = net.middlebox_is.read(buf, off, buf.length - off);
                int i;
                for (i = off; i < off + nb; i++) if (buf[i] == '\n') break;
                if(i != off+nb) {
                    off = i;
                    break ;
                }
                off = i;
            }
            String ss = (new String(buf)).substring(0, off);
            msg.getData().putString("transcoded", ss);
            handler.sendMessage(msg);
        } catch (IOException e) {
            String s = e.toString();
            msg.getData().putString("log", "acquire stream uri failed: " + s);
            msg.getData().putString("transcoded", "fail");
            handler.sendMessage(msg);
            return ;
        }
    }
}
