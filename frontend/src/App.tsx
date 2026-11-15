import { useState } from 'react'
import ApplyForm from './pages/ApplyForm'
import MyRequests from './pages/MyRequests'
import ManagerInbox from './pages/ManagerInbox'
import './App.css'

type Tab = 'apply' | 'mine' | 'manager'

function stored(key: string): string {
  try {
    return localStorage.getItem(key) ?? ''
  } catch {
    return ''
  }
}

export default function App() {
  const [tab, setTab] = useState<Tab>('apply')
  const [name, setName] = useState(stored('name'))
  const [email, setEmail] = useState(stored('email'))
  const [version, setVersion] = useState(0)

  const save = (key: string, value: string, setter: (v: string) => void) => {
    try {
      localStorage.setItem(key, value)
    } catch {
      // storage unavailable: keep the value in memory only
    }
    setter(value)
  }

  return (
    <div className="app">
      <header className="topbar">
        <div>
          <h1>Leave Portal</h1>
          <p className="muted">Camunda 8 · Spring Boot · React</p>
        </div>
        <div className="who">
          <input
            placeholder="Your name"
            value={name}
            onChange={(e) => save('name', e.target.value, setName)}
          />
          <input
            placeholder="Your email"
            type="email"
            value={email}
            onChange={(e) => save('email', e.target.value, setEmail)}
          />
        </div>
      </header>

      <nav className="tabs">
        <button className={tab === 'apply' ? 'on' : ''} onClick={() => setTab('apply')}>
          Apply
        </button>
        <button className={tab === 'mine' ? 'on' : ''} onClick={() => setTab('mine')}>
          My requests
        </button>
        <button className={tab === 'manager' ? 'on' : ''} onClick={() => setTab('manager')}>
          Manager inbox
        </button>
      </nav>

      <main>
        {tab === 'apply' && (
          <ApplyForm
            name={name}
            email={email}
            onSubmitted={() => {
              setVersion((v) => v + 1)
              setTab('mine')
            }}
          />
        )}
        {tab === 'mine' && <MyRequests email={email} version={version} />}
        {tab === 'manager' && <ManagerInbox />}
      </main>
    </div>
  )
}
