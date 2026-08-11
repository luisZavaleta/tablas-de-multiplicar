const BASE_URL = '/api'

async function request(path, options) {
  const response = await fetch(`${BASE_URL}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  })
  if (!response.ok) {
    const body = await response.json().catch(() => ({}))
    throw new Error(body.error || `Error ${response.status}`)
  }
  return response.json()
}

export function createPlayer(name) {
  return request('/player', { method: 'POST', body: JSON.stringify({ name }) })
}

export function fetchQuestion(playerId, tables, mode) {
  return request(`/quiz/question?playerId=${playerId}&tables=${tables.join(',')}&mode=${mode}`)
}

export function submitAnswer(playerId, questionId, answer) {
  return request('/quiz/answer', {
    method: 'POST',
    body: JSON.stringify({ playerId, questionId, answer }),
  })
}

export function fetchStats(playerId) {
  return request(`/quiz/stats/${playerId}`)
}

export function fetchStreak(playerId, table, mode) {
  return request(`/quiz/streak?playerId=${playerId}&table=${table}&mode=${mode}`)
}
