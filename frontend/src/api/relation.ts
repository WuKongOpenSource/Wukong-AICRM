import { post } from '@/utils/request'
import type { PageResult } from '@/types/api'
import type { RelationAddBO, RelationDetailVO, RelationQueryBO, RelationUpdateBO, RelationVO } from '@/types/relation'

export function addRelation(data: RelationAddBO): Promise<string> {
  return post('/relation/add', data)
}

export function updateRelation(data: RelationUpdateBO): Promise<void> {
  return post('/relation/update', data)
}

export function deleteRelation(id: string): Promise<void> {
  return post(`/relation/delete/${id}`)
}

export function queryRelationList(query: RelationQueryBO = {}): Promise<PageResult<RelationVO>> {
  return post('/relation/queryPageList', { page: 1, limit: 20, ...query })
}

export function getRelationDetail(id: string): Promise<RelationDetailVO> {
  return post(`/relation/detail/${id}`)
}

export function addRelationFromContact(contactId: string): Promise<string> {
  return post(`/relation/addFromContact/${contactId}`)
}
