const $=selector=>document.querySelector(selector);
let source,timer,startedAt,eventCount=0;
const scenarios={
  cpu_spike:{title:'Checkout CPU saturation',service:'checkout-service',description:'批处理窗口后 CPU 升至 92%，P99 延迟持续恶化'},
  connection_pool:{title:'Database connection timeout',service:'payment-service',description:'连接池活跃连接触顶，请求获取数据库连接超时'},
  bad_release:{title:'Order API error spike',service:'order-service',description:'2.4.1 版本发布后接口错误率出现明显突增'}
};
const stages=['created','planning','rag','tools','report'];

setInterval(()=>$('#clock').textContent=new Date().toLocaleTimeString('zh-CN',{hour12:false}),1000);
$('#scenario').addEventListener('change',renderIncident);
$('#start').addEventListener('click',startDiagnosis);
renderIncident();

function renderIncident(){
  const item=scenarios[$('#scenario').value];
  $('#incident-card').innerHTML=`<strong>${item.title}</strong><small>${item.service}<br>${item.description}</small>`;
}

async function startDiagnosis(){
  resetDashboard();
  try{
    const scenario=$('#scenario').value,item=scenarios[scenario];
    const response=await fetch('/api/diagnoses',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({title:item.title,service:item.service,severity:'HIGH',description:item.description,labels:{scenario,mode:$('#mode').value}})});
    if(!response.ok){const error=await response.json().catch(()=>({}));throw new Error(error.detail||error.message||`HTTP ${response.status}`);}
    const task=await response.json();
    $('#task-id').textContent=task.taskId;$('#graph-service').textContent=task.alert.service;
    activateStage('created');setNode('alert','active');setStatus('INGESTING');connect(task.taskId);
  }catch(error){setStatus('FAILED');$('#conclusion').textContent=`任务创建失败：${error.message}`;finish(false)}
}

function resetDashboard(){
  if(source)source.close();eventCount=0;startedAt=Date.now();clearInterval(timer);
  timer=setInterval(()=>$('#elapsed').innerHTML=`${Math.max(1,Math.round((Date.now()-startedAt)/1000))}<small>s</small>`,1000);
  $('#start').disabled=true;$('#topology').classList.add('running');$('#agent-state').textContent='ACTIVATING';
  $('#event-cards').innerHTML='';$('#event-count').textContent='0';$('#report').classList.add('hidden');$('#confidence').textContent='--';
  $('#conclusion-box').classList.remove('has-result');$('#conclusion').dataset.started='';$('#conclusion').textContent='Agent 正在建立诊断上下文…';
  document.querySelectorAll('[data-graph]').forEach(n=>n.classList.remove('active','done'));
  document.querySelectorAll('[data-stage]').forEach((n,i)=>{n.className='';n.querySelector('i').textContent=String(i+1).padStart(2,'0')});
  document.querySelectorAll('[data-evidence]').forEach(n=>n.classList.remove('active'));
}

function connect(taskId){
  source=new EventSource(`/api/diagnoses/${taskId}/events`);
  ['task_created','state_changed','plan_created','rag_retrieval','tool_started','tool_completed','tool_failed','model_token','agent_decision','diagnosis_ready','report_ready','task_completed','task_review_required','task_failed'].forEach(name=>source.addEventListener(name,e=>handle(name,JSON.parse(e.data))));
}

function handle(name,event){
  eventCount++;$('#event-count').textContent=eventCount;pushEvent(name,event.payload);
  if(name==='plan_created'){activateStage('planning');setNode('alert','done');setNode('agent','active');setStatus('PLANNING');$('#agent-state').textContent='PLANNING'}
  if(name==='rag_retrieval'){activateStage('rag');setNode('rag','active');setNode('memory','active');setEvidence('rag');setStatus('RETRIEVING')}
  if(name==='tool_started'){activateStage('tools');const node=skillNode(event.payload);setNode(node,'active');setStatus('EXECUTING');$('#agent-state').textContent='EXECUTING'}
  if(name==='tool_completed'){const node=skillNode(event.payload);setNode(node,'done');setEvidence(node==='releases'?'logs':node)}
  if(name==='tool_failed'){setStatus('EVIDENCE GAP');$('#confidence').textContent='--'}
  if(name==='model_token'){const node=$('#conclusion');node.textContent=(node.dataset.started?node.textContent:'')+event.payload.token;node.dataset.started='1';$('#conclusion-box').classList.add('has-result');$('#confidence').textContent='--'}
  if(name==='agent_decision'){setStatus('DECIDING');$('#agent-state').textContent='DECIDING'}
  if(name==='diagnosis_ready'){$('#conclusion').textContent=event.payload.conclusion;$('#conclusion-box').classList.add('has-result')}
  if(name==='report_ready'){activateStage('report');setNode('agent','done');setNode('report','active');$('#report').href=event.payload.downloadUrl;$('#report').classList.remove('hidden');setStatus('REPORTING')}
  if(name==='task_completed'){setNode('report','done');setStatus('COMPLETED');finish(true)}
  if(name==='task_review_required'){setNode('report','done');setStatus('HUMAN REVIEW');$('#confidence').textContent='--';$('#conclusion').textContent=event.payload.conclusion;finish(false)}
  if(name==='task_failed'){setStatus('FAILED');finish(false)}
}

function skillNode(payload){
  const value=String(payload.skill||payload.skillName||'').toLowerCase();
  if(value.includes('metric'))return'metrics';if(value.includes('release'))return'releases';return'logs';
}

function activateStage(stage){
  const index=stages.indexOf(stage);
  stages.forEach((name,i)=>{const node=document.querySelector(`[data-stage="${name}"]`);node.className=i<index?'done':i===index?'active':'';if(i<index)node.querySelector('i').textContent='✓'});
}
function setNode(name,state){const node=document.querySelector(`[data-graph="${name}"]`);if(node){node.classList.remove('active','done');node.classList.add(state)}}
function setEvidence(name){const node=document.querySelector(`[data-evidence="${name}"]`);if(node)node.classList.add('active')}
function setStatus(value){const node=$('#status');node.textContent=value;node.className=value==='COMPLETED'?'done':value==='STANDBY'?'':'running'}

function pushEvent(name,payload){
  if(name==='model_token'||name==='state_changed')return;
  const labels={agent_decision:'模型下一步决策',diagnosis_ready:'诊断结果已生成',task_created:'告警已接入',plan_created:'计划已生成',rag_retrieval:'知识已命中',tool_started:'Skill 调用',tool_completed:'证据已返回',tool_failed:'取证失败',report_ready:'报告已生成',task_completed:'诊断完成',task_review_required:'需要人工复核',task_failed:'任务失败'};
  const card=document.createElement('span');card.className=`event-card ${name.includes('completed')||name==='report_ready'?'done':''}`;
  const detail=payload.skill||(payload.action&&(payload.action.skillName||payload.action.type));
  card.textContent=`${labels[name]||name}${detail?` · ${detail}`:''}`;
  $('#event-cards').prepend(card);while($('#event-cards').children.length>4)$('#event-cards').lastElementChild.remove();
}

function finish(success){
  clearInterval(timer);$('#start').disabled=false;$('#topology').classList.remove('running');$('#agent-state').textContent=success?'COMPLETE':'STOPPED';if(source)source.close();
  if(success){activateStage('report');const last=document.querySelector('[data-stage="report"]');last.className='done';last.querySelector('i').textContent='✓';document.querySelector('.confidence-ring').style.background='#1d2b31'}
}
