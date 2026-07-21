export type FinanceTab = 'dashboard' | 'contract' | 'receivable' | 'payment' | 'invoice' | 'expense'

export interface FinanceQueryBO {
  keyword?: string
  customerId?: string
  projectId?: string
  ownerId?: string
  status?: string
  overdue?: boolean
  startDate?: string
  endDate?: string
  page?: number
  limit?: number
}

export interface FinanceRecordVO {
  id: string
  contractId?: string
  receivableId?: string
  paymentId?: string
  invoiceId?: string
  expenseId?: string
  contractNo?: string
  contractName?: string
  title?: string
  invoiceNo?: string
  taxNo?: string
  expenseType?: string
  customerId?: string
  customerName?: string
  projectId?: string
  projectName?: string
  ownerId?: string
  ownerName?: string
  amount?: number | string
  receivedAmount?: number | string
  unpaidAmount?: number | string
  signDate?: string
  startDate?: string
  endDate?: string
  dueDate?: string
  overdueDays?: number
  paymentDate?: string
  paymentMethod?: string
  invoiceDate?: string
  expenseDate?: string
  status?: string
  remark?: string
  sourceType?: string
  sourceText?: string
  aiCreated?: boolean
  createUserId?: string
  createUserName?: string
  createTime?: string
  updateTime?: string
  customFields?: Record<string, unknown>
}

export interface FinanceDashboardVO {
  monthIncome: number | string
  monthExpense: number | string
  netCashFlow: number | string
  receivable30: number | string
  receivable60: number | string
  receivable90: number | string
  overdueReceivable: number | string
  cashFlow: Array<{ month: string; income: number | string; expense: number | string; net: number | string }>
  recentRecords: FinanceRecordVO[]
}

export interface FinanceContractBO {
  contractId?: string
  contractNo?: string
  contractName: string
  customerId?: string
  projectId?: string
  ownerId?: string
  amount: number | string
  signDate?: string
  startDate?: string
  endDate?: string
  status?: string
  remark?: string
  customFields?: Record<string, unknown>
}

export interface FinanceReceivableBO {
  receivableId?: string
  contractId?: string
  customerId?: string
  projectId?: string
  ownerId?: string
  title: string
  amount: number | string
  dueDate?: string
  status?: string
  remark?: string
  customFields?: Record<string, unknown>
}

export interface FinancePaymentBO {
  paymentId?: string
  receivableId?: string
  contractId?: string
  customerId?: string
  projectId?: string
  ownerId?: string
  amount: number | string
  paymentDate?: string
  paymentMethod?: string
  remark?: string
  customFields?: Record<string, unknown>
}

export interface FinanceInvoiceBO {
  invoiceId?: string
  contractId?: string
  receivableId?: string
  customerId?: string
  projectId?: string
  ownerId?: string
  invoiceNo?: string
  title: string
  taxNo?: string
  amount: number | string
  invoiceDate?: string
  status?: string
  remark?: string
  customFields?: Record<string, unknown>
}

export interface FinanceExpenseBO {
  expenseId?: string
  customerId?: string
  projectId?: string
  ownerId?: string
  expenseType: string
  amount: number | string
  expenseDate?: string
  status?: string
  remark?: string
  customFields?: Record<string, unknown>
}
