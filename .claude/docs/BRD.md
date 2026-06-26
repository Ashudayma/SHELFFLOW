Business Requirement Document (BRD)

Project Title: ShelfLife – Outbound Picking & Dispatch System

1. Document Overview & Objective

The purpose of this document is to outline the requirements for ShelfLife, an internal logistics application designed to orchestrate and optimize the picking of outbound fresh produce orders at fulfillment Hubs.

Currently, pickers manually locate items using paper lists, leading to slow fulfillment rates, picking errors, and inefficient travel paths. This application will allow Central Operations to ingest customer orders, map items to specific physical shelf coordinates, and guide Hub Pickers through an optimal walk-path where they scan and verify picked items.

2. User Roles & Role-Based Access Control (RBAC)

The system requires strict role-based access control to isolate user capabilities and secure warehouse inventory data.

Role
	

Operational Scope
	

Key Permissions

Central Admin
	

Global / Cross-Warehouse
	

System configuration, picker-to-hub mapping, bulk order uploads, master inventory shelf mapping, and cross-warehouse performance reports.

Hub Picker
	

Assigned Warehouse Only
	

Select and enter assigned warehouse, retrieve active pick lists, navigate shelf routes, and scan picked items.

3. Functional Requirements

3.1. User Management & Authentication

Secure Login: Standard secure login (email/username and password) with persistent session management.

Picker-to-Warehouse Mapping: A Central Admin must be able to map Hub Pickers to specific physical warehouses.

Data Isolation: A Hub Picker is restricted to viewing, modifying, or executing orders originating from and mapped to their designated warehouse.

3.2. Order Ingestion & Path Planning

Order Ingestion: Central Admin can upload daily customer orders (CSV/Excel) to generate picking tasks.

Data Fields Required per Order Line:

Order_ID (String, Unique)

Customer_ID (String)

Warehouse_ID (String)

Item_SKU (String)

Item_Name (String)

Quantity_Ordered (Positive Integer, representing Units/Eaches)

Master Location Mapping: The database must maintain a master layout mapping each Item_SKU to a specific physical location string (e.g., Aisle_A-Bay_04-Shelf_2).

Routing Engine: When a picker selects an order, the system must sort the items sequentially by their location identifier to present an optimized travel path.

3.3. Hub Picker Workflow (The "Pick & Pack" Module)

Warehouse Entry: Upon logging in, the Hub Picker registers their active physical Warehouse_ID to open their dashboard.

Order Selection: The picker claims an unfulfilled order from the queue, locking it from other pickers.

Scan-to-Pick:

The application interface must guide the picker location-by-location.

The picker navigates to the indicated shelf and scans the item's barcode containing the Item_SKU.

Scanning the correct Item_SKU increments the Picked Quantity by +1 (Each/Unit) for that line item.

The UI must prevent scanning items that do not belong to the current location step until the active step is completed or bypassed.

3.4. Reporting & Analytics

Dispatch Report: Central Admin must be able to download a performance report (CSV/Excel) filtered by Date, Warehouse, or Picker.

Report Schema Requirements: The report must output fulfillment performance:

Order_ID, Picker_ID, Warehouse_ID, Item_SKU, Item_Name

Quantity_Ordered

Quantity_Picked

Fulfillment_Rate (calculated dynamically)

The fulfillment rate must be represented mathematically as:

4. Technical & Non-Functional Requirements

Aesthetics & UI/UX: High contrast, large fonts, and minimal touch interactions. The application must be designed to run seamlessly on hand-held industrial rugged Android devices.

Audit Trail: Every scan, item skip, or pick completion must log a transaction history containing the timestamp, Order_ID, Item_SKU, Location, and User_ID.

Performance: High-frequency scanner inputs must resolve instantly without slowing down the picker's physical pace.

5. Candidate Deliverables & Instructions

Note to Candidate: Treat this BRD as a production-grade request. You are expected to analyze these requirements and present a technical proposal that balances architectural scale with deployment speed.

Proactive improvement is highly encouraged. If you notice gaps, edge cases, or potential optimization opportunities in this workflow, please feel free to propose changes, enhancements, or additional features in your proposal.

Please review the requirements above and develop the application.