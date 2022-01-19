package com.example.player.net;

import android.renderscript.ScriptGroup;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class NetRes {
    // connection to the middlebox
    public Socket middlebox;
    // if the middlebox is ready
    public boolean middlebox_ready;
    public OutputStream middlebox_os;
    public InputStream middlebox_is;
}
