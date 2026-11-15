import { useEffect, useState } from 'react'
import { balance, errorMessage, myLeaves } from '../services/api'
import type { Leave } from '../types/leave'
import StatusBadge from '../components/StatusBadge'

interface Props {
  email: string
  version: number
}

export default function MyRequests({ email, version }: Props) {
  const [rows, setRows] = useState<Leave[]>([])
  const [remaining, setRemaining] = useState<number | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!email) return
    let alive = true

    const load = async () => {
      try {
        const [requests, leaveBalance] = await Promise.all([myLeaves(email), balance(email)])
        if (alive) {
          setRows(requests)
          setRemaining(leaveBalance.remainingDays)
          setError('')
        }
      } catch (e) {
        if (alive) setError(errorMessage(e))
      }
    }

    load()
    // Workers finish asynchronously, so poll for status changes.
    const timer = setInterval(load, 3000)
    return () => {
      alive = false
      clearInterval(timer)
    }
  }, [email, version])

  if (!email) return <p className="card muted">Enter your email at the top to see your requests.</p>

  return (
    <div className="card">
      <div className="card-head">
        <h2>My requests</h2>
        <div className="balance">
          Annual leave remaining: <strong>{remaining ?? '...'}</strong> day(s)
        </div>
      </div>
      {error && <p className="error">{error}</p>}
      {rows.length === 0 && !error && <p className="muted">No requests yet.</p>}
      {rows.length > 0 && (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>#</th>
                <th>Type</th>
                <th>Dates</th>
                <th>Days</th>
                <th>Status</th>
                <th>Note</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.id}>
                  <td>{r.id}</td>
                  <td>{r.leaveType}</td>
                  <td>
                    {r.startDate} to {r.endDate}
                  </td>
                  <td>{r.days}</td>
                  <td>
                    <StatusBadge status={r.status} />
                  </td>
                  <td>{r.statusMessage}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
