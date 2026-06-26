# Audit Trail (BRD §4)

A cross-cutting audit trail: domain services publish events; an async listener writes
`audit_logs`. No inline audit code in controllers, and the write never blocks the request.

## Flow
```
service (login/logout/scan/skip/completion)
   └─ ApplicationEventPublisher.publishEvent(AuditEvent ...)        // one line, in the domain layer
        └─ AuditEventListener  @Async("auditExecutor") @TransactionalEventListener(AFTER_COMMIT)
             └─ AuditLogWriter.write(event)  → audit_logs (own REQUIRES_NEW tx)
```

- **AFTER_COMMIT**: audit fires only after the triggering transaction commits — rolled-back
  actions (e.g. a rejected scan that throws) are never audited.
- **@Async on `auditExecutor`** (a dedicated `ThreadPoolTaskExecutor`, see `config/AsyncConfig`):
  the write runs on a separate thread, off the request path.
- **REQUIRES_NEW** in `JpaAuditLogWriter`: the audit commit is fully independent.

## Events captured (`AuditAction`)
LOGIN, LOGOUT (from `AuthService`); SCAN, SKIP, ITEM_PICKED, ORDER_COMPLETED (from `ScanService`).
A completing scan emits SCAN (+ ITEM_PICKED) (+ ORDER_COMPLETED) as applicable.

## Fields written (exactly the BRD §4 set + action type)
`audit_logs`: `timestamp` (the action time, captured in the event — not the async write time),
`order_id` (internal order id), `sku`, `location`, `user_id`, and `action` (the `AuditAction` name).
Actor-only events (login/logout) leave order_id/sku/location null. No schema change was needed —
`audit_logs` already exists from V1.

## Key classes (`audit` package)
`AuditEvent` (record), `AuditAction` (enum), `AuditLog` (entity) + `AuditLogRepository`,
`AuditLogWriter` (seam) + `JpaAuditLogWriter`, `AuditEventListener`. Async enabled in
`config/AsyncConfig`.

## Non-blocking proof
`audit/AuditTrailAsyncTest` gates the audit write behind a latch the test holds (via a
`@Primary` test `AuditLogWriter`) and mints the picker token directly (no LOGIN noise). It asserts:
1. `POST /scan` returns in well under the gated write's timeout (elapsed < 5s vs a 20s gate) —
   it did not wait on the audit write;
2. at the moment the scan returned, **no audit row is committed** and the write latch is still closed;
3. after the test opens the gate, the audit row appears asynchronously with exactly the BRD
   fields (action=SCAN, order_id, sku, location, timestamp).

The test is not `@Transactional` (the scan must really commit for AFTER_COMMIT to fire) and
cleans up explicitly.

## Design notes / follow-ups
- **Why events, not AOP:** publishing is a single domain-layer line per action and keeps the
  write centralized in one listener — satisfies "cross-cutting, not scattered across controllers."
  An AOP aspect was the alternative; events are simpler here and decouple cleanly.
- **Rejected scans** (wrong-SKU 409) are not audited — they throw before publishing and the tx
  rolls back. If mis-scan auditing is wanted, publish a SCAN_REJECTED event on a separate
  (committing) path.
- **Durability:** an in-process async executor can drop events if the JVM dies between commit
  and audit write. For stronger guarantees, move to a transactional outbox or a broker. Acceptable
  for this stage; called out.
- `audit_logs.order_id` stores the internal id; join to `orders.order_number` for the external Order_ID.
