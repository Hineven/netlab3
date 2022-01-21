package com.example.player.net;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.RouteInfo;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.example.player.AC;
import com.example.player.AppRes;
import com.example.player.R;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import javax.net.SocketFactory;

public class ConnectToMiddleBox implements Runnable {
    Context context;
    Handler handler;
    NetRes net;
    AppRes res;
    public ConnectToMiddleBox (Context context, Handler handler, AppRes res, NetRes net) {
        this.context = context;
        this.handler = handler;
        this.res = res;
        this.net = net;
    }
    @Override
    public void run () {
        Message msg = new Message();
        Network wifi = null;
        ConnectivityManager con_mgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        for(Network net : con_mgr.getAllNetworks()) {
            NetworkCapabilities caps = con_mgr.getNetworkCapabilities(net);
            if(caps == null) continue ;
            if(!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue ;
            wifi = net;
            break;
        }
        if(wifi == null) {
            msg.getData().putString("log", "Not connected to WIFI");
            msg.getData().putBoolean("connected", false);
            handler.sendMessage(msg);
            return ;
        };
        {
            Message msgi = new Message();
            msgi.getData().putString("log", "connecting to given middlebox: " + res.middlebox_connect_ip);
            handler.sendMessage(msgi);
        }
        // Try to connect to the given middle box
        SocketFactory factory = wifi.getSocketFactory();
        net.middlebox = null;
        try {
            net.middlebox = factory.createSocket();
        } catch (IOException e) {
            msg.getData().putString("log", "socket creation failed: " + e.toString());
            msg.getData().putBoolean("connected", false);
            handler.sendMessage(msg);
            return ;
        }
        try {
            net.middlebox.connect(new InetSocketAddress(res.middlebox_connect_ip, AC.MIDDLEBOX_SERVICE_PORT), 5000);
        } catch (IOException e) {
            String s = e.toString();
            msg.getData().putString("log", "connect failed: " + s);
            msg.getData().putBoolean("connected", false);
            handler.sendMessage(msg);
            return ;
        }
        try {
            net.middlebox_os = net.middlebox.getOutputStream();
            net.middlebox_is = net.middlebox.getInputStream();
        } catch (IOException e) {
            String s = e.toString();
            msg.getData().putString("log", "connect failed: " + s);
            msg.getData().putBoolean("connected", false);
            handler.sendMessage(msg);
            return ;
        }
        String os = "";
        os += res.hardware_accel_codecs.length;
        os += '\n';
        for(String s : res.hardware_accel_codecs)
            os += s + '\n';
        try {
            byte[] in = new byte[2];
            net.middlebox_os.write(os.getBytes(StandardCharsets.UTF_8));
            net.middlebox_os.flush();
            net.middlebox_is.read(in);
            if(!(in[0] == '0' && in[1] == '\n')) {
                msg.getData().putString("log", "unexpected error" );
                msg.getData().putBoolean("connected", false);
                handler.sendMessage(msg);
                return ;
            }
        } catch (IOException e) {
            String s = e.toString();
            msg.getData().putString("log", "connect failed: " + s);
            msg.getData().putBoolean("connected", false);
            handler.sendMessage(msg);
            return ;
        }
        msg.getData().putString("log", "connect success");
        msg.getData().putBoolean("connected", true);
        handler.sendMessage(msg);
        return ;
    }
}
