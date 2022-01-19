import socket
import collections
import threading


# 实现下面这两个函数！然后调用startServer即可

'''
为用户usr提供服务，将uri流转码为usr可以接受的码率之一，返回转码后的流的地址uri
'''
def serve_stream(uri, encodings) -> str:
    # open stream from 'uri'
    # transcode stream to proper code (one of the given 'encodings' list)
    # return the transcoded stream uri
    return "rtmp://10.42.0.1/live/livestream/1.flv"

'''
停止某个流的转码服务！
'''
def stop_stream(transcoded_uri):
    # stop transcoding stream 'uri'
    pass

class Server(threading.Thread):
    conn : socket 
    addr : tuple
    supported : list
    def __init__(self, conn, addr):
        super().__init__()
        self.conn = conn
        self.addr = addr
        self.start()
    def run(self):
        b = conn.recv(4096)
        codes = b.split(b'\n')
        n = int(codes[0].decode('utf-8'))
        codes = [x.decode('utf-8') for x in codes[1:n+1]]
        codes = list(collections.Counter(codes).keys())
        codes = list(filter(lambda v : v.startswith('video/'), codes))
        print(addr, " supported ", codes)
        self.supported = codes
        # connected, reply
        conn.send(b"0\n")
        serving = None
        # waiting for service!
        while True:
            b = conn.recv(4096)
            uri = b.decode('utf-8')
            print(self.addr, ' calls for service ', uri)
            transcoded = serve_stream(addr, uri)
            if serving != None:
                stop_stream(serving)
            serving = serve_stream(uri, self.supported) + '\n'
            print("reply ", self.addr, " with ", serving)
            conn.send(serving.encode('utf-8'))


def startServer():
    with socket.create_server(('', 6978)) as server:
        while True:
            conn, addr = server.accept()
            print(addr)
            threading.Thread()
            Server(conn, addr)
