// Types mirroring the ShelfLife backend DTOs.

export type Role = 'CENTRAL_ADMIN' | 'HUB_PICKER';
export type UserStatus = 'ACTIVE' | 'INACTIVE' | 'SUSPENDED';

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
}

export interface CurrentUser {
  id: number;
  email: string;
  role: Role;
  warehouseIds: number[];
}

export interface User {
  id: number;
  name: string;
  email: string;
  role: Role;
  status: UserStatus;
}

export interface CreateUserRequest {
  name: string;
  email: string;
  password: string;
  role: Role;
  status?: UserStatus;
}

export interface UpdateUserRequest {
  name: string;
  email: string;
  role: Role;
  status: UserStatus;
  password?: string;
}

export interface Warehouse {
  id: number;
  warehouseCode: string;
  warehouseName: string;
  address?: string | null;
}

export interface CreateWarehouseRequest {
  warehouseCode: string;
  warehouseName: string;
  address?: string;
}

export interface Product {
  sku: string;
  name: string;
  barcode?: string | null;
  unit?: string | null;
}

export interface CreateProductRequest {
  sku: string;
  name: string;
  barcode?: string;
  unit?: string;
}

export interface ShelfLocation {
  id: number;
  warehouseId: number;
  sku: string;
  aisle?: string | null;
  bay?: string | null;
  shelf?: string | null;
  locationCode: string;
}

export interface ShelfLocationRequest {
  warehouseId: number;
  sku: string;
  locationCode: string;
}

export interface PickerMappingResponse {
  pickerId: number;
  warehouseIds: number[];
}

export type RowResultStatus = 'SUCCESS' | 'FAILED' | 'SKIPPED';

export interface OrderUploadRowResult {
  row: number;
  orderId: string;
  status: RowResultStatus;
  error: string | null;
}

export interface OrderUploadReport {
  totalRows: number;
  successCount: number;
  failedCount: number;
  skippedCount: number;
  ordersCreated: number;
  rows: OrderUploadRowResult[];
}

// Report rows use the exact BRD column names as JSON keys.
export interface ReportRow {
  Order_ID: string;
  Picker_ID: number | null;
  Warehouse_ID: string;
  Item_SKU: string;
  Item_Name: string;
  Quantity_Ordered: number;
  Quantity_Picked: number;
  Fulfillment_Rate: number;
}

export interface ReportFilters {
  date?: string;
  warehouse?: string;
  picker?: number;
}

export type ReportFormat = 'csv' | 'xlsx';

// --- Picker workflow types (migrated from picker-frontend) ---------------------------

export type OrderStatus = 'PENDING' | 'ASSIGNED' | 'PICKING' | 'COMPLETED' | 'DISPATCHED';
export type OrderItemStatus = 'PENDING' | 'PICKED' | 'SKIPPED';

export interface ActiveWarehouseResponse {
  warehouseId: number;
  warehouseCode: string;
  warehouseName: string;
  selectedAt: string;
}

export interface OrderSummary {
  id: number;
  orderNumber: string;
  customerId: string;
  status: OrderStatus;
  warehouseId: number;
  pickerId: number | null;
}

export interface DashboardResponse {
  activeWarehouseId: number;
  available: OrderSummary[];
  current: OrderSummary[];
  completed: OrderSummary[];
}

export interface ClaimResponse {
  orderId: number;
  orderNumber: string;
  status: OrderStatus;
  pickerId: number;
  warehouseId: number;
  version: number;
}

export interface RouteStep {
  sequence: number;
  itemId: number;
  locationCode: string | null;
  aisle: string | null;
  bay: string | null;
  shelf: string | null;
  sku: string;
  itemName: string;
  orderedQuantity: number;
  pickedQuantity: number;
  status: OrderItemStatus;
}

export interface RouteResponse {
  orderId: number;
  orderNumber: string;
  warehouseId: number;
  steps: RouteStep[];
}

export interface ScannedItem {
  itemId: number;
  sku: string;
  itemName: string;
  orderedQuantity: number;
  pickedQuantity: number;
  status: OrderItemStatus;
}

export interface StepView {
  itemId: number;
  sku: string;
  itemName: string;
  locationCode: string | null;
  orderedQuantity: number;
  pickedQuantity: number;
}

export interface ScanResponse {
  orderId: number;
  orderNumber: string;
  orderStatus: OrderStatus;
  item: ScannedItem | null;
  nextStep: StepView | null;
  duplicate: boolean;
}
