import type { AxiosResponse } from 'axios';
import { http } from './http';
import type {
  ActiveWarehouseResponse,
  ClaimResponse,
  CreateProductRequest,
  CreateUserRequest,
  CreateWarehouseRequest,
  CurrentUser,
  DashboardResponse,
  OrderUploadReport,
  PickerMappingResponse,
  Product,
  ReportFilters,
  ReportFormat,
  ReportRow,
  RouteResponse,
  ScanResponse,
  ShelfLocation,
  ShelfLocationRequest,
  TokenResponse,
  UpdateUserRequest,
  User,
  Warehouse,
} from '../types';

export const authApi = {
  login: (usernameOrEmail: string, password: string) =>
    http.post<TokenResponse>('/auth/login', { usernameOrEmail, password }).then((r) => r.data),
  logout: (refreshToken: string) => http.post('/auth/logout', { refreshToken }),
  me: () => http.get<CurrentUser>('/users/me').then((r) => r.data),
};

export const usersApi = {
  list: () => http.get<User[]>('/users').then((r) => r.data),
  create: (body: CreateUserRequest) => http.post<User>('/users', body).then((r) => r.data),
  update: (id: number, body: UpdateUserRequest) =>
    http.put<User>(`/users/${id}`, body).then((r) => r.data),
  remove: (id: number) => http.delete(`/users/${id}`),
};

export const warehousesApi = {
  list: () => http.get<Warehouse[]>('/warehouses').then((r) => r.data),
  create: (body: CreateWarehouseRequest) =>
    http.post<Warehouse>('/warehouses', body).then((r) => r.data),
};

export const productsApi = {
  list: () => http.get<Product[]>('/products').then((r) => r.data),
  create: (body: CreateProductRequest) =>
    http.post<Product>('/products', body).then((r) => r.data),
};

export const mappingApi = {
  mapPicker: (pickerId: number, warehouseIds: number[]) =>
    http.post<PickerMappingResponse>('/map-picker', { pickerId, warehouseIds }).then((r) => r.data),
};

export const shelfApi = {
  listByWarehouse: (warehouseId: number) =>
    http
      .get<ShelfLocation[]>('/shelf-locations', { params: { warehouseId } })
      .then((r) => r.data),
  create: (body: ShelfLocationRequest) =>
    http.post<ShelfLocation>('/shelf-location', body).then((r) => r.data),
  update: (body: ShelfLocationRequest) =>
    http.put<ShelfLocation>('/shelf-location', body).then((r) => r.data),
};

export const ordersApi = {
  // Admin: bulk order upload.
  upload: (file: File) => {
    const form = new FormData();
    form.append('file', file);
    return http
      .post<OrderUploadReport>('/orders/upload', form, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      .then((r) => r.data);
  },
  // Picker: claim an order / fetch its pick route.
  claim: (orderId: number) =>
    http.post<ClaimResponse>(`/orders/${orderId}/claim`).then((r) => r.data),
  route: (orderId: number) =>
    http.get<RouteResponse>(`/orders/${orderId}/route`).then((r) => r.data),
};

export const pickerApi = {
  myWarehouses: () => http.get<Warehouse[]>('/picker/warehouses').then((r) => r.data),
  selectWarehouse: (warehouseId: number) =>
    http
      .post<ActiveWarehouseResponse>('/picker/select-warehouse', { warehouseId })
      .then((r) => r.data),
  dashboard: () => http.get<DashboardResponse>('/picker/dashboard').then((r) => r.data),
};

export const scanApi = {
  scan: (orderId: number, sku: string) =>
    http.post<ScanResponse>('/scan', { orderId, sku }).then((r) => r.data),
  skip: (orderId: number, itemId: number) =>
    http.post<ScanResponse>(`/orders/${orderId}/items/${itemId}/skip`).then((r) => r.data),
};

function reportParams(filters: ReportFilters, format?: ReportFormat) {
  const params: Record<string, string | number> = {};
  if (filters.date) params.date = filters.date;
  if (filters.warehouse) params.warehouse = filters.warehouse;
  if (filters.picker != null) params.picker = filters.picker;
  if (format) params.format = format;
  return params;
}

export const reportApi = {
  json: (filters: ReportFilters) =>
    http.get<ReportRow[]>('/report', { params: reportParams(filters) }).then((r) => r.data),
  download: (filters: ReportFilters, format: ReportFormat): Promise<AxiosResponse<Blob>> =>
    http.get('/report', { params: reportParams(filters, format), responseType: 'blob' }),
};
