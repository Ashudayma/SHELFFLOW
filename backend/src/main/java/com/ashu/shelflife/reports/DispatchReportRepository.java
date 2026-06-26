package com.ashu.shelflife.reports;

import com.ashu.shelflife.reports.dto.ReportRow;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * Read-only query for the BRD 3.4 Dispatch Report. Joins order_items → orders → warehouses and
 * projects exactly the report fields (Warehouse_ID is the warehouse business code).
 *
 * <p>The JPQL is built dynamically so that each optional filter is omitted when not supplied.
 * (A static {@code :param is null} guard fails on PostgreSQL — it cannot infer the type of a
 * NULL bind parameter, "could not determine data type of parameter".)
 */
@Repository
public class DispatchReportRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public List<ReportRow> findReportRows(OffsetDateTime startDate, OffsetDateTime endDate,
                                          String warehouseCode, Long pickerId) {
        StringBuilder jpql = new StringBuilder("""
                select new com.ashu.shelflife.reports.dto.ReportRow(
                    o.orderNumber, o.pickerId, w.warehouseCode,
                    oi.sku, oi.itemName, oi.orderedQuantity, oi.pickedQuantity)
                from OrderItem oi
                    join oi.order o, Warehouse w
                where w.id = o.warehouseId
                """);
        if (startDate != null) {
            jpql.append(" and o.createdAt >= :startDate and o.createdAt < :endDate");
        }
        if (warehouseCode != null) {
            jpql.append(" and w.warehouseCode = :warehouse");
        }
        if (pickerId != null) {
            jpql.append(" and o.pickerId = :picker");
        }
        jpql.append(" order by o.orderNumber, oi.sku");

        TypedQuery<ReportRow> query = entityManager.createQuery(jpql.toString(), ReportRow.class);
        if (startDate != null) {
            query.setParameter("startDate", startDate);
            query.setParameter("endDate", endDate);
        }
        if (warehouseCode != null) {
            query.setParameter("warehouse", warehouseCode);
        }
        if (pickerId != null) {
            query.setParameter("picker", pickerId);
        }
        return query.getResultList();
    }
}
