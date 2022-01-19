import os

SRS_ROOT = "/home/hineven/packages/srs/trunk/"
SRS_PATH = "objs/srs"
SRS_CONF = "conf/ffmpeg.transcode.conf"
SRS_INIT = "etc/init.d/srs"   # check srs status, or reload
SRS_FFMPEG = "objs/ffmpeg/bin/ffmpeg"

def start_srs():
    os.chdir(SRS_ROOT)
    os.system("{} -c {}".format(SRS_PATH, SRS_CONF))

def reload_srs():
    os.chdir(SRS_ROOT)
    os.system("{} reload".format(SRS_INIT))

def push_stream(filepath, target):
    """push a video file/stream to the target url.

    Args:
        filepath: path to the video.
        target: the url you intend to push.
    """
    os.system("{} -re -i {} -c copy -f flv {}".format(SRS_FFMPEG, filepath, target))

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
    start_srs()
    set_config()
    reload_srs()
    push_stream(SRS_ROOT+"doc/source.flv", "rtmp://localhost/live/livestream")
    os.sleep(10)
    set_config(vheight="600")
    reload_srs()
    
