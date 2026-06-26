# Inventory Master Data: Products & Location Mapping

Admin-managed master data in the `inventory` feature (products + shelf locations).

## Products / SKU (`/products`, CENTRAL_ADMIN only)
| Method & path | Purpose | Notes |
|---------------|---------|-------|
| GET  /products | list all products | |
| POST /products | create a product | 201; **409** if `sku` already exists (service-layer `existsById` check) |

Fields: `sku` (natural PK), `name` (required), `barcode`, `unit`.
Classes: `inventory/Product` (entity, assigned `@Id` String), `ProductRepository`,
`ProductService`, `ProductController`, `dto/CreateProductRequest`, `dto/ProductResponse`.

## Master Location Mapping (BRD 3.2) (`/shelf-location`, CENTRAL_ADMIN only)
Maps a SKU to a `location_code` within a specific warehouse.

| Method & path | Purpose | Notes |
|---------------|---------|-------|
| POST /shelf-location | create a (warehouse, sku) → location mapping | 201 |
| PUT  /shelf-location | change the location for an existing (warehouse, sku) | 200; **404** if no mapping exists |

Request body: `{warehouseId, sku, locationCode}`. **`aisle`/`bay`/`shelf` are NOT accepted from
the client** — they are parsed from the validated `locationCode` so stored components always
match the code.

### Validation & error semantics (all at the service layer)
- **Format**: `location_code` validated by regex in `inventory/LocationCodes`
  (`^Aisle_([A-Za-z0-9]+)-Bay_([0-9]+)-Shelf_([0-9]+)$`), canonical example
  `Aisle_A-Bay_04-Shelf_2`. Malformed → **400** with a descriptive message naming the
  expected format (not a raw parse error).
- **Uniqueness**: the unique `(warehouse_id, sku)` constraint is checked via
  `existsByWarehouseIdAndSku` and surfaced as a clear **409** (`ConflictException`), not a raw
  `DataIntegrityViolationException`. (The DB constraint + the 409→DataIntegrity handler remain
  a backstop for races.)
- **References**: unknown `warehouseId` or `sku` → **404** (checked before insert, so the FK
  is never hit with a clear cause).

Classes: `inventory/ShelfLocation` (entity), `ShelfLocationRepository`
(`findByWarehouseIdAndSku`, `existsByWarehouseIdAndSku`), `ShelfLocationService`
(`create`/`update`), `ShelfLocationAdminController`, `dto/ShelfLocationRequest`,
`LocationCodes` + `ParsedLocation`.

### Path note
Picker-facing read is `GET /shelf-locations` (plural, see
`management-and-data-isolation.md`); admin writes are `POST/PUT /shelf-location` (singular),
per the BRD wording. Two paths, deliberately.

## Tests
`inventory/ProductAndLocationMappingTest` (6):
1. create + list products; duplicate sku → 409.
2. map sku→location (asserts parsed aisle/bay/shelf); duplicate (warehouse, sku) → 409.
3. malformed `location_code` → 400 with descriptive message.
4. unknown warehouse / sku → 404.
5. PUT updates an existing mapping; PUT on an unmapped (warehouse, sku) → 404.
6. RBAC: HUB_PICKER cannot POST `/products` or `/shelf-location` (403).

## Follow-ups
- `POST /products` is create-only; no update/delete yet.
- Location components assume numeric bay/shelf and alphanumeric aisle — widen the regex in
  `LocationCodes` if real codes differ.
