import pathlib,subprocess,json,time,datetime,urllib.request,threading,os,statistics
ROOT=pathlib.Path(__file__).resolve().parent
COMPOSE=['docker','compose','-f',str(ROOT/'compose.json')]
def now():return datetime.datetime.now(datetime.timezone.utc).isoformat()
def dc(*args,**kw):return subprocess.run(COMPOSE+list(args),check=True,**kw)
def sql(query):
 return dc('exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_PASSWORD" mysql -utradeuser -Dtrade -N -B',input=query,text=True,capture_output=True).stdout
def get(path):
 with urllib.request.urlopen('http://127.0.0.1:18086'+path,timeout=60) as r:return r.read()
def ready():
 for _ in range(60):
  try:
   if json.loads(get('/actuator/health')).get('status')=='UP':return
  except Exception:pass
  time.sleep(2)
 raise RuntimeError('Local backend not healthy')
def es(query):return dc('exec','-T','elasticsearch','curl','-fsS','http://localhost:9200/'+query,text=True,capture_output=True).stdout
def collect(out,stop):
 with (out/'resource-samples.jsonl').open('w') as f:
  while not stop.is_set():
   p=subprocess.run(['docker','stats','--no-stream','--format','{{json .}}'],text=True,capture_output=True)
   samples=[]
   for line in p.stdout.splitlines():
    try:x=json.loads(line)
    except ValueError:continue
    if x.get('Name','').startswith('cui-5006-volume-1029-'):samples.append(x)
   f.write(json.dumps({'at':now(),'containers':samples})+'\n');f.flush()
   stop.wait(4)
def seed(n):
 # Refuse a differently labelled database or one containing orders.
 ident=dc('ps','-q','mysql',text=True,capture_output=True).stdout.strip()
 label=subprocess.check_output(['docker','inspect','--format','{{index .Config.Labels "com.docker.compose.project"}}',ident],text=True).strip()
 assert label=='cui-5006-volume-1029',label
 assert sql('SELECT COUNT(*) FROM oms_order;').strip()=='0','Nonempty orders: refusing fixture reset'
 users=[]
 for i in range(20):users.append(f"({900000+i},'volume_seller_{i}','Volume seller {i}','!local-volume-disabled!','volume_{i}@invalid.local')")
 q="DELETE FROM pms_product_media; DELETE FROM pms_product; INSERT IGNORE INTO users(id,username,display_name,password_hash,email) VALUES "+','.join(users)+';\n'
 for start in range(0,n,500):
  products=[];media=[]
  for i in range(start,min(start+500,n)):
   ident=900001+i; name=f'VOLCAT item {i:05d}'+(' VOLUME_FIXED' if i<10 else '')
   products.append(f"({ident},{900000+i%20},'{name}','Deterministic local volume fixture; no external transaction data.',1,10,9,'VolumeLab','books',1,'2026-10-01 00:00:00','2026-10-01 00:00:00')")
   for order in range(2):media.append(f"({ident},'http://127.0.0.1:19000/fixture/{ident}-{order}.png',1,{order})")
  q+='INSERT INTO pms_product(id,user_id,name,description,price,stock,condition_level,location,category,status,created_at,updated_at) VALUES '+','.join(products)+';\n'
  q+='INSERT INTO pms_product_media(product_id,url,media_type,sort_order) VALUES '+','.join(media)+';\n'
 return q
def main():
 assert os.uname().machine=='arm64','Record changed architecture before running'
 ready()
 for n in (100,1000,10000):
  out=ROOT/'runs'/str(n);out.mkdir(parents=True,exist_ok=False)
  (out/'start.txt').write_text(now());q=seed(n);(out/'seed.sql').write_text(q)
  began=time.monotonic();sql(q);(out/'seed-seconds.txt').write_text(str(time.monotonic()-began))
  (out/'database-counts.txt').write_text(sql('SELECT COUNT(*) FROM pms_product; SELECT COUNT(*) FROM pms_product_media; SELECT COUNT(DISTINCT user_id) FROM pms_product;'))
  assert sql('SELECT COUNT(*) FROM pms_product;').strip()==str(n)
  dc('restart','backend',stdout=subprocess.DEVNULL);ready()
  # ApplicationReadyEvent rebuilds the index on this isolated backend restart.
  for _ in range(60):
   indexes=json.loads(es('_cat/indices?format=json'));matching=[x for x in indexes if x.get('index')=='products']
   if matching and int(matching[0].get('docs.count',0))==n:break
   time.sleep(2)
  else:raise RuntimeError('Index count did not reach dataset size')
  (out/'index-counts.json').write_text(json.dumps(indexes,indent=2))
  (out/'version-before.json').write_bytes(get('/actuator/info'))
  checks=[]
  for op,path,count in [('list','/api/products',n),('selective','/api/products?keyword=VOLUME_FIXED',10),('broad','/api/products?keyword=VOLCAT',n),('empty','/api/products?keyword=VOLUME_NORESULTS',0)]:
   raw=get(path);items=json.loads(raw);ids=[x['id'] for x in items]
   expected=set(range(900001,900001+(10 if op=='selective' else n))) if count else set()
   row={'operation':op,'count':len(items),'bytes':len(raw),'unique':len(set(ids))==len(ids),'exact_id_membership':set(ids)==expected,'media_two_each':all(len(x.get('media') or [])==2 for x in items)}
   checks.append(row);assert len(items)==count and row['unique'] and row['exact_id_membership'] and row['media_two_each'],row
  (out/'correctness.json').write_text(json.dumps(checks,indent=2))
  # Same warmup interval and workload at every size.
  for _ in range(3):
   get('/api/products?keyword=VOLUME_FIXED');get('/api/products?keyword=VOLCAT');time.sleep(1)
  (out/'mysql-before.txt').write_text(sql("SHOW GLOBAL STATUS WHERE Variable_name IN ('Questions','Threads_connected','Slow_queries','Bytes_sent');"))
  stop=threading.Event();collector=threading.Thread(target=collect,args=(out,stop));collector.start()
  (out/'load-start.txt').write_text(now())
  try:
   with (out/'console.log').open('w') as f:
    p=subprocess.run(['docker','run','--rm','--name','cui-5006-volume-k6','--network','cui-5006-volume-1029_default','--user',str(os.getuid())+':'+str(os.getgid()),'-v',str(out)+':/results','-v',str(ROOT/'volume.js')+':/scripts/volume.js:ro','grafana/k6:2.3.0','run','-e','DATASET_SIZE='+str(n),'--out','json=/results/timeseries.json','/scripts/volume.js'],stdout=f,stderr=subprocess.STDOUT)
  finally:stop.set();collector.join()
  (out/'exit-code.txt').write_text(str(p.returncode));(out/'load-end.txt').write_text(now())
  (out/'mysql-after.txt').write_text(sql("SHOW GLOBAL STATUS WHERE Variable_name IN ('Questions','Threads_connected','Slow_queries','Bytes_sent');"))
  (out/'version-after.json').write_bytes(get('/actuator/info'))
  assert (out/'version-before.json').read_bytes()==(out/'version-after.json').read_bytes()
  d=json.loads((out/'summary.json').read_text());print(n,'products','exit',p.returncode,'error rate',d['metrics']['business_errors']['values']['rate'],flush=True)
  dc('logs','--no-color','backend',stdout=(out/'backend.log').open('w'))
  (out/'end.txt').write_text(now());time.sleep(5)
if __name__=='__main__':main()
