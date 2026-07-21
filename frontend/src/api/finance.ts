import { get, post } from '@/utils/request'
import type { PageResult } from '@/types/api'
import type {
  FinanceContractBO,
  FinanceDashboardVO,
  FinanceExpenseBO,
  FinanceInvoiceBO,
  FinancePaymentBO,
  FinanceQueryBO,
  FinanceReceivableBO,
  FinanceRecordVO
} from '@/types/finance'

export function getFinanceDashboard(query: FinanceQueryBO = {}): Promise<FinanceDashboardVO> {
  return post('/finance/dashboard', query)
}

export function queryFinanceContracts(query: FinanceQueryBO): Promise<PageResult<FinanceRecordVO>> {
  return post('/finance/contract/queryPageList', query)
}

export function getFinanceContractDetail(id: string | number): Promise<FinanceRecordVO> {
  return get(`/finance/contract/detail/${id}`)
}

export function addFinanceContract(data: FinanceContractBO): Promise<string> {
  return post('/finance/contract/add', data)
}

export function updateFinanceContract(data: FinanceContractBO): Promise<void> {
  return post('/finance/contract/update', data)
}

export function deleteFinanceContract(id: string): Promise<void> {
  return post(`/finance/contract/delete/${id}`)
}

export function queryFinanceReceivables(query: FinanceQueryBO): Promise<PageResult<FinanceRecordVO>> {
  return post('/finance/receivable/queryPageList', query)
}

export function getFinanceReceivableDetail(id: string | number): Promise<FinanceRecordVO> {
  return get(`/finance/receivable/detail/${id}`)
}

export function addFinanceReceivable(data: FinanceReceivableBO): Promise<string> {
  return post('/finance/receivable/add', data)
}

export function updateFinanceReceivable(data: FinanceReceivableBO): Promise<void> {
  return post('/finance/receivable/update', data)
}

export function deleteFinanceReceivable(id: string): Promise<void> {
  return post(`/finance/receivable/delete/${id}`)
}

export function queryFinancePayments(query: FinanceQueryBO): Promise<PageResult<FinanceRecordVO>> {
  return post('/finance/payment/queryPageList', query)
}

export function getFinancePaymentDetail(id: string | number): Promise<FinanceRecordVO> {
  return get(`/finance/payment/detail/${id}`)
}

export function addFinancePayment(data: FinancePaymentBO): Promise<string> {
  return post('/finance/payment/add', data)
}

export function updateFinancePayment(data: FinancePaymentBO): Promise<void> {
  return post('/finance/payment/update', data)
}

export function deleteFinancePayment(id: string): Promise<void> {
  return post(`/finance/payment/delete/${id}`)
}

export function queryFinanceInvoices(query: FinanceQueryBO): Promise<PageResult<FinanceRecordVO>> {
  return post('/finance/invoice/queryPageList', query)
}

export function getFinanceInvoiceDetail(id: string | number): Promise<FinanceRecordVO> {
  return get(`/finance/invoice/detail/${id}`)
}

export function addFinanceInvoice(data: FinanceInvoiceBO): Promise<string> {
  return post('/finance/invoice/add', data)
}

export function updateFinanceInvoice(data: FinanceInvoiceBO): Promise<void> {
  return post('/finance/invoice/update', data)
}

export function deleteFinanceInvoice(id: string): Promise<void> {
  return post(`/finance/invoice/delete/${id}`)
}

export function queryFinanceExpenses(query: FinanceQueryBO): Promise<PageResult<FinanceRecordVO>> {
  return post('/finance/expense/queryPageList', query)
}

export function getFinanceExpenseDetail(id: string | number): Promise<FinanceRecordVO> {
  return get(`/finance/expense/detail/${id}`)
}

export function addFinanceExpense(data: FinanceExpenseBO): Promise<string> {
  return post('/finance/expense/add', data)
}

export function updateFinanceExpense(data: FinanceExpenseBO): Promise<void> {
  return post('/finance/expense/update', data)
}

export function deleteFinanceExpense(id: string): Promise<void> {
  return post(`/finance/expense/delete/${id}`)
}
