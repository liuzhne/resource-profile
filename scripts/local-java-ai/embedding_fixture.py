"""Local protocol fixture only; deliberately not a semantic embedding model."""
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        if self.headers.get('Transfer-Encoding','').lower()=='chunked':
            raw=b''
            while True:
                size=int(self.rfile.readline().strip().split(b';')[0],16)
                if not size:
                    self.rfile.readline(); break
                raw+=self.rfile.read(size); self.rfile.read(2)
        else: raw=self.rfile.read(int(self.headers['Content-Length']))
        body=json.loads(raw)
        inputs=body['input']; inputs=[inputs] if isinstance(inputs,str) else inputs
        if any('__FAIL_EMBEDDING__' in str(value) for value in inputs):
            self.send_response(503); self.end_headers(); return
        data=[{'object':'embedding','index':i,'embedding':[1.0]+[0.0]*1023} for i,_ in enumerate(inputs)]
        response=json.dumps({'object':'list','data':data,'model':'local-fixture','usage':{'prompt_tokens':1,'total_tokens':1}}).encode()
        self.send_response(200); self.send_header('Content-Type','application/json'); self.end_headers(); self.wfile.write(response)
    def log_message(self,*args): pass

if __name__=='__main__': ThreadingHTTPServer(('0.0.0.0',18092),Handler).serve_forever()
