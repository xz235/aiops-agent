const $ = (selector) => document.querySelector(selector);
let eventSource;
let eventCount = 0;

const scenarios = {
  cpu_spike: { title: 'Checkout CPU saturation', service: 'checkout-service', description: 'CPU and latency increased after batch processing' },
  connection_pool: { title: 'Database connection timeout', service: 'payment-service', description: 'Requests time out while acquiring database connections' },
  bad_release: { title: 'Order API error spike', service: 'order-service', description: 'Error rate increased immediately after deployment' }
};

$('#start').addEventListener('click', async () => {
  if (eventSource) eventSource.close();
  eventCount = 0;
  $('#timeline').replaceChildren();
  $('#conclusion').textContent = 'Agent 正在收集证据...';
  $('#report').classList.add('hidden');
  $('#start').disabled = true;
  const scenario = $('#scenario').value;
  const body = { ...scenarios[scenario], severity: 'HIGH', labels: { scenario } };
  const response = await fetch('/api/diagnoses', { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(body) });
  const task = await response.json();
  $('#task-meta').innerHTML = `<dt>Task ID</dt><dd>${task.taskId}</dd><dt>Service</dt><dd>${task.alert.service}</dd><dt>Severity</dt><dd>${task.alert.severity}</dd>`;
  connect(task.taskId);
});

function connect(taskId) {
  eventSource = new EventSource(`/api/diagnoses/${taskId}/events`);
  ['task_created','state_changed','plan_created','rag_retrieval','tool_started','tool_completed','model_token','report_ready','task_completed','task_failed'].forEach(name => {
    eventSource.addEventListener(name, event => handle(name, JSON.parse(event.data)));
  });
}

function handle(name, event) {
  eventCount += 1;
  $('#event-count').textContent = `${eventCount} EVENTS`;
  if (name !== 'model_token') {
    const item = document.createElement('li');
    item.innerHTML = `<span class="event-name">${event.eventId} · ${name.toUpperCase()}</span><span class="event-data">${escapeHtml(JSON.stringify(event.payload))}</span>`;
    $('#timeline').append(item);
  }
  if (name === 'state_changed') $('#status').textContent = event.payload.to;
  if (name === 'model_token') $('#conclusion').textContent = ($('#conclusion').dataset.started ? $('#conclusion').textContent : '') + event.payload.token, $('#conclusion').dataset.started='1';
  if (name === 'report_ready') { $('#report').href=event.payload.downloadUrl; $('#report').classList.remove('hidden'); }
  if (name === 'task_completed' || name === 'task_failed') { $('#start').disabled=false; eventSource.close(); }
}

function escapeHtml(value) { const node=document.createElement('span'); node.textContent=value; return node.innerHTML; }

