#include <stdio.h>

#ifdef __cplusplus
extern "C"
{
#endif
#include <libavformat/avformat.h>
#include <libavutil/mathematics.h>
#include <libavutil/time.h>
#ifdef __cplusplus
};
#endif

int main(int argc, char * argv[])
{    
 
    AVFormatContext *pInFmtContext = NULL;    
    AVStream *in_stream;
    
    AVCodecContext *pInCodecCtx;
    AVCodec *pInCodec;
    AVPacket *in_packet;
 
 
    AVFormatContext * pOutFmtContext;
    AVOutputFormat *outputFmt;
    AVStream * out_stream;
    //AVCodecContext * pOutCodecCtx;
    //AVCodec *pOutCodec;
    //AVPacket *out_packet; 
    //AVFrame *pOutFrame;
    AVRational frame_rate; 
    double duration;
    
	//int picture_size = 0;
    //FILE *fp; 
    int ret;
    const char * default_url = "rtmp://localhost/live/livestream";
    char in_file[128] = {0};
    char out_file[256] = {0};
    
    int videoindex = -1;
	int audioindex = -1;
	int video_frame_count = 0;
	int audio_frame_count = 0;
	int video_frame_size = 0;
	int audio_frame_size = 0;
    int i;
    int got_picture;
 
 
    if(argc < 2){
        printf("Usage: a.out <in_filename> <url>\n");
        return -1;
    }
    
    memcpy(in_file, argv[1], strlen(argv[1]));
    if( argc == 2){
        memcpy(out_file, default_url, strlen(default_url));
    }else{
        memcpy(out_file, argv[2], strlen(argv[2]));
    }
 
    //av_register_all();
    //avformat_network_init();
 
 
    //拉流，设置超时断开连接时间 3s
    AVDictionary* options = NULL;
    av_dict_set(&options, "stimeout", "3000000", 0);
    
    // Open an input stream and read the header,
    if (avformat_open_input ( &pInFmtContext, in_file, NULL, &options) < 0){
        printf("avformat_open_input failed\n");         
		return -1;    
    }
    
    //查询输入流中的所有流信息
	if( avformat_find_stream_info(pInFmtContext, NULL) < 0){
		printf("avformat_find_stream_info failed\n");
		return -1;
	}
    //打印流的信息
	av_dump_format(pInFmtContext, 0, in_file, 0); 
 
 
    ret = avformat_alloc_output_context2(&pOutFmtContext, NULL, "flv", out_file);
    //初始化一个用于输出的AVFormatContext结构体
    //参数是用来猜格式的
    //https://blog.csdn.net/qq_35044535/article/details/77715655
    if(ret < 0){
        printf("avformat_alloc_output_context2 failed\n");
        return -1;
    }        
    //outputFmt = pOutFmtContext->oformat;
 
 
    for(i=0; i < pInFmtContext->nb_streams; i++){
    //枚举每个stream，加入到 outstream 中，同时找到视频流和音频流
        in_stream = pInFmtContext->streams[i];
        //stream 可能是音频，也可能是视频流
		if( in_stream->codecpar->codec_type == AVMEDIA_TYPE_AUDIO){
			audioindex = i;
		}
        if( in_stream->codecpar->codec_type == AVMEDIA_TYPE_VIDEO){
            videoindex = i;
            frame_rate = av_guess_frame_rate(pInFmtContext, in_stream, NULL);
            printf("video: frame_rate:%d/%d\n", frame_rate.num, frame_rate.den);
            
            printf("video: frame_rate:%d/%d\n", frame_rate.den, frame_rate.num);
            //此处不知为何要把两种顺序都打一遍
            //den 是分母， num 是分子
            duration = av_q2d((AVRational){frame_rate.den, frame_rate.num});
            //av_q2d 分数转小数，返回值类型为double  
            //这里构造的 AVRational 是 frame_rate 的倒数，即 duration
        }
        
        pInCodec = avcodec_find_decoder(in_stream->codecpar->codec_id);
        printf("%x, %d\n", pInCodec, in_stream->codecpar->codec_id);
        //printf("-----%s,%s\n", pInCodec->name, in_stream->codec->codec->name);
        out_stream = avformat_new_stream(pOutFmtContext,  pInCodec);//in_stream->codec->codec);
        //avformat_new_stream 在 AVFormatContext 中创建 Stream 通道
        if( out_stream == NULL){
            printf("avformat_new_stream failed:%d\n",i);
        }
 
        ret = avcodec_parameters_copy(out_stream->codecpar, in_stream->codecpar);
        if( ret < 0){
            printf("avcodec_parameters_copy failed:%d\n", i);
        }
 
        out_stream->codecpar->codec_tag = 0;
 
        if( pOutFmtContext->oformat->flags & AVFMT_GLOBALHEADER){
//AVFMT_GLOBALHEADER代表封装格式包含“全局头”（即整个文件的文件头），大部分封装格式是这样的。一些封装格式没有“全局头”，比如MPEG2TS
            out_stream->codec->flags |= AV_CODEC_FLAG_GLOBAL_HEADER;
        }        
    }
 
 
	av_dump_format(pOutFmtContext, 0, out_file, 1); 
 
    ret = avio_open(&pOutFmtContext->pb, out_file, AVIO_FLAG_WRITE);
    //打开FFmpeg的输入输出文件，指定 url 和读写权限
    if(ret < 0){
        printf("avio_open failed:%d\n", ret);
        return -1;
    }
    int64_t start_time = av_gettime();
 
    ret = avformat_write_header(pOutFmtContext, NULL);
    
    //封装好之后，准备拆成 packet 并推流 

    in_packet = av_packet_alloc(); //封装了 malloc，分配一个结构体大小的内存
    while(1){
        ret = av_read_frame(pInFmtContext, in_packet);
        //av_read_frame()获取视频的一帧，不存在半帧说法。但可以获取音频的若干帧
        // in_packet 获取的是下一帧
        if(ret < 0){  //EOF
            printf("read frame end\n");
            break;
        }
        in_stream = pInFmtContext->streams[in_packet->stream_index];
		
		if(in_packet->stream_index == videoindex){
			video_frame_size += in_packet->size;
            printf("recv %5d video frame %5d-%5d\n", ++video_frame_count, in_packet->size, video_frame_size);
        }
 
        
        if(in_packet->stream_index == audioindex){
			audio_frame_size += in_packet->size;
            printf("recv %5d audio frame %5d-%5d\n", ++audio_frame_count, in_packet->size, audio_frame_size);
        }
        
        /*
        if(in_packet->pts==AV_NOPTS_VALUE){
			//Write PTS
			AVRational time_base1=pInFmtContext->streams[videoindex]->time_base;
			//Duration between 2 frames (us)
			int64_t calc_duration=(double)AV_TIME_BASE/av_q2d(pInFmtContext->streams[videoindex]->r_frame_rate);
			//Parameters
			in_packet->pts=(double)(frame_index*calc_duration)/(double)(av_q2d(time_base1)*AV_TIME_BASE);
			in_packet->dts=in_packet->pts;
			in_packet->duration=(double)calc_duration/(double)(av_q2d(time_base1)*AV_TIME_BASE);
		}
		*/
		
        int codec_type = in_stream->codecpar->codec_type;
        if( codec_type == AVMEDIA_TYPE_VIDEO){
	        AVRational  dst_time_base = {1, AV_TIME_BASE};
	        int64_t pts_time = av_rescale_q(in_packet->pts, in_stream->time_base, dst_time_base); 
	        int64_t now_time = av_gettime() - start_time;
	        if( pts_time > now_time)
	            av_usleep(pts_time - now_time);
        }
 
        out_stream = pOutFmtContext->streams[in_packet->stream_index];
        av_packet_rescale_ts(in_packet,in_stream->time_base, out_stream->time_base);
        //av_packet_rescale_ts()用于将 AVPacket 中各种时间值从一种时间基转换为另一种时间基
        in_packet->pos = -1;
        //byte position in stream, -1 if unknown
        ret = av_interleaved_write_frame(pOutFmtContext, in_packet);
        //将对 packet 进行缓存和 pts 检查，这是区别于 av_write_frame 的地方
        if( ret < 0){
            printf("av_interleaved_write_frame failed\n");
 
            break;
        }
        av_packet_unref(in_packet);
        //https://www.cnblogs.com/wangguchangqing/p/6138683.html
    }
 
    av_write_trailer(pOutFmtContext); //还未输出的AVPacket和文件尾
    av_packet_free(&in_packet);
 
    avformat_close_input(&pInFmtContext);
    avio_close( pOutFmtContext->pb);
    avformat_free_context(pOutFmtContext);
	return 0;
}
