import http from 'k6/http';
import {sleep,check} from 'k6';
import exec from 'k6/execution';
import {Rate,Trend} from 'k6/metrics';
const base=__ENV.BASE_URL;
if(!base || !/^https?:\/\//.test(base)) throw new Error('BASE_URL required');
const errors=new Rate('business_errors');
const latency=new Trend('read_ms',true);
export const options={
 scenarios:{
  baseline:{executor:'constant-vus',vus:10,duration:'30s',gracefulStop:'10s'},
  pressure:{executor:'ramping-vus',startTime:'40s',startVUs:10,stages:[{duration:'90s',target:175},{duration:'120s',target:175},{duration:'30s',target:10}],gracefulRampDown:'10s',gracefulStop:'10s'},
  recovery:{executor:'constant-vus',startTime:'4m50s',vus:10,duration:'90s',gracefulStop:'10s'}
 },
 thresholds:{
  business_errors:[{threshold:'rate<0.05',abortOnFail:true,delayAbortEval:'20s'}],
  'business_errors{phase:baseline}':['rate<0.01'],
  'business_errors{phase:pressure}':['rate<0.01'],
  'business_errors{phase:recovery}':['rate<0.01'],
  'read_ms{phase:baseline}':['med<1000','p(95)<2000'],
  'read_ms{phase:recovery}':['med<1000','p(95)<2000']
 },summaryTrendStats:['min','med','p(95)','p(99)','max']
};
export function setup(){
 for(const path of ['/api/products','/api/products?keyword=iPhone']){
  const r=http.get(base+path,{timeout:'10s',tags:{phase:'preflight'}});
  let b;try{b=r.json()}catch(e){b=null}
  if(r.status!==200 || !Array.isArray(b))throw new Error('Preflight rejected: '+r.status);
  console.log(JSON.stringify({event:'preflight',path,status:r.status,items:b.length}));
 }
 console.log('Read-only gradual 10 to 175 to 10 VU recovery experiment; one-second think time; not a maximum-capacity claim.');
}
export default function(){
 const operation=(__ITER+__VU)%2===0?'list':'search';
 const phase=exec.scenario.name;
 const r=http.get(base+(operation==='list'?'/api/products':'/api/products?keyword=iPhone'),{timeout:'10s',redirects:0,tags:{phase,operation,name:operation}});
 let b;try{b=r.json()}catch(e){b=null}
 const ok=r.status===200&&Array.isArray(b);
 check(r,{'HTTP 200 and JSON array':()=>ok},{phase,operation});
 errors.add(!ok,{phase,operation});latency.add(r.timings.duration,{phase,operation});
 if(!ok&&__ITER<3)console.warn(JSON.stringify({event:'failure',phase,operation,status:r.status,error:r.error||null,at:new Date().toISOString()}));
 sleep(1);
}
export function handleSummary(data){return {'/results/summary.json':JSON.stringify({metadata:{base,maximumVUs:175,thinkTimeSeconds:1,keyword:'iPhone',purpose:'gradual load and recovery'},...data},null,2),stdout:JSON.stringify({metrics:data.metrics},null,2)+'\n'};}
