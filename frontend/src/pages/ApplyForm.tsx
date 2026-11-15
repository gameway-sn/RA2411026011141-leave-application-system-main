import { useState, type FormEvent } from 'react'
import { errorMessage, submitLeave } from '../services/api'

interface Props {
  name: string
  email: string
  onSubmitted: () => void
}

export default function ApplyForm({ name, email, onSubmitted }: Props) {
  const [leaveType, setLeaveType] = useState('ANNUAL')
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')
  const [reason, setReason] = useState('')
  const [message, setMessage] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (!name.trim() || !email.trim()) {
      setMessage('Enter your name and email at the top first.')
      return
    }

    setBusy(true)
    setMessage(null)
    try {
      await submitLeave({
        employeeName: name,
        employeeEmail: email,
        leaveType,
        startDate,
        endDate,
        reason,
      })
      setStartDate('')
      setEndDate('')
      setReason('')
      onSubmitted()
    } catch (error) {
      setMessage(`Could not submit: ${errorMessage(error)}`)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={submit} className="card">
      <h2>Apply for leave</h2>
      <label>
        Leave type
        <select value={leaveType} onChange={(e) => setLeaveType(e.target.value)}>
          <option value="ANNUAL">Annual</option>
          <option value="SICK">Sick</option>
          <option value="UNPAID">Unpaid</option>
        </select>
      </label>
      <div className="row">
        <label>
          From
          <input
            type="date"
            required
            value={startDate}
            onChange={(e) => setStartDate(e.target.value)}
          />
        </label>
        <label>
          To
          <input
            type="date"
            required
            min={startDate}
            value={endDate}
            onChange={(e) => setEndDate(e.target.value)}
          />
        </label>
      </div>
      <label>
        Reason
        <textarea value={reason} onChange={(e) => setReason(e.target.value)} />
      </label>
      <button className="primary" disabled={busy}>
        {busy ? 'Submitting...' : 'Submit request'}
      </button>
      {message && <p className="error">{message}</p>}
    </form>
  )
}
