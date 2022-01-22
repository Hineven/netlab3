import os
import subprocess

SRS_ROOT = "/home/soar/srs/trunk/"
SRS_PATH = "objs/srs"
SRS_CONF = "conf/ffmpeg.transcode.conf"
SRS_INIT = "etc/init.d/srs"   # check srs status, or reload
SRS_FFMPEG = "objs/ffmpeg/bin/ffmpeg"

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
    subprocess.run("{} -re -i {} -c copy -f flv {} 1>/dev/null 2>&1 &".format(SRS_FFMPEG, filepath, target), shell=True)
    print("pushing stream {} to {}".format(filepath, target))

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
        "vwidth":          "768",
        "vheight":         "300",
        "vthreads":        "12",
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
    

if __name__ == "__main__":
    set_config()
    start_srs()
    control_srs("reload")
    push_stream("/home/soar/srs/trunk/doc/source.flv", "rtmp://localhost/live/livestream")