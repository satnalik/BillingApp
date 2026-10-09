package com.pahal.billingApp.repository;
import com.pahal.billingApp.entity.GstDocument;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.*;
public interface GstDocumentRepository extends JpaRepository<GstDocument, Long> {
    @EntityGraph(attributePaths = "lines")
    @Query("select d from GstDocument d where d.tenantId = :tenant and ((d.documentDate >= :from and d.documentDate <= :to) or (d.supplierNoteDate >= :from and d.supplierNoteDate <= :to)) order by d.documentDate, d.id")
    List<GstDocument> findByTenantIdAndDocumentDateBetweenOrderByDocumentDateAscIdAsc(@Param("tenant") String tenant, @Param("from") LocalDate from, @Param("to") LocalDate to);
    @EntityGraph(attributePaths = "lines")
    Optional<GstDocument> findByTenantIdAndSourceKey(String tenant, String key);
    @EntityGraph(attributePaths = "lines")
    Optional<GstDocument> findByIdAndTenantId(Long id, String tenant);
    boolean existsByTenantId(String tenant);
    boolean existsByTenantIdAndRegistrationMode(String tenant, String mode);
    boolean existsByTenantIdAndDocumentTypeAndPartyGstinAndDocumentNumberAndDocumentDateBetween(
            String tenant, String type, String gstin, String number, LocalDate from, LocalDate to);
    @Query("select count(d) from GstDocument d where d.tenantId = :tenant and d.documentType = :type and d.documentDate >= :from and d.documentDate <= :to")
    long countSeries(@Param("tenant") String tenant, @Param("type") String type, @Param("from") LocalDate from, @Param("to") LocalDate to);
    @EntityGraph(attributePaths = "lines")
    List<GstDocument> findByTenantIdAndOriginalDocumentId(String tenant, Long originalId);
}
