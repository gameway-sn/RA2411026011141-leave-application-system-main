import { useCallback, useEffect, useState } from 'react'
import { decide, errorMessage, pendingLeaves } from '../services/api'
import type { Leave } from '../types/leave'

export default function ManagerInbox() {
  const [rows, setRows] = useState<Leave[]>([])
  const [comments, setComments] = useState<Record<number, string>>({})
  const [hidden, setHidden] = useState<number[]>([])
  const [busy, setBusy] = useState<number | null>(null)
  const [error, setError] = useState('')

  const load = useCallback(() => {
    pendingLeaves()
      .then((data) => {
        setRows(data)
        setError('')
      })
      .catch((e) => setError(errorMessage(e)))
  }, [])

  useEffect(() => {
    load()
    const timer = setInterval(load, 3000)
    return () => clearInterval(timer)
  }, [load])

  async function act(id: number, approved: boolean) {
    setBusy(id)
    setError('')
    try {
      await decide(id, approved, comments[id] ?? '')
      // Hide until the finalize-leave worker has changed the status in the database.
      setHidden((current) => [...current, id])
      load()
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(null)
    }
  }

  const visible = rows.filter((row) => !hidden.includes(row.id))

  return (
    <div>
      <h2 className="section-title">Manager inbox</h2>
      {error && <p className="error">{error}</p>}
      {visible.length === 0 && <p className="card muted">No requests are waiting for approval.</p>}
      {visible.map((r) => (
        <div key={r.id} className="card">
          <div className="card-head">
            <strong>
              #{r.id} {r.employeeName}
            </strong>
            <span className="muted">{r.employeeEmail}</span>
          </div>
          <div>
            {r.leaveType}, {r.startDate} to {r.endDate}, {r.days} working day(s)
          </div>
          {r.reason && <p className="muted">{r.reason}</p>}
          <label>
            Comment
            <input
              placeholder="Optional manager comment"
              value={comments[r.id] ?? ''}
              onChange={(e) => setComments({ ...comments, [r.id]: e.target.value })}
            />
          </label>
          <div className="actions">
            <button className="primary" disabled={busy === r.id} onClick={() => act(r.id, true)}>
              Approve
            </button>
            <button className="danger" disabled={busy === r.id} onClick={() => act(r.id, false)}>
              Reject
            </button>
          </div>
        </div>
      ))}
    </div>
  )
}
