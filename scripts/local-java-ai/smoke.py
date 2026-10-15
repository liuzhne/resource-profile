"""Real Java containers + Redis + Milvus; synthetic local embedding fixture."""
import json, urllib.request, urllib.error, time, sys
results=[]
def call(path,body=None,token='internal',port=18090,headers=None):
    h={'Content-Type':'application/json','Accept':'application/json, text/event-stream'}
    if token: h['X-'+token.title()+'-Token']='synthetic-'+token
    h.update(headers or {})
    req=urllib.request.Request(f'http://127.0.0.1:{port}'+path,data=None if body is None else json.dumps(body).encode(),headers=h)
    try:
        with urllib.request.urlopen(req,timeout=60) as r: return r.status,r.read().decode(),dict(r.headers)
    except urllib.error.HTTPError as e: return e.code,e.read().decode(),dict(e.headers)
def check(name,condition,detail=None):
    results.append({'name':name,'passed':bool(condition)})
    print(name, 'PASS' if condition else 'FAIL', flush=True)
    if not condition: raise AssertionError(str(detail))
def api(path,body,token='internal',port=18090):
    status,text,_=call(path,body,token,port); check(path+' HTTP200',status==200,(status,text)); return json.loads(text)
def retrieve(q='local test',sources=['case']): return api('/api/v1/rag/retrieve',{'queries':[q],'sources':sources,'top_k':20})
def upsert(doc,text,source='case',strategy=None,title='synthetic'):
    return api('/api/v1/rag/upsert',{'collection':source,'doc_id':doc,'text':text,'metadata':{'title':title,'source_url':'https://example.org/local-test'},'chunk_strategy':strategy},'admin')
def rpc_json(text):
    if any(line.startswith('data:') for line in text.splitlines()):
        return json.loads(next(line[5:].strip() for line in text.splitlines() if line.startswith('data:')))
    return json.loads(text)
try:
    for i in range(90):
        try:
            if call('/health',token=None)[0]==200 and call('/health',token=None,port=18095)[0]==200: break
        except OSError: pass
        time.sleep(1)
    check('Java API health',json.loads(call('/health',token=None)[1])['runtime']=='java')
    check('anonymous retrieval rejected',call('/api/v1/rag/retrieve',{'queries':['test']},None)[0]==401)
    check('internal credential cannot import',call('/api/v1/rag/upsert',{},'internal')[0]==401)
    api('/api/v1/rag/initialize',{},'admin'); api('/api/v1/rag/initialize',{},'admin')
    if '--persist' in sys.argv:
        hits=retrieve('after restart '+str(time.time_ns()))['chunks']; check('persistent imported data',any(x['doc_id']=='local_case' and x['content']=='updated synthetic text' for x in hits))
    else:
        check('empty collection fallback',retrieve(sources=['success'])['fallback'])
        for source in ['case','policy','psychology']:
            r=upsert('local_'+source,'synthetic '+source,source); check(source+' inserted',r['chunks_written']==1,r)
        r=upsert('local_case','synthetic case'); check('idempotent duplicate skipped',r['skipped'],r)
        hits=retrieve()['chunks']; check('retrieval provenance',len(hits)==1 and hits[0]['source_url']=='https://example.org/local-test' and hits[0]['doc_id']=='local_case',hits)
        upsert('local_case_child','child document')
        r=upsert('local_case','updated synthetic text'); check('old row deleted',r['chunks_deleted_first']==1,r)
        hits=retrieve()['chunks']; check('replacement visible and child retained',len(hits)==2 and any(x['content']=='updated synthetic text' for x in hits) and any(x['doc_id']=='local_case_child' for x in hits),hits)
        status,_,_=call('/api/v1/rag/upsert',{'collection':'case','doc_id':'local_case','text':'__FAIL_EMBEDDING__'},'admin')
        check('embedding failure rejected503',status==503)
        hits=retrieve('after failed replacement')['chunks']
        check('failed embedding preserves old document',any(x['content']=='updated synthetic text' for x in hits))
        r=upsert('fixed','abcdefghij'*5,'policy',{'name':'fixed_size','window':20,'overlap':5}); check('fixed chunks inserted',r['chunks_written']>1,r)
        r=upsert('sentence','第一句话。第二句话。第三句话。','psychology',{'name':'sentence','max_chars':8}); check('sentence chunks inserted',r['chunks_written']>1,r)
        check('invalid collection422',call('/api/v1/rag/retrieve',{'queries':['x'],'sources':['invalid']})[0]==422)
        check('MCP-only API boundary404',call('/api/v1/rag/upsert',{},'admin',18095)[0]==404)
        check('MCP anonymous rejected',call('/mcp',{},None,18095)[0]==401)
    status,text,h=call('/mcp',{'jsonrpc':'2.0','id':1,'method':'initialize','params':{'protocolVersion':'2025-03-26','capabilities':{},'clientInfo':{'name':'local-test','version':'1'}}},'mcp',18095)
    check('MCP initialize',status==200 and 'result' in rpc_json(text),(status,text))
    session={k:v for k,v in h.items() if k.lower()=='mcp-session-id'}; session['MCP-Protocol-Version']='2025-03-26'
    call('/mcp',{'jsonrpc':'2.0','method':'notifications/initialized'},'mcp',18095,session)
    status,text,_=call('/mcp',{'jsonrpc':'2.0','id':2,'method':'tools/list'},'mcp',18095,session)
    check('MCP three tools',status==200 and {x['name'] for x in rpc_json(text)['result']['tools']}=={'search_cases','search_policies','search_psychology'},text)
    for i,name in enumerate(['search_cases','search_policies','search_psychology'],3):
        status,text,_=call('/mcp',{'jsonrpc':'2.0','id':i,'method':'tools/call','params':{'name':name,'arguments':{'query':'synthetic '+str(time.time_ns()),'top_k':3}}},'mcp',18095,session)
        result=rpc_json(text).get('result',{})
        check('MCP '+name,status==200 and not result.get('isError') and 'source_db' in json.dumps(result),text)
finally:
    if '--output' in sys.argv:
        with open(sys.argv[sys.argv.index('--output')+1],'w') as f: json.dump(results,f,indent=2)
