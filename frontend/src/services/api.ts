import type { Leave, NewLeave } from '../types/leave'

export class ApiError extends Error {
  status: number

  constructor(message: string, status: number) {
    super(message)
    this.status = status
  }
}

// Spring Boot returns errors as JSON ({ message, error, ... }); show the most useful part.
async function readError(response: Response): Promise<string> {
  const text = await response.text()
  try {
    const body = JSON.parse(text)
    return body.message || body.error || text || response.statusText
  } catch {
    return text || response.statusText
  }
}

async function http<T>(url: string, init?: RequestInit): Promise<T> {
  let response: Response
  try {
    response = await fetch(url, {
      headers: { 'Content-Type': 'application/json' },
      ...init,
    })
  } catch {
    throw new ApiError('Cannot reach the backend. Is Spring Boot running on port 8081?', 0)
  }

  if (!response.ok) {
    throw new ApiError(await readError(response), response.status)
  }

  return response.status === 204 ? (undefined as T) : response.json()
}

const q = encodeURIComponent

export const submitLeave = (body: NewLeave) =>
  http<Leave>('/api/leaves', {
    method: 'POST',
    body: JSON.stringify(body),
  })

export const myLeaves = (email: string) =>
  http<Leave[]>(`/api/leaves?employeeEmail=${q(email)}`)

export const pendingLeaves = () => http<Leave[]>('/api/leaves/pending')

export const balance = (email: string) =>
  http<{ remainingDays: number }>(`/api/leaves/balance?employeeEmail=${q(email)}`)

// The Manager approval task may need a moment to appear in Camunda's search index.
// The backend answers 409 in that case, so retry a few times before giving up.
export async function decide(id: number, approved: boolean, comment: string) {
  for (let attempt = 0; ; attempt++) {
    try {
      return await http<void>(`/api/leaves/${id}/decision`, {
        method: 'POST',
        body: JSON.stringify({ approved, comment }),
      })
    } catch (error) {
      if (!(error instanceof ApiError) || error.status !== 409 || attempt >= 4) throw error
      await new Promise((resolve) => setTimeout(resolve, 1000))
    }
  }
}

export function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error)
}
