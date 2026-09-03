import { api } from '../../shared/api/client'
import type {
  ActivityResponse, ContactResponse, CreateContactRequest, CreateCustomerRequest, CustomerDetailResponse,
  CustomerResponse, PageResponse, UpdateContactRequest, UpdateCustomerRequest,
} from '../../shared/api/types'

/** 고객사 API — 07-api-spec.md §B. 이 도메인의 호출은 여기서만 한다 (12 §8). */

export interface CustomerListParams {
  keyword?: string
  industry?: string
  page?: number
  size?: number
}

export async function fetchCustomers(params: CustomerListParams) {
  const { data } = await api.get<PageResponse<CustomerResponse>>('/customers', { params })
  return data
}

export async function fetchCustomer(id: string) {
  const { data } = await api.get<CustomerDetailResponse>(`/customers/${id}`)
  return data
}

export async function createCustomer(body: CreateCustomerRequest) {
  const { data } = await api.post<CustomerResponse>('/customers', body)
  return data
}

export async function updateCustomer(id: string, body: UpdateCustomerRequest) {
  const { data } = await api.patch<CustomerResponse>(`/customers/${id}`, body)
  return data
}

export async function deleteCustomer(id: string) {
  await api.delete(`/customers/${id}`)
}

export async function createContact(customerId: string, body: CreateContactRequest) {
  const { data } = await api.post<ContactResponse>(`/customers/${customerId}/contacts`, body)
  return data
}

export async function updateContact(customerId: string, contactId: string, body: UpdateContactRequest) {
  const { data } = await api.patch<ContactResponse>(`/customers/${customerId}/contacts/${contactId}`, body)
  return data
}

export async function deleteContact(customerId: string, contactId: string) {
  await api.delete(`/customers/${customerId}/contacts/${contactId}`)
}

export async function setPrimaryContact(customerId: string, contactId: string) {
  await api.post(`/customers/${customerId}/contacts/${contactId}/set-primary`)
}

export async function fetchCustomerActivities(customerId: string) {
  const { data } = await api.get<PageResponse<ActivityResponse>>(`/customers/${customerId}/activities`, { params: { size: 50 } })
  return data
}
