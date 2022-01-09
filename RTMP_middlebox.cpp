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
 
 
    //���������ó�ʱ�Ͽ�����ʱ�� 3s
    AVDictionary* options = NULL;
    av_dict_set(&options, "stimeout", "3000000", 0);
    
    // Open an input stream and read the header,
    if (avformat_open_input ( &pInFmtContext, in_file, NULL, &options) < 0){
        printf("avformat_open_input failed\n");         
		return -1;    
    }
    
    //��ѯ�������е���������Ϣ
	if( avformat_find_stream_info(pInFmtContext, NULL) < 0){
		printf("avformat_find_stream_info failed\n");
		return -1;
	}
    //��ӡ������Ϣ
	av_dump_format(pInFmtContext, 0, in_file, 0); 
 
 
    ret = avformat_alloc_output_context2(&pOutFmtContext, NULL, "flv", out_file);
    //��ʼ��һ�����������AVFormatContext�ṹ��
    //�����������¸�ʽ��
    //https://blog.csdn.net/qq_35044535/article/details/77715655
    if(ret < 0){
        printf("avformat_alloc_output_context2 failed\n");
        return -1;
    }        
    //outputFmt = pOutFmtContext->oformat;
 
 
    for(i=0; i < pInFmtContext->nb_streams; i++){
    //ö��ÿ��stream�����뵽 outstream �У�ͬʱ�ҵ���Ƶ������Ƶ��
        in_stream = pInFmtContext->streams[i];
        //stream ��������Ƶ��Ҳ��������Ƶ��
		if( in_stream->codecpar->codec_type == AVMEDIA_TYPE_AUDIO){
			audioindex = i;
		}
        if( in_stream->codecpar->codec_type == AVMEDIA_TYPE_VIDEO){
            videoindex = i;
            frame_rate = av_guess_frame_rate(pInFmtContext, in_stream, NULL);
            printf("video: frame_rate:%d/%d\n", frame_rate.num, frame_rate.den);
            
            printf("video: frame_rate:%d/%d\n", frame_rate.den, frame_rate.num);
            //�˴���֪Ϊ��Ҫ������˳�򶼴�һ��
            //den �Ƿ�ĸ�� num �Ƿ���
            duration = av_q2d((AVRational){frame_rate.den, frame_rate.num});
            //av_q2d ����תС��������ֵ����Ϊdouble  
            //���ﹹ��� AVRational �� frame_rate �ĵ������� duration
        }
        
        pInCodec = avcodec_find_decoder(in_stream->codecpar->codec_id);
        printf("%x, %d\n", pInCodec, in_stream->codecpar->codec_id);
        //printf("-----%s,%s\n", pInCodec->name, in_stream->codec->codec->name);
        out_stream = avformat_new_stream(pOutFmtContext,  pInCodec);//in_stream->codec->codec);
        //avformat_new_stream �� AVFormatContext �д��� Stream ͨ��
        if( out_stream == NULL){
            printf("avformat_new_stream failed:%d\n",i);
        }
 
        ret = avcodec_parameters_copy(out_stream->codecpar, in_stream->codecpar);
        if( ret < 0){
            printf("avcodec_parameters_copy failed:%d\n", i);
        }
 
        out_stream->codecpar->codec_tag = 0;
 
        if( pOutFmtContext->oformat->flags & AVFMT_GLOBALHEADER){
//AVFMT_GLOBALHEADER�����װ��ʽ������ȫ��ͷ�����������ļ����ļ�ͷ�����󲿷ַ�װ��ʽ�������ġ�һЩ��װ��ʽû�С�ȫ��ͷ��������MPEG2TS
            out_stream->codec->flags |= AV_CODEC_FLAG_GLOBAL_HEADER;
        }        
    }
 
 
	av_dump_format(pOutFmtContext, 0, out_file, 1); 
 
    ret = avio_open(&pOutFmtContext->pb, out_file, AVIO_FLAG_WRITE);
    //��FFmpeg����������ļ���ָ�� url �Ͷ�дȨ��
    if(ret < 0){
        printf("avio_open failed:%d\n", ret);
        return -1;
    }
    int64_t start_time = av_gettime();
 
    ret = avformat_write_header(pOutFmtContext, NULL);
    
    //��װ��֮��׼����� packet ������ 

    in_packet = av_packet_alloc(); //��װ�� malloc������һ���ṹ���С���ڴ�
    while(1){
        ret = av_read_frame(pInFmtContext, in_packet);
        //av_read_frame()��ȡ��Ƶ��һ֡�������ڰ�֡˵���������Ի�ȡ��Ƶ������֡
        // in_packet ��ȡ������һ֡
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
        //av_packet_rescale_ts()���ڽ� AVPacket �и���ʱ��ֵ��һ��ʱ���ת��Ϊ��һ��ʱ���
        in_packet->pos = -1;
        //byte position in stream, -1 if unknown
        ret = av_interleaved_write_frame(pOutFmtContext, in_packet);
        //���� packet ���л���� pts ��飬���������� av_write_frame �ĵط�
        if( ret < 0){
            printf("av_interleaved_write_frame failed\n");
 
            break;
        }
        av_packet_unref(in_packet);
        //https://www.cnblogs.com/wangguchangqing/p/6138683.html
    }
 
    av_write_trailer(pOutFmtContext); //��δ�����AVPacket���ļ�β
    av_packet_free(&in_packet);
 
    avformat_close_input(&pInFmtContext);
    avio_close( pOutFmtContext->pb);
    avformat_free_context(pOutFmtContext);
	return 0;
}
