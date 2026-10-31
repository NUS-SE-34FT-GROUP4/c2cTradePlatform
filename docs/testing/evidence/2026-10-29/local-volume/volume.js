import http from 'k6/http';
import {check,sleep} from 'k6';
import {Rate,Trend} from 'k6/metrics';
const n=Number(__ENV.DATASET_SIZE);
if(![100,1000,10000].includes(n))throw new Error('Unsupported dataset');
const fail=new Rate('business_errors');
const duration=new Trend('read_ms',true);
const chars=new Trend('response_chars');
const operations=['list','selective','broad','empty'];
const urls={list:'/api/products',selective:'/api/products?keyword=VOLUME_FIXED',broad:'/api/products?keyword=VOLCAT',empty:'/api/products?keyword=VOLUME_NORESULTS'};
export const options={scenarios:{reads:{executor:'constant-vus',vus:2,duration:'45s',gracefulStop:'15s'}},thresholds:{business_errors:['rate<0.01'],...Object.fromEntries(operations.map(o=>['read_ms{operation:'+o+'}',['med<1000','p(95)<2000']]))},summaryTrendStats:['min','med','p(95)','p(99)','max']};
export default function(){
 const op=operations[(__ITER+__VU)%4];
 const r=http.get('http://backend:8080'+urls[op],{timeout:'15s',tags:{operation:op,name:op,dataset:String(n)}});
 let b;try{b=r.json()}catch(e){b=null}
 const count=op==='selective'?10:op==='empty'?0:n;
 const ok=r.status===200&&Array.isArray(b)&&b.length===count;
 check(r,{'HTTP 200 and expected result count':()=>ok},{operation:op});fail.add(!ok,{operation:op});duration.add(r.timings.duration,{operation:op});chars.add(r.body?r.body.length:0,{operation:op});
 if(!ok)console.warn(JSON.stringify({operation:op,status:r.status,items:Array.isArray(b)?b.length:null,error:r.error||null}));
 sleep(1);
}
export function handleSummary(data){return {'/results/summary.json':JSON.stringify({metadata:{dataset:n,vus:2,duration:'45s',thinkTimeSeconds:1,environment:'isolated local Docker ARM64',operations},...data},null,2),stdout:JSON.stringify(data.metrics,null,2)+'\n'};}
