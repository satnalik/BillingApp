# Cashier shifts and cash reconciliation

## What the Reports review found

`Reports.jsx` previously contained a Day End Closing screen with a counted-cash field, an in-browser variance calculation and a close button. The backend implemented only `GET /api/reports/day-end`, not the `POST /api/reports/day-end/close` called by that screen. Its cashier filter actually selected `salesmen` from the backend response. There were no persisted shifts, opening floats, cash movements, stable cashier user IDs on payments, or saved reconciliation records. Expected cash was simply daily cash collections.

The report tab is now named **Cashier Shift Reconciliation** and reviews the actual saved shift history. It links to the existing **Cashier shifts** screen for opening shifts, cash movements and closing. Existing sales summary and bill register reports remain available, and the legacy daily collections API remains available. Managers can now access Reports, matching backend permissions.

## Store workflow

1. Sign in and open **Cashier shifts** in the sidebar.
2. Select **Open my shift**. Enter the cash physically present in the drawer, including zero, and optionally a counter label.
3. Create bills, collect customer dues and process sales returns/cancellations normally. These operations require an open shift and assign their new payment/refund entries to the logged-in operator's current shift.
4. Record **Cash in / out** for movements outside customer payments: top-ups, cash expenses, supplier payments from the drawer, supplier refunds put into the drawer, bank deposits and other removals. Enter a reason and optionally a reference.
5. Select **Count & close shift**. Count all physical cash before removal or handover. Enter the count and explain any shortage/overage. Confirm to save and freeze the reconciliation.
6. Export CSV or **Print / Save PDF** for the complete selected shift, including opening, mode totals, movements, closing count, variance and notes.

The new-bill screen shows shift status and a link that opens shift setup in another tab so the current bill/cart stays on screen. Status refreshes on window focus and periodically. The backend enforces shift requirements even if another tab closes the shift after the status check.

Logging out, closing a browser tab or restarting the application does not close the shift. An open shift can continue across midnight. It is independent of the calendar day and the selected salesman.

## Reconciliation

**Expected cash = opening cash + CASH collections − CASH refunds + manual cash in − manual cash out.**

**Variance = counted physical cash − expected cash.** Positive means over, negative means short.

Example: opening ₹500, cash collected ₹1,000, cash refunded ₹100 and cash out ₹200 gives expected ₹1,200. A count of ₹1,180 saves a ₹20 shortage and requires an explanation.

- UPI and card collections/refunds appear separately and do not contribute to physical cash.
- CREDIT is an unpaid amount, so it does not count as collected money. Credit adjustment entries are also excluded from physical cash.
- Due collections on an old invoice belong to the shift that receives that payment. Returns/cancellations of older bills put new refund entries in the shift performing the refund, even when the invoice belongs to a closed shift.
- Cancelled invoices' payment records remain in the shift ledger. Their later negative refund entries balance them; invoice status does not erase collections from historical cash totals.
- Repeated partial refunds are capped by the remaining net refundable amount for each payment mode, after subtracting earlier refunds.
- Customer refund entries follow the application's existing automatic return/cancellation settlement logic. Physically pay the recorded refund through its recorded mode; this feature does not add a deferred customer-refund workflow.
- Supplier purchase payments/refunds are not automatically taken from a cashier's drawer. Record the matching manual cash movement only if money physically leaves/enters that drawer. Do not duplicate automatically captured customer collections or refunds.
- Opening cash is entered each time; previous closing cash is not carried automatically. Count before removing/handover of cash to avoid recording the same removal twice.
- A negative expected cash balance indicates an unrecorded cash funding movement or inconsistent recorded cash flow; review and record actual funding before closing.

## Access and persistence

- Cashiers see and operate their own shifts.
- Managers/admins see all tenant shifts through **Reports → Cashier Shift Reconciliation** or the **All cashiers** history option. From the **Cashier shifts** operational screen they can close another cashier's shift using the confirmed physical count and a required explanation. Cash in/out remains restricted to the shift owner.
- One cashier account can have only one open shift. The label identifies the counter; it does not create a shared multi-cashier drawer. Use one responsible cashier account per physical drawer, or close and hand over before changing operators.
- Closing saves method totals, expected/count cash, variance, time, closing operator and notes. Closed shifts have no edit/reopen API. Start a new shift for later operations.
- Mutations take the cashier account lock before the shift and bill/product locks. Closing and payment posting are serialized. If collections or movements changed while counting, the server rejects the stale close and asks for a refresh/review/recount.
- UUID request keys make identical retries of opening, cash movements and closing safe. Reusing a key with changed details is rejected.
- History filters use the shift's opening date, inclusive. Each shift can be opened separately multiple times a day after the prior shift closes.
- Historical payments created before this feature have null shift IDs and are not attributed by cashier display name or date. Opening cash should match the physical drawer when tracking begins.

## API

- `GET /api/shifts/status`: lightweight current-shift status for billing.
- `GET /api/shifts/workspace`: own active reconciliation and review permissions.
- `POST /api/shifts`: `{requestKey, openingCash, counterName}`.
- `GET /api/shifts?all=true&from=2026-10-01&to=2026-10-31&cashierUserId=ankit&status=CLOSED&page=0&size=25` (all requires manager/admin).
- `GET /api/shifts/{id}`: own shift, or any current-tenant shift for managers/admins.
- `POST /api/shifts/{id}/cash-movements`: `{requestKey, cashIn, amount, reason, reference}`.
- `POST /api/shifts/{id}/close`: `{requestKey, countedCash, expectedRevision, notes}`. Use `revision` from the latest detail response.

Amounts accept at most two decimal places. Count must be entered explicitly, including zero. Cash movement direction and reason are required. Nonzero variance and another-cashier close require notes. Stale/closed/missing shift conditions return HTTP 409; ownership violations return HTTP 403.

## Updating an installation

Restart the backend before using the updated frontend. Local, QA and desktop profiles use `ddl-auto=update`, which adds the new tables, columns, unique constraints and payment index automatically. Production (`ddl-auto=validate`) must apply [the migration](migrations/20261008-cashier-shifts.sql) before restart. No customer database was modified during implementation.

The existing executable has not been rebuilt and does not contain these new screens. Rebuild it only when requested.

Implementation checks use Java compilation, frontend production build and targeted lint. Live database/browser workflows and automated tests were not run. `NewBill.jsx` has pre-existing lint findings outside the two lines changed for the shift banner; the new shift files and other modified report/navigation files are linted separately.
