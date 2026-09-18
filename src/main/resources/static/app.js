const $ = selector => document.querySelector(selector);
let eventSource, timer, startedAt, eventCount = 0;
const scenarios = {
  cpu_spike:{title:'Checkout CPU saturation',service:'checkout-service',description:'CPU 与接口延迟在批处理后持续升高',initials:'CS'},
  connection_pool:{title:'Database connection timeout',service:'payment-service',description:'请求获取数据库连接时持续超时',initials:'PS'},
  bad_release:{title:'Order API error spike',service:'order-service',description:'新版本发布后接口错误率快速上升',initials:'OS'}
};

$('#scenario').addEventListener('change', renderScenario);
$('#start').addEventListener('click', startDiagnosis);
renderScenario();

function renderScenario(){
  const item=scenarios[$('#scenario').value];
  $('#scenario-detail').innerHTML=`<span class="service-icon">${item.initials}</span><div><strong>${item.service}</strong><small>生产环境 · HIGH</small></div><p>${item.description}</p>`;
}

async function startDiagnosis(){
  try{
    resetView();
    const scenario=$('#scenario').value,item=scenarios[scenario];
    const response=await fetch('/api/diagnoses',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({title:item.title,service:item.service,severity:'HIGH',description:item.description,labels:{scenario}})});
    if(!response.ok) throw new Error(`HTTP ${response.status}`);
    const task=await response.json();
    $('#task-meta').innerHTML=`<div><dt>任务编号</dt><dd title="${task.taskId}">${task.taskId.slice(0,8)}…</dd></div><div><dt>目标服务</dt><dd>${task.alert.service}</dd></div><div><dt>告警等级</dt><dd>${task.alert.severity}</dd></div>`;
    setStatus('CREATED');markStep('created');connect(task.taskId);
  }catch(error){setStatus('FAILED');$('#conclusion').textContent=`任务创建失败：${error.message}`;$('#start').disabled=false;clearInterval(timer)}
}

function resetView(){
  if(eventSource)eventSource.close();eventCount=0;startedAt=Date.now();clearInterval(timer);
  timer=setInterval(()=>$('#elapsed').textContent=`${Math.max(1,Math.round((Date.now()-startedAt)/1000))}s`,1000);
  $('#timeline').replaceChildren();$('#event-count').textContent='0 EVENTS';$('#conclusion').textContent='Agent 正在理解告警并生成诊断计划…';
  $('#conclusion-wrap').classList.remove('has-result');$('#conclusion').dataset.started='';$('#report').classList.add('hidden');$('#start').disabled=true;
  document.querySelectorAll('.pipeline div').forEach((step,index)=>{step.className='';step.querySelector('i').textContent=index+1});
}

function connect(taskId){
  eventSource=new EventSource(`/api/diagnoses/${taskId}/events`);
  ['task_created','state_changed','plan_created','rag_retrieval','tool_started','tool_completed','model_token','report_ready','task_completed','task_failed'].forEach(name=>eventSource.addEventListener(name,event=>handle(name,JSON.parse(event.data))));
  eventSource.onerror=()=>{if($('#start').disabled)$('#health').innerHTML='<i></i>RECONNECTING'};
}

function handle(name,event){
  eventCount++;$('#event-count').textContent=`${eventCount} EVENTS`;updatePipeline(name);if(name!=='model_token')appendEvent(name,event);
  if(name==='state_changed')setStatus(event.payload.to);
  if(name==='model_token'){const node=$('#conclusion');node.textContent=(node.dataset.started?node.textContent:'')+event.payload.token;node.dataset.started='1';$('#conclusion-wrap').classList.add('has-result')}
  if(name==='report_ready'){$('#report').href=event.payload.downloadUrl;$('#report').classList.remove('hidden')}
  if(name==='task_completed'||name==='task_failed')finish(name==='task_completed');
}

function appendEvent(name,event){
  const labels={task_created:'任务创建',state_changed:'状态流转',plan_created:'生成计划',rag_retrieval:'知识检索',tool_started:'调用工具',tool_completed:'工具完成',report_ready:'报告就绪',task_completed:'诊断完成',task_failed:'执行失败'};
  const item=document.createElement('li');item.innerHTML=`<span class="event-name">${String(event.eventId).padStart(2,'0')} · ${labels[name]||name}</span><span class="event-data">${escapeHtml(summarize(event.payload))}</span>`;
  $('#timeline').append(item);$('#timeline').scrollTop=$('#timeline').scrollHeight;
}

function summarize(payload){
  if(payload.skill)return `${payload.skill}${payload.durationMs?` · ${payload.durationMs}ms`:''}`;
  if(payload.from&&payload.to)return `${payload.from} → ${payload.to}`;
  if(payload.downloadUrl)return 'PDF 诊断报告已生成';
  if(payload.query)return `命中运行手册：${payload.query}`;
  return JSON.stringify(payload);
}

function setStatus(value){const node=$('#status');node.textContent=value;node.className=`status-pill ${value==='COMPLETED'?'done':value==='IDLE'?'idle':'running'}`}
function updatePipeline(name){if(name==='plan_created'||name==='rag_retrieval')markStep('planning');if(name==='tool_started'||name==='tool_completed')markStep('tools');if(name==='report_ready'||name==='task_completed')markStep('report')}
function markStep(current){const order=['created','planning','tools','report'],index=order.indexOf(current);order.forEach((name,i)=>{const node=document.querySelector(`[data-step="${name}"]`);node.className=i<index?'done':i===index?'active':'';if(i<index)node.querySelector('i').textContent='✓'})}
function finish(success){clearInterval(timer);$('#start').disabled=false;$('#health').innerHTML='<i></i>AGENT READY';if(eventSource)eventSource.close();if(success){setStatus('COMPLETED');const report=document.querySelector('[data-step="report"]');report.className='done';report.querySelector('i').textContent='✓'}}
function escapeHtml(value){const node=document.createElement('span');node.textContent=value;return node.innerHTML}
