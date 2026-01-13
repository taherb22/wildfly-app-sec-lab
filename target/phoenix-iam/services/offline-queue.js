const OFFLINE_QUEUE_KEY = 'offlineQueue';

export function enqueueOfflineRequest(req) {
  const queue = JSON.parse(localStorage.getItem(OFFLINE_QUEUE_KEY) || '[]');
  queue.push(req);
  localStorage.setItem(OFFLINE_QUEUE_KEY, JSON.stringify(queue));
}

export async function processQueue() {
  const queue = JSON.parse(localStorage.getItem(OFFLINE_QUEUE_KEY) || '[]');
  const successRequests = [];

  for (let req of queue) {
    try {
      const res = await fetch(req.url, req.options);
      if (res.ok) successRequests.push(req);
    } catch(e) { console.warn('Offline request failed', e); }
  }

  const remaining = queue.filter(r => !successRequests.includes(r));
  localStorage.setItem(OFFLINE_QUEUE_KEY, JSON.stringify(remaining));
  return remaining.length === 0;
}
