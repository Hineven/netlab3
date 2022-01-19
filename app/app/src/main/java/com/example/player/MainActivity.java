package com.example.player;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.RouteInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.player.net.AcquireTranscodedStream;
import com.example.player.net.ConnectToMiddlebox;
import com.example.player.net.NetRes;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation;
import com.google.android.exoplayer2.ext.rtmp.RtmpDataSource;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.net.SocketFactory;

public class MainActivity extends AppCompatActivity {


    // UI
    private StyledPlayerView playerView;
    private TextView uri_input;
    private String str_log = "";
    private TextView bitrate_text;
    private TextView playback_delay_text;
    private TextView encoding_text;
    private Button net_button;
    private Button play_button;
    private String encoding_name;
    private String decoder_name;

    // Player
    private ExoPlayer player;
    // injected bandwidthmeter
    private BandwidthMeter bandwidth;
    // Event handler
    private Handler handler;
    // Network relating objects
    private NetRes net;
    // Application relating resources
    private AppRes res;

    protected void logstr (String str) {
        str_log += str + '\n';
        str_log = str_log.substring(0, Math.min(str_log.length(), 2048));
    }
    protected void alert (String str) {
        new AlertDialog.Builder(this)
                .setTitle("Alert")
                .setMessage(str)
                .setPositiveButton(android.R.string.yes, null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }
    protected void detectDeviceCodecInfo () {
        String ssout = "";
        MediaCodecList list= new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        MediaCodecInfo [] infos = list.getCodecInfos();
        ArrayList<String> codes = new ArrayList<String>();
        for (int i = 0; i<infos.length; i++) {
            MediaCodecInfo codecInfo = infos[i];
            if(codecInfo.isHardwareAccelerated()) {
                if(codecInfo.isEncoder()) ssout += "[enc]";
                else ssout += "[dec]";
                ssout += "(" + codecInfo.getName() + ")\n";
                String[] types = codecInfo.getSupportedTypes();
                for (int j = 0; j < types.length; j++) {
                    ssout += types[j];
                    codes.add(types[j]);
                    ssout += '\n';
                    MediaCodecInfo.CodecCapabilities caps = codecInfo.getCapabilitiesForType(types[j]);
                    if(caps.getVideoCapabilities() != null) {
                        List<MediaCodecInfo.VideoCapabilities.PerformancePoint> points = caps.getVideoCapabilities().getSupportedPerformancePoints();
                        if(points != null)
                            for(int k = 0; k<points.size(); k++) {
                                MediaCodecInfo.VideoCapabilities.PerformancePoint point = points.get(k);
                                ssout += point.toString() + ';';
                            }
                    }
                }
                ssout += '\n';
            }
        }
        logstr(ssout);
        res.hardware_accel_codecs = codes.toArray(new String[0]);
    }
    protected void initializeUI () {
        uri_input = (TextView) ActivityCompat.requireViewById(this, R.id.streamUriText);
        bandwidth = new DefaultBandwidthMeter.Builder(getApplicationContext()).build();
        RtmpDataSource.Factory rtmpDataSourceFactory = new RtmpDataSource.Factory();
        rtmpDataSourceFactory.setTransferListener(bandwidth.getTransferListener());

        playerView = ActivityCompat.requireViewById(this, R.id.videoView);
        player = new ExoPlayer.Builder(getApplicationContext()).setMediaSourceFactory(
                new DefaultMediaSourceFactory(rtmpDataSourceFactory)
        ).build();
        playerView.setPlayer(player);

        LinearLayout status_list = ActivityCompat.requireViewById(this, R.id.statusList);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1.f
        );
        LinearLayout bitrate = new LinearLayout(getApplicationContext());
        bitrate.setOrientation(LinearLayout.HORIZONTAL);
        TextView bitrate_label = new TextView(getApplicationContext());
        bitrate_label.setText("bitrate");
        bitrate_label.setLayoutParams(params);
        bitrate.addView(bitrate_label);
        bitrate_text = new TextView(getApplicationContext());
        bitrate_text.setLayoutParams(params);
        bitrate_text.setText("/");
        bitrate_text.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
        bitrate.addView(bitrate_text);
        status_list.addView(bitrate);

        LinearLayout delay = new LinearLayout(getApplicationContext());
        delay.setOrientation(LinearLayout.HORIZONTAL);
        TextView delay_label = new TextView(getApplicationContext());
        delay_label.setText("delay");
        delay_label.setLayoutParams(params);
        delay.addView(delay_label);
        playback_delay_text = new TextView(getApplicationContext());
        playback_delay_text.setText("/");
        playback_delay_text.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
        playback_delay_text.setLayoutParams(params);
        delay.addView(playback_delay_text);
        status_list.addView(delay);

        LinearLayout encoding = new LinearLayout(getApplicationContext());
        encoding.setOrientation(LinearLayout.HORIZONTAL);
        TextView encoding_label = new TextView(getApplicationContext());
        encoding_label.setText("encoding");
        encoding_label.setLayoutParams(params);
        encoding.addView(encoding_label);
        encoding_text = new TextView(getApplicationContext());
        encoding_text.setText("/");
        encoding_text.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
        encoding_text.setLayoutParams(params);
        encoding.addView(encoding_text);

        status_list.addView(encoding);

        net_button = ActivityCompat.requireViewById(this, R.id.net);
        play_button = ActivityCompat.requireViewById(this, R.id.play_button);
    }


    protected void playStream (Uri uri) {
        try {
            if(player.isPlaying()) {
                player.stop();
            }
            // Build the media item.
            MediaItem mediaItem = MediaItem.fromUri(uri);
            // Set the media item to be played.
            player.setMediaItem(mediaItem);
            // Prepare the player.
            player.prepare();
            // Start the playback.
            player.play();
        } catch (Exception e) {
            alert(e.toString());
        }
    }

    class AppEventHandler extends Handler {
        public AppEventHandler(@NonNull Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            Bundle data = msg.getData();
            if(data.containsKey("log")) {
                logstr(data.getString("log"));
            }
            if(data.containsKey("connected")) {
                if(data.getBoolean("connected")) {
                    net_button.setEnabled(false);
                    net_button.setText("Connected");
                    net.middlebox_ready = true;
                } else {
                    net.middlebox_ready = false;
                    net_button.setEnabled(true);
                    net_button.setText("Connect");
                    alert("Failed to connect to the middle box.");
                }
            }
            if(data.containsKey("transcoded")) {
                String cmd = data.getString("transcoded");
                if(cmd == "fail") alert("Failed to acquire a transcoded stream from the middle box.");
                else {
                    logstr("acquired: " + cmd);
                    playStream(Uri.parse(cmd));
                }
                play_button.setEnabled(true);
            }
        }
    };

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //ActivityCompat.requestPermissions(this, perms, 200);
        super.onCreate(savedInstanceState);
        // Initialize net res container
        net = new NetRes();
        // Initialize app res container
        res = new AppRes();
        setContentView(R.layout.activity_main);
        detectDeviceCodecInfo();
        initializeUI();
        uri_input.setText("rtmp://192.168.137.130/live/livestream");

        // UI done
        // Registering analytics listeners
        player.addAnalyticsListener(new AnalyticsListener() {
            @Override
            public void onVideoInputFormatChanged(EventTime eventTime, Format format, @Nullable DecoderReuseEvaluation decoderReuseEvaluation) {
                encoding_name = format.codecs + "; " + format.width + "*" + format.height;
                encoding_text.setText(encoding_name + " \n decoder: " + decoder_name);
            }
            @Override
            public void onVideoDecoderInitialized(EventTime eventTime, String decoderName, long initializedTimestampMs, long initializationDurationMs) {
                decoder_name = decoderName;
                encoding_text.setText(encoding_name + " \n decoder: " + decoder_name);
            }
        });
        final Handler vhandler = new Handler();
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                long bitr = bandwidth.getBitrateEstimate();
                bitrate_text.setText("" + bitr/1024 + "kbps");
                Timeline tl = player.getCurrentTimeline();
                if(tl != null && tl.getFirstWindowIndex(false) != -1) {
                    Timeline.Window wd = new Timeline.Window();
                    tl.getWindow(tl.getFirstWindowIndex(false), wd);
                    long netdelay;
                    String dltext = player.isCurrentMediaItemLive() ? "live" : "playback";
                    if (player.getDuration() == C.TIME_UNSET || wd.windowStartTimeMs == C.TIME_UNSET)
                        netdelay = -1;
                    else
                        netdelay = wd.getCurrentUnixTimeMs() - player.getDuration() + wd.windowStartTimeMs;
                    if (netdelay != -1) dltext += " / " + (double) netdelay / 1000 + "s";
                    else dltext += " / unknown";
                    playback_delay_text.setText(dltext);
                }
                //also call the same runnable to call it at regular interval
                vhandler.postDelayed(this, 1000);
            }
        };
        //runnable must be execute once
        vhandler.post(runnable);


        handler = new AppEventHandler(getMainLooper());

    }

    public void onPlayButtonClicked (View view) {
        if(!net.middlebox_ready) {
            alert("Middle box is not ready!");
            return ;
        }
        String urit = uri_input.getEditableText().toString();
        res.request_video_uri = Uri.parse(urit);
        play_button.setEnabled(false);
        new Thread(new AcquireTranscodedStream(getApplicationContext(), handler, res, net)).start();
    }
    
    public void onNetworkButtonClicked (View view) {
        net_button.setEnabled(false);
        net_button.setText("Connecting");
        new Thread(new ConnectToMiddlebox(getApplicationContext(), handler, res, net)).start();
    }

    public void onLogsButtonClicked (View view) {
        Intent intent = new Intent(this, LogsActivity.class);
        intent.putExtra("logs", str_log);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

}