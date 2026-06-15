import { get, post } from '@/utils/request'
import type { PageResult } from '@/types/api'
import type { AddressBookDetail, AddressBookEmployee, AddressBookQuery } from '@/types/addressBook'

export function queryAddressBook(query: AddressBookQuery = {}): Promise<PageResult<AddressBookEmployee>> {
  return post('/addressBook/queryPageList', { page: 1, limit: 20, ...query })
}

export function getAddressBookDetail(userId: string | number): Promise<AddressBookDetail> {
  return get(`/addressBook/detail/${userId}`)
}
