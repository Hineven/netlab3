import os
import subprocess
import json
import datetime
from time import sleep
import bandwidth as bw

SRS_ROOT = "/home/soar/srs/trunk/"
SRS_PATH = "objs/srs"
SRS_CONF = "conf/ffmpeg.transcode.conf"
SRS_INIT = "etc/init.d/srs"   # check srs status, or reload
SRS_FFMPEG = "objs/ffmpeg/bin/ffmpeg"

BITRATE_TX = None # in bytes
BITRATE_FFMPEG_ORIGINAL = 5000 # in KB
BITRATE_FFMPEG_CURRENT = 5000 # in KB
BITRATE_FFMPEG = None # in KB, dynamically changing
BITRATE_TIMESTAMP = None
BITRATE_ALPHA = 0.9
BITRATE_THRESHOLD = 0.3
BITRATE_INTERVAL = 3 # in seconds

def start_srs():
    """
    Start srs at background
    """
    os.chdir(SRS_ROOT)
    subprocess.run("{} -c {} 1>/dev/null 2>/dev/null &".format(SRS_PATH, SRS_CONF), shell=True)
    print("srs started")

def control_srs(cmd):
    """
    Control srs with etc/init.d/srs
    e.g. reload, status, grace
    """
    os.chdir(SRS_ROOT)
    ret = os.system("{} {}".format(SRS_INIT, cmd))
    return ret

def push_stream(filepath, target):
    """push a video file/stream to the target url at background.

    Args:
        filepath: path to the video.
        target: the url you intend to push.
    """
    os.chdir(SRS_ROOT)
    subprocess.Popen("{} -stream_loop 100 -re -i {} -c copy -f flv {} 1>/dev/null 2>/dev/null &".format(SRS_FFMPEG, filepath, target), shell=True)
    print("pushing stream {} to {}".format(filepath, target))

def get_stream_info(filepath):
    """Use ffprobe to analyze the stream info and fetch useful ones
    """
    os.system("ffprobe -v quiet -print_format json -show_format -show_streams >.stream_info.txt {}".format(filepath))
    with open(".stream_info.txt", "r") as f:
        infos = f.read()
        infos = json.loads(infos)
    subprocess.Popen("rm .stream_info.txt", shell=True)

    video_stream_infos = None
    for stream in infos["streams"]:
        if stream["codec_type"] == "video":
            video_stream_infos = stream
            break
    if video_stream_infos == None:
        raise RuntimeError
    format_infos = infos["format"]
    
    # for key, value in stream_infos.items():
    #     print(f"{key} {value}")
    
    useful_configs = {
        "vbitrate": str(int(format_infos["bit_rate"])//1000),
        "vfps": str(int(eval(video_stream_infos["avg_frame_rate"]))),
        "vwidth": str(video_stream_infos["width"]),
        "vheight": str(video_stream_infos["height"]),
    }
    print(useful_configs)
    return useful_configs

def set_config(**kwargs):
    """rewrite the config file.

    Args:
        kwargs: modify the config according to the kwargs
    """

    config_params = {
        "enabled":         "on",
        "perfile":         ["re"],
        "vfilter":         [],
        "vcodec":          "libx264",
        "vbitrate":        "500",
        "vfps":            "25",
        "vwidth":          "1920",
        "vheight":         "1080",
        "vthreads":        "8",
        "vprofile":        "main",
        "vpreset":         "ultrafast",
        "vparams":         [],
        "acodec":          "copy",
        "aparams":         [],
        "output":          "rtmp://127.0.0.1:[port]/[app]?vhost=[vhost]/[stream]_[engine]"
    }

    # update default parameters
    for key, value in kwargs.items():
        if key in config_params.keys():
            config_params[key] = value

    # construct config file 
    with open(SRS_ROOT+SRS_CONF, "w") as f:
        f.write(
"""# the config for srs use ffmpeg to transcode
# @see https://github.com/ossrs/srs/wiki/v1_CN_SampleFFMPEG
# @see full.conf for detail config.

listen              1935;
max_connections     1000;
vhost __defaultVhost__ {
    transcode {
        enabled     on;
        ffmpeg      ./objs/ffmpeg/bin/ffmpeg;
        engine ff {
"""
        )

        for key, value in config_params.items():
            padding = 16

            if isinstance(value, str):
                key = key.ljust(padding)
                f.write(f"            {key} {value};\n")
            elif isinstance(value, list) or isinstance(value, tuple):
                f.write(f"            {key} {{\n")
                for v in value:
                    f.write(f"                {v};\n")
                f.write(f"            }}\n")
                
            else:
                raise NotImplementedError("value of wrong type in `set_config`")

        f.write(
"""        }
    }
}
"""
        )

def adjust_bitrate():
    """
    Adjust the bitrate and adapt to the network bandwidth according to iftop statistics.
    You should initialize original ffmpeg bitrate before calling adjust_bitrate

    Return:
    0 on success.
    1 on failure.
    """
    global BITRATE_TIMESTAMP, BITRATE_TX, BITRATE_FFMPEG, BITRATE_FFMPEG_CURRENT, BITRATE_FFMPEG_ORIGINAL, BITRATE_ALPHA, BITRATE_THRESHOLD

    if BITRATE_TIMESTAMP is None:
        _, BITRATE_TX =  bw.get_net_data()
        BITRATE_TIMESTAMP = datetime.datetime.now()
        BITRATE_FFMPEG = BITRATE_FFMPEG_ORIGINAL
        return 1
    else:
        prev_bitrate_tx = BITRATE_TX # in bytes
        prev_bitrate_timestamp = BITRATE_TIMESTAMP
        _, BITRATE_TX =  bw.get_net_data()
        BITRATE_TIMESTAMP = datetime.datetime.now()

        delta = BITRATE_TIMESTAMP - prev_bitrate_timestamp
        delta_s = delta.seconds + delta.microseconds / 100000
        # print(delta_s)

        if delta_s < BITRATE_THRESHOLD:
            BITRATE_TX = prev_bitrate_tx
            BITRATE_TIMESTAMP = prev_bitrate_timestamp
            return 1

        speed = (BITRATE_TX - prev_bitrate_tx) * 8 / 1024 / delta_s
        BITRATE_FFMPEG = BITRATE_ALPHA * BITRATE_FFMPEG + (1-BITRATE_ALPHA) * speed

        if (abs(BITRATE_FFMPEG - BITRATE_FFMPEG_CURRENT) / BITRATE_FFMPEG_CURRENT > BITRATE_THRESHOLD):
            print(f"Current Speed {speed} Kb")
            print(f"BITRATE_FFMPEG {BITRATE_FFMPEG} Kb")
            set_config(vbitrate=str(min(int(BITRATE_FFMPEG), int(BITRATE_FFMPEG_ORIGINAL))))
            BITRATE_FFMPEG_CURRENT = BITRATE_FFMPEG
            control_srs("reload")

        return 0
    

if __name__ == "__main__":
    while True:
        sleep(BITRATE_INTERVAL)
        adjust_bitrate()
