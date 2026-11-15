export type LeaveStatus =
  | 'SUBMITTED'
  | 'PENDING_APPROVAL'
  | 'APPROVED'
  | 'REJECTED'

export interface Leave {
  id: number
  employeeName: string
  employeeEmail: string
  leaveType: string
  startDate: string
  endDate: string
  days: number
  reason?: string
  status: LeaveStatus
  statusMessage?: string
  createdAt: string
}

export interface NewLeave {
  employeeName: string
  employeeEmail: string
  leaveType: string
  startDate: string
  endDate: string
  reason: string
}
