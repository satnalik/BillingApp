# Billing App Application Flow Design

This document explains the Billing App backend flow using diagrams and short notes. It is intended for developers, testers, and new team members who need to understand how requests move through the system and how the main business scenarios connect.

## System Context

```mermaid
flowchart LR
    User[Store User] --> UI[Frontend UI<br/>localhost:5173]
    UI -->|REST API + JWT| API[Spring Boot Backend<br/>localhost:8080]
    API --> Security[Spring Security<br/>JWT Filter]
    API --> Tenant[Tenant Context<br/>Tenant Interceptor]
    API --> Services[Business Services]
    Services --> Repositories[JPA Repositories]
    Repositories --> DB[(Database)]
    Services --> PDF[PDF Services<br/>Bills and Barcode Labels]
```

The frontend calls the backend REST APIs. After login, the backend returns a JWT token. Protected requests use the JWT to identify the user, role, and tenant.

## Main Backend Layers

```mermaid
flowchart TB
    Controllers[Controllers<br/>API endpoints]
    Security[Security Layer<br/>JWT authentication + role authorization]
    Tenant[Multi-Tenant Layer<br/>TenantContext + Hibernate tenant filter]
    Services[Services<br/>Business rules and transactions]
    Repos[Repositories<br/>Database access]
    Entities[Entities<br/>Products, Bills, Purchases, Users]

    Controllers --> Security
    Security --> Tenant
    Tenant --> Services
    Services --> Repos
    Repos --> Entities
```

## Authentication And Tenant Flow

```mermaid
sequenceDiagram
    actor User
    participant UI as Frontend UI
    participant Auth as AuthController
    participant JWT as JwtService
    participant Sec as JwtAuthenticationFilter
    participant Tenant as TenantInterceptor / TenantContext
    participant Repo as Repository + Hibernate Filter

    User->>UI: Enter userId and password
    UI->>Auth: POST /api/auth/login
    Auth->>JWT: Generate token with userId, tenantId, role
    Auth-->>UI: token + user data
    UI->>Sec: Protected API request with Bearer token
    Sec->>Sec: Validate token and set authenticated user
    UI->>Tenant: Same request reaches interceptor
    Tenant->>JWT: Extract tenantId from token
    Tenant->>Tenant: Store tenantId in TenantContext
    Repo->>Repo: Enable Hibernate tenantFilter
    Repo-->>UI: Return tenant-scoped data only
    Tenant->>Tenant: Clear TenantContext after request
```

## Role-Based Access

```mermaid
flowchart LR
    Login[Public<br/>/api/auth/login]
    ProvisionOwner[Operator key<br/>/api/provisioning/tenants/{tenantId}/owner]
    AddUser[Admin only<br/>/api/users/adduser]
    Cashier[Role: CASHIER]
    Admin[Role: ADMIN]

    Cashier --> Bills[/api/bills/**]
    Cashier --> Products[/api/products/**]
    Cashier --> SalesmanList[/api/salesman]
    Cashier --> Dashboard[/api/dashboard/**]

    Admin --> Bills
    Admin --> Products
    Admin --> SalesmanList
    Admin --> Dashboard
    Admin --> AddSalesman[/api/salesman/addsalesman]
    Admin --> Reports[/api/reports/**]

    Login -.-> Cashier
    Login -.-> Admin
    ProvisionOwner -.-> Admin
    Admin --> AddUser
```

The application operator provisions the first tenant admin through Swagger using the `X-Provisioning-Key` header. Set a high-entropy `APP_PROVISIONING_KEY` of at least 32 characters in the backend environment before provisioning; if it is missing or too short, the provisioning endpoint is disabled. Provisioning creates an `ADMIN` user and refuses a tenant that already has a user. The Swagger operation is `POST /api/provisioning/tenants/{tenantId}/owner` with a body such as `{"userId":"owner@example.com","password":"replace-with-a-long-unique-password","name":"Store Owner"}`. After that, an authenticated tenant admin can create `CASHIER` and `MANAGER` users. The server takes the tenant ID from the signed-in admin's JWT and does not accept a tenant ID or admin role from the staff creation request.

Password changes require the authenticated user's current password and new password. The target user is always taken from the authenticated principal; a caller cannot choose another user ID.

The first-login form asks for the current/temporary password, a new password of at least 12 characters, and confirmation. It sends `POST /api/auth/change-password` with the login bearer token and JSON containing `currentPassword` and `newPassword`. After a successful update it clears the local first-login flag and continues the session. An incorrect current password or invalid new password returns a validation error (400) for display in the form; it does not trigger the frontend's 401 logout handler.

Role access is enforced by API area: cashiers can use sales, products (read only), customers, salesman lookup, and dashboard endpoints. Managers can perform operational and inventory tasks and view reports. Tenant admins can manage staff and use all tenant functions. Only the platform operator key can provision a tenant's first admin.

## Master Data Flow

```mermaid
flowchart TB
    Supplier[Supplier Master]
    Product[Product Master]
    Barcode[Product Barcode]
    Salesman[Salesman Master]
    Customer[Customer]

    Supplier -->|Optional link| Product
    Product -->|Primary or extra barcodes| Barcode
    Salesman -->|Selected on sales bill| Bill[Sales Bill]
    Customer -->|Created/updated from bill contact| Bill
    Product -->|Sold as bill item| BillItem[Bill Item]
    Bill --> BillItem
```

Master data supports transaction flows:

- Suppliers are used for purchase bills and can be linked to products.
- Products carry price, stock, category, GST, HSN, supplier, and barcode information.
- Product barcodes can represent one product scan or a configured quantity per scan.
- Salesmen are attached to sales bills.
- Customers are captured automatically from bill customer/contact details.

## Purchase / Stock Inward Flow

```mermaid
flowchart TD
    Start([Create Purchase Bill])
    SupplierCheck{Supplier exists<br/>for tenant?}
    ItemsCheck{At least one item?}
    ProductResolve[Resolve each product<br/>by productId or barcode]
    BarcodeLink[Create barcode link<br/>when needed]
    StockIncrease[Increase product stock]
    PriceUpdate[Update cost/selling price]
    Totals[Calculate subtotal,<br/>discount, tax, total]
    Payment[Apply paid amount<br/>and supplier due]
    Save[Save purchase bill,<br/>items, payments]
    Labels[Generate barcode labels / PDF]
    End([Purchase completed])

    Start --> SupplierCheck
    SupplierCheck -- No --> Error1[Reject: Supplier required/not found]
    SupplierCheck -- Yes --> ItemsCheck
    ItemsCheck -- No --> Error2[Reject: Purchase item required]
    ItemsCheck -- Yes --> ProductResolve
    ProductResolve --> BarcodeLink
    BarcodeLink --> StockIncrease
    StockIncrease --> PriceUpdate
    PriceUpdate --> Totals
    Totals --> Payment
    Payment --> Save
    Save --> Labels
    Labels --> End
```

Purchase rules:

- Purchase bill number is required and unique per tenant, supplier, and bill number.
- Quantity must be greater than zero.
- Purchase price must be zero or greater.
- Creating a purchase increases product stock.
- Paid amount cannot exceed purchase total.
- Supplier due is `totalAmount - paidAmount`.
- A purchase cannot be cancelled after supplier payment is recorded.
- Cancelling a purchase reverses stock and marks the purchase as `CANCELLED`.

## Sales Billing Flow

```mermaid
flowchart TD
    Start([Create Sales Bill])
    SalesmanCheck{Salesman exists?}
    ItemsCheck{At least one item?}
    CustomerCapture[Create/update customer<br/>from contact number]
    ResolveProduct[Resolve product by<br/>barcode, productId, or name]
    StockCheck{Enough stock?}
    ReduceStock[Reduce product stock]
    LineCalc[Calculate line taxable,<br/>discount, GST]
    BillTotals[Calculate subtotal,<br/>GST, instant discount,<br/>grand total]
    Payments[Apply payments]
    Credit[Auto-add CREDIT<br/>for unpaid balance]
    Save[Save bill, items, payments]
    PDF[Download bill PDF]
    End([Bill completed])

    Start --> SalesmanCheck
    SalesmanCheck -- No --> Error1[Reject: Salesman not found]
    SalesmanCheck -- Yes --> ItemsCheck
    ItemsCheck -- No --> Error2[Reject: At least one item required]
    ItemsCheck -- Yes --> CustomerCapture
    CustomerCapture --> ResolveProduct
    ResolveProduct --> StockCheck
    StockCheck -- No --> Error3[Reject: Insufficient stock]
    StockCheck -- Yes --> ReduceStock
    ReduceStock --> LineCalc
    LineCalc --> BillTotals
    BillTotals --> Payments
    Payments --> Credit
    Credit --> Save
    Save --> PDF
    PDF --> End
```

Sales billing rules:

- A bill must have a salesman and at least one item.
- Product can be resolved by barcode, product ID, or product name.
- Barcode must belong to the requested product when both are supplied.
- Stock is reduced during bill creation.
- GST is calculated per item using the product GST rate.
- Instant discount is an absolute amount and cannot exceed the bill total.
- If no payment is sent, the full bill becomes `CREDIT`.
- If partial payment is sent, the remaining amount is auto-recorded as `CREDIT`.
- Sum of payments cannot exceed the bill total.

## Customer Due Collection Flow

```mermaid
flowchart TD
    Start([Collect Customer Due])
    LoadBill[Load bill with lock]
    ValidateMethod{Payment method<br/>is not CREDIT?}
    ValidateAmount{Amount > 0 and<br/>amount <= current due?}
    AddPayment[Add positive payment<br/>Cash/UPI/Card/etc.]
    AdjustCredit[Add negative CREDIT<br/>adjustment]
    Recompute[Recompute paid and due]
    Save[Save bill]
    End([Due updated])

    Start --> LoadBill
    LoadBill --> ValidateMethod
    ValidateMethod -- No --> Error1[Reject]
    ValidateMethod -- Yes --> ValidateAmount
    ValidateAmount -- No --> Error2[Reject]
    ValidateAmount -- Yes --> AddPayment
    AddPayment --> AdjustCredit
    AdjustCredit --> Recompute
    Recompute --> Save
    Save --> End
```

## Supplier Due Payment Flow

```mermaid
flowchart TD
    Start([Pay Supplier Due])
    LoadPurchase[Load purchase bill with lock]
    StatusCheck{Purchase active?}
    MethodCheck{Payment method<br/>is not CREDIT?}
    AmountCheck{Amount > 0 and<br/>amount <= supplier due?}
    AddPayment[Add supplier payment]
    UpdateDue[Increase paid amount<br/>and reduce due]
    Save[Save purchase bill]
    End([Supplier due updated])

    Start --> LoadPurchase
    LoadPurchase --> StatusCheck
    StatusCheck -- No --> Error1[Reject cancelled purchase]
    StatusCheck -- Yes --> MethodCheck
    MethodCheck -- No --> Error2[Reject CREDIT method]
    MethodCheck -- Yes --> AmountCheck
    AmountCheck -- No --> Error3[Reject invalid amount]
    AmountCheck -- Yes --> AddPayment
    AddPayment --> UpdateDue
    UpdateDue --> Save
    Save --> End
```

## Reporting And Dashboard Flow

```mermaid
flowchart LR
    UI[Frontend UI] --> Dashboard[/api/dashboard/today]
    UI --> Daily[/api/reports/daily]
    UI --> Monthly[/api/reports/monthly]
    UI --> Range[/api/reports/range]
    UI --> Sales[/api/reports/sales]
    UI --> DayEnd[/api/reports/day-end]
    UI --> Register[/api/bills/register]
    UI --> Summary[/api/bills/register/summary]

    Dashboard --> Bills[(Bills)]
    Dashboard --> Products[(Products)]
    Daily --> Bills
    Monthly --> Bills
    Range --> Bills
    Sales --> Bills
    DayEnd --> Bills
    Register --> Bills
    Summary --> Bills
```

Reports are tenant-scoped and use bill/payment data to calculate totals, payment splits, dues, and date-based summaries.

## Entity Relationship Diagram

This is the main ER diagram for the application. It shows the core tables, primary keys, foreign keys, tenant ownership, and transaction relationships.

```mermaid
erDiagram
    USER {
        long id PK
        string userId
        string password
        string name
        enum role
        boolean is_FirstTimeLogin
        string tenantId
    }

    SUPPLIER {
        long id PK
        string name
        string supplierCode UK
        string contactPerson
        string phoneNumber
        string email
        string gstNumber
        string address
        boolean active
        string tenantId
    }

    PRODUCT {
        long id PK
        string name
        string barcode
        enum itemType
        long supplier_id FK
        string supplierName
        double costPrice
        double landingPrice
        double mrp
        double sellingPrice
        double price
        double stockQuantity
        string category
        string hsnCode
        double gstRate
        string tenantId
    }

    PRODUCT_BARCODE {
        long id PK
        long product_id FK
        string barcode UK
        double quantityPerScan
        string barcodeType
        boolean primaryBarcode
        string tenantId
    }

    SALESMAN {
        string employeeId PK
        string name
        string phoneNumber
        boolean active
        string tenantId
    }

    CUSTOMER {
        long id PK
        string name
        string contactNumber UK
        datetime createdAt
        string tenantId
    }

    BILL {
        long id PK
        long version
        string customerName
        string contactInfo
        string salesman_employee_id FK
        double subTotalAmount
        boolean gstApplied
        double gstRate
        double gstAmount
        double instantDiscountAmount
        double totalAmount
        double paidAmount
        double dueAmount
        datetime createdAt
        string tenantId
    }

    BILL_ITEM {
        long id PK
        long bill_id FK
        long productId
        string barcode
        string productName
        double quantity
        double unitSellingPrice
        double priceAtSale
        double discount
        string hsnCode
        double gstRate
        double taxableAmount
        double gstAmount
    }

    BILL_PAYMENT {
        long id PK
        long bill_id FK
        enum method
        double amount
        string reference
        datetime createdAt
    }

    PURCHASE_BILL {
        long id PK
        long supplier_id FK
        string billNumber UK
        date billDate
        double subTotalAmount
        double discountAmount
        double taxAmount
        double totalAmount
        double paidAmount
        double dueAmount
        enum status
        string cancelReason
        datetime cancelledAt
        string notes
        datetime createdAt
        string tenantId
    }

    PURCHASE_BILL_ITEM {
        long id PK
        long purchase_bill_id FK
        long product_id FK
        string barcode
        string productName
        double quantity
        double purchasePrice
        double sellingPrice
        double lineTotal
    }

    PURCHASE_PAYMENT {
        long id PK
        long purchase_bill_id FK
        enum method
        double amount
        string reference
        datetime createdAt
    }

    SUPPLIER ||--o{ PRODUCT : supplies
    PRODUCT ||--o{ PRODUCT_BARCODE : has
    SALESMAN ||--o{ BILL : handles
    BILL ||--o{ BILL_ITEM : contains
    BILL ||--o{ BILL_PAYMENT : has
    SUPPLIER ||--o{ PURCHASE_BILL : receives
    PURCHASE_BILL ||--o{ PURCHASE_BILL_ITEM : contains
    PURCHASE_BILL ||--o{ PURCHASE_PAYMENT : has
    PRODUCT ||--o{ PURCHASE_BILL_ITEM : stocked_by
```

## ER Diagram Notes

- `tenantId` separates data by store/tenant. Tenant-owned tables are filtered using Hibernate `tenantFilter`.
- `USER.tenantId` is embedded into the JWT during login and drives tenant scoping for later requests.
- `SUPPLIER.supplierCode` is unique per tenant.
- `PRODUCT.barcode` is unique per tenant, and extra barcodes are stored in `PRODUCT_BARCODE`.
- `PRODUCT_BARCODE.quantityPerScan` supports cases like carton/barcode scans where one scan maps to multiple product units.
- `BILL_ITEM` stores product name and price details at sale time so old bills do not change when product master data changes later.
- `PURCHASE_BILL_ITEM` links directly to `PRODUCT`; creating a purchase increases product stock.
- `BILL` links to `SALESMAN` through `salesman_employee_id`; creating a sales bill reduces product stock.
- `CUSTOMER` is not directly linked by foreign key to `BILL`; customer records are created or updated from bill contact details.
- `BILL_PAYMENT` and `PURCHASE_PAYMENT` store payment history and due adjustments.

## Simplified Business ER View

Use this smaller version when explaining the application to non-technical users.

```mermaid
erDiagram
    SUPPLIER ||--o{ PRODUCT : supplies
    PRODUCT ||--o{ PRODUCT_BARCODE : has
    SUPPLIER ||--o{ PURCHASE_BILL : sends
    PURCHASE_BILL ||--o{ PURCHASE_BILL_ITEM : contains
    PRODUCT ||--o{ PURCHASE_BILL_ITEM : received
    PURCHASE_BILL ||--o{ PURCHASE_PAYMENT : paid_by
    SALESMAN ||--o{ BILL : creates
    BILL ||--o{ BILL_ITEM : contains
    BILL ||--o{ BILL_PAYMENT : paid_by
    PRODUCT ||..o{ BILL_ITEM : sold_as
    CUSTOMER ||..o{ BILL : captured_from

    SUPPLIER {
        string name
        string supplierCode
    }

    PRODUCT {
        string name
        string barcode
        double stockQuantity
        double sellingPrice
    }

    PRODUCT_BARCODE {
        string barcode
        double quantityPerScan
    }

    PURCHASE_BILL {
        string billNumber
        double totalAmount
        double dueAmount
    }

    PURCHASE_BILL_ITEM {
        string productName
        double quantity
    }

    PURCHASE_PAYMENT {
        enum method
        double amount
    }

    SALESMAN {
        string employeeId
        string name
    }

    CUSTOMER {
        string name
        string contactNumber
    }

    BILL {
        string customerName
        double totalAmount
        double dueAmount
    }

    BILL_ITEM {
        string productName
        double quantity
        double unitSellingPrice
    }

    BILL_PAYMENT {
        enum method
        double amount
    }
```

## API Map

```mermaid
flowchart TB
    Auth[Authentication<br/>/api/auth]
    Users[Users<br/>/api/users]
    Products[Products<br/>/api/products]
    Suppliers[Suppliers<br/>/api/suppliers]
    Salesman[Salesmen<br/>/api/salesman]
    Bills[Sales Bills<br/>/api/bills]
    Purchases[Purchases<br/>/api/purchases]
    Reports[Reports<br/>/api/reports]
    Dashboard[Dashboard<br/>/api/dashboard]

    Auth --> Login[POST /login]
    Auth --> ChangePassword[POST /change-password]
    Users --> AddUser[POST /adduser]
    Products --> ProductOps[Create, list, search,<br/>barcode lookup, barcode links]
    Suppliers --> SupplierOps[Create, list, search,<br/>update, active status]
    Salesman --> SalesmanOps[Create, list,<br/>active status]
    Bills --> BillOps[Create, list, register,<br/>summary, payment, PDF]
    Purchases --> PurchaseOps[Create, list, get,<br/>cancel, payment, labels, PDF]
    Reports --> ReportOps[Daily, monthly, range,<br/>sales, day-end]
    Dashboard --> Today[Today summary]
```

## End-To-End Business Flow

```mermaid
flowchart LR
    Login[Login]
    Setup[Create master data<br/>supplier, products, salesman]
    Purchase[Create purchase bill<br/>stock inward]
    Labels[Generate barcode labels]
    Sale[Create sales bill<br/>stock outward]
    Payment[Record payment or credit]
    Due[Collect customer due<br/>or supplier due]
    Reports[View dashboard<br/>and reports]

    Login --> Setup
    Setup --> Purchase
    Purchase --> Labels
    Purchase --> Sale
    Sale --> Payment
    Payment --> Due
    Due --> Reports
```

## Important Invariants

- Every tenant-owned entity is scoped using `tenantId`.
- `TenantInterceptor` extracts tenant information from the JWT and stores it in `TenantContext`.
- `TenantFilterAspect` enables the Hibernate `tenantFilter` before repository calls.
- Purchase transactions increase inventory stock.
- Sales bill transactions reduce inventory stock.
- Customer credit is represented through `CREDIT` bill payments.
- Customer due collection adds a positive payment and a negative `CREDIT` adjustment.
- Supplier purchase due cannot be paid using `CREDIT`.
- Reports and dashboards are calculated from tenant-scoped bill and payment data.
