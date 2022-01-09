package com.example.myapplication;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.app.ActionBar;
import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Telephony;
import android.util.Log;
import android.util.Range;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.VideoView;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.TracksInfo;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation;
import com.google.android.exoplayer2.ext.rtmp.RtmpDataSource;
import com.google.android.exoplayer2.ext.rtmp.RtmpDataSourceFactory;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.source.MediaLoadData;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceFactory;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.rtsp.RtspMediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.video.VideoFrameMetadataListener;

import java.io.IOException;
import java.lang.reflect.Array;
import java.security.Permission;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {


    private ExoPlayer player;
    private StyledPlayerView playerView;
    private TextView logs_text;
    private TextView uri_input;
    private String str_log = "";
    private TextView bitrate_text;
    private TextView playback_delay_text;
    private TextView encoding_text;

    // injected bandwidthmeter
    private BandwidthMeter bandwidth;

    protected void logstr (String str) {
        str_log += str + '\n';
        str_log = str_log.substring(0, Math.min(str_log.length(), 2048));
        logs_text.setText(str_log);
    }

    private String encoding_name;
    private String decoder_name;

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //ActivityCompat.requestPermissions(this, perms, 200);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        logs_text = (TextView) ActivityCompat.requireViewById(this, R.id.logsText);
        String ssout = "";
        MediaCodecList list= new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        MediaCodecInfo [] infos = list.getCodecInfos();
        for (int i = 0; i<infos.length; i++) {
            MediaCodecInfo codecInfo = infos[i];
            if(codecInfo.isHardwareAccelerated()) {
                if(codecInfo.isEncoder()) ssout += "[enc]";
                else ssout += "[dec]";
                ssout += "(" + codecInfo.getName() + ")\n";
                String[] types = codecInfo.getSupportedTypes();
                for (int j = 0; j < types.length; j++) {
                    ssout += types[j];
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

        final Handler handler = new Handler();
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
                handler.postDelayed(this, 1000);
            }
        };
        uri_input.setText("rtmp://192.168.137.130/live/livestream");
//runnable must be execute once
        handler.post(runnable);
    }

    public void onSettingsButtonClicked (View view) {
        String urit = uri_input.getEditableText().toString();
        Uri uri = Uri.parse(urit);
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
            logstr(e.toString());
        }
    }
}