package com.sigep.guardians.infrastructure.repository

import com.sigep.guardians.domain.model.GuardianBillingFilter
import com.sigep.guardians.domain.model.GuardianClientChargeReadModel
import com.sigep.guardians.domain.model.GuardianClientDetailReadModel
import com.sigep.guardians.domain.model.GuardianClientPaymentReadModel
import com.sigep.guardians.domain.model.GuardianClientSearchCriteria
import com.sigep.guardians.domain.model.GuardianClientStatsReadModel
import com.sigep.guardians.domain.model.GuardianClientStudentReadModel
import com.sigep.guardians.domain.model.GuardianClientSummaryReadModel
import com.sigep.guardians.domain.model.GuardianClientTuitionReadModel
import com.sigep.guardians.domain.model.GuardianRelationshipFilter
import com.sigep.guardians.domain.repository.GuardianClientReadRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.sql.ResultSet
import java.time.LocalDate
import java.time.LocalDateTime

@Repository
class JdbcGuardianClientReadRepository(
    private val jdbc: NamedParameterJdbcTemplate
) : GuardianClientReadRepository {

    override fun search(criteria: GuardianClientSearchCriteria): Page<GuardianClientSummaryReadModel> {
        val params = MapSqlParameterSource()
            .addValue("limit", criteria.size)
            .addValue("offset", criteria.page.toLong() * criteria.size)
        val where = buildWhere(criteria, params)
        val direction = if (criteria.order.equals("DESC", ignoreCase = true)) "DESC" else "ASC"
        val sort = SORT_COLUMNS[criteria.sort] ?: SORT_COLUMNS.getValue("lastName")
        val content = jdbc.query(
            "$BASE_QUERY SELECT * FROM guardian_base g $where ORDER BY $sort $direction, g.guardian_user_id ASC LIMIT :limit OFFSET :offset",
            params,
            summaryMapper
        )
        val total = jdbc.queryForObject(
            "$BASE_QUERY SELECT count(*) FROM guardian_base g $where",
            params,
            Long::class.java
        ) ?: 0L
        return PageImpl(content, PageRequest.of(criteria.page, criteria.size), total)
    }

    override fun getStats(): GuardianClientStatsReadModel = jdbc.queryForObject(
        """
        $BASE_QUERY
        SELECT
            count(*) total_clients,
            count(*) FILTER (WHERE student_count > 0) with_students,
            count(*) FILTER (WHERE student_count = 0) without_students,
            count(*) FILTER (WHERE billing_account_id IS NOT NULL) with_billing_account,
            count(*) FILTER (WHERE outstanding_amount > 0) with_open_debt,
            count(*) FILTER (WHERE missing_contact_data) missing_contact_data
        FROM guardian_base
        """.trimIndent(),
        emptyMap<String, Any>(),
        RowMapper { rs, _ ->
            GuardianClientStatsReadModel(
                totalClients = rs.getLong("total_clients"),
                withStudents = rs.getLong("with_students"),
                withoutStudents = rs.getLong("without_students"),
                withBillingAccount = rs.getLong("with_billing_account"),
                withOpenDebt = rs.getLong("with_open_debt"),
                missingContactData = rs.getLong("missing_contact_data")
            )
        }
    )!!

    override fun findDetail(guardianUserId: Long): GuardianClientDetailReadModel? = jdbc.query(
        "$BASE_QUERY SELECT * FROM guardian_base WHERE guardian_user_id = :guardianUserId",
        mapOf("guardianUserId" to guardianUserId)
    ) { rs, _ ->
        GuardianClientDetailReadModel(
            summary = mapSummary(rs),
            address = rs.getString("address"),
            dateOfBirth = rs.localDate("date_of_birth"),
            emergencyContact = rs.getString("emergency_contact"),
            administrativeNotes = rs.getString("administrative_notes"),
            updatedAt = rs.localDateTime("profile_updated_at")
        )
    }.firstOrNull()

    override fun findStudents(guardianUserId: Long): List<GuardianClientStudentReadModel> = jdbc.query(
        STUDENTS_QUERY,
        mapOf("guardianUserId" to guardianUserId)
    ) { rs, _ ->
        GuardianClientStudentReadModel(
            studentId = rs.getLong("student_id"),
            studentNumber = rs.getString("student_number"),
            firstName = rs.getString("first_name"),
            lastName = rs.getString("last_name"),
            active = rs.getBoolean("active"),
            currentLevel = rs.getString("current_level"),
            enrollmentId = rs.nullableLong("enrollment_id"),
            courseId = rs.nullableLong("course_id"),
            courseName = rs.getString("course_name"),
            enrollmentStatus = rs.getString("enrollment_status"),
            tuitionApplicationId = rs.nullableLong("tuition_application_id"),
            tuitionApplicationStatus = rs.getString("tuition_application_status"),
            openChargeCount = rs.getLong("open_charge_count"),
            outstandingAmount = rs.decimal("outstanding_amount")
        )
    }

    override fun findTuitionApplications(guardianUserId: Long): List<GuardianClientTuitionReadModel> = jdbc.query(
        TUITION_QUERY,
        mapOf("guardianUserId" to guardianUserId)
    ) { rs, _ ->
        GuardianClientTuitionReadModel(
            applicationId = rs.getLong("application_id"),
            studentId = rs.nullableLong("student_id"),
            studentName = rs.getString("student_name"),
            applicationType = rs.getString("application_type"),
            status = rs.getString("status"),
            origin = rs.getString("origin"),
            submittedAt = rs.localDateTime("submitted_at")!!,
            enrollmentId = rs.nullableLong("enrollment_id"),
            assignedCourseId = rs.nullableLong("assigned_course_id"),
            assignedCourseName = rs.getString("assigned_course_name")
        )
    }

    override fun findCharges(guardianUserId: Long): List<GuardianClientChargeReadModel> = jdbc.query(
        CHARGES_QUERY,
        mapOf("guardianUserId" to guardianUserId)
    ) { rs, _ ->
        GuardianClientChargeReadModel(
            chargeId = rs.getLong("charge_id"),
            studentId = rs.nullableLong("student_id"),
            studentName = rs.getString("student_name"),
            concept = rs.getString("concept"),
            description = rs.getString("description"),
            amount = rs.decimal("amount"),
            paidAmount = rs.decimal("paid_amount"),
            outstandingAmount = rs.decimal("outstanding_amount"),
            currency = rs.getString("currency"),
            dueDate = rs.localDate("due_date")!!,
            status = rs.getString("status"),
            overdue = rs.getBoolean("overdue"),
            fiscalDisposition = rs.getString("fiscal_disposition"),
            fiscalInvoiceId = rs.nullableLong("fiscal_invoice_id"),
            fiscalInvoiceStatus = rs.getString("fiscal_invoice_status")
        )
    }

    override fun findPayments(guardianUserId: Long): List<GuardianClientPaymentReadModel> = jdbc.query(
        PAYMENTS_QUERY,
        mapOf("guardianUserId" to guardianUserId)
    ) { rs, _ ->
        GuardianClientPaymentReadModel(
            paymentId = rs.getLong("payment_id"),
            paymentDate = rs.localDate("payment_date"),
            amount = rs.decimal("amount"),
            allocatedAmount = rs.decimal("allocated_amount"),
            currency = rs.getString("currency"),
            status = rs.getString("status"),
            paymentMethod = rs.getString("payment_method"),
            receiptId = rs.nullableLong("receipt_id"),
            receiptNumber = rs.getString("receipt_number"),
            invoiceId = rs.nullableLong("invoice_id"),
            invoiceStatus = rs.getString("invoice_status")
        )
    }

    override fun existsGuardian(guardianUserId: Long): Boolean = jdbc.queryForObject(
        """
        SELECT EXISTS(
            SELECT 1
            FROM users u
            WHERE u.id = :guardianUserId
              AND (
                  EXISTS (
                      SELECT 1
                      FROM user_role_assignments ura
                      WHERE ura.user_id = u.id
                        AND ura.role = 'GUARDIAN'
                        AND ura.revoked_at IS NULL
                  )
                  OR (
                      u.role = 'GUARDIAN'
                      AND NOT EXISTS (
                          SELECT 1 FROM user_role_assignments any_role WHERE any_role.user_id = u.id
                      )
                  )
              )
        )
        """.trimIndent(),
        mapOf("guardianUserId" to guardianUserId),
        Boolean::class.java
    ) == true

    private fun buildWhere(criteria: GuardianClientSearchCriteria, params: MapSqlParameterSource): String {
        val filters = mutableListOf<String>()
        criteria.search?.takeIf { it.isNotBlank() }?.let {
            params.addValue("search", "%${it.trim().lowercase()}%")
            filters += """
                (lower(g.client_number) LIKE :search
                 OR lower(g.first_name) LIKE :search
                 OR lower(g.last_name) LIKE :search
                 OR lower(concat(g.first_name, ' ', g.last_name)) LIKE :search
                 OR lower(g.email) LIKE :search
                 OR lower(coalesce(g.document_number, '')) LIKE :search
                 OR EXISTS (
                     SELECT 1
                     FROM students sx
                     JOIN student_guardian_relationships sgrx ON sgrx.student_id = sx.id
                     WHERE sgrx.guardian_user_id = g.guardian_user_id
                       AND sgrx.active
                       AND sgrx.can_view_academic
                       AND (lower(sx.student_number) LIKE :search
                            OR lower(sx.first_name) LIKE :search
                            OR lower(sx.last_name) LIKE :search
                            OR lower(concat(sx.first_name, ' ', sx.last_name)) LIKE :search
                            OR lower(coalesce(sx.document_number, '')) LIKE :search)
                 ))
            """.trimIndent()
        }
        criteria.accountStatus?.let {
            params.addValue("accountStatus", it)
            filters += "g.account_status = :accountStatus"
        }
        when (criteria.relationship) {
            GuardianRelationshipFilter.WITH_STUDENTS -> filters += "g.student_count > 0"
            GuardianRelationshipFilter.WITHOUT_STUDENTS -> filters += "g.student_count = 0"
            null -> Unit
        }
        when (criteria.billing) {
            GuardianBillingFilter.WITH_DEBT -> filters += "g.outstanding_amount > 0"
            GuardianBillingFilter.NO_DEBT -> filters += "g.billing_account_id IS NOT NULL AND g.outstanding_amount = 0"
            GuardianBillingFilter.NO_ACCOUNT -> filters += "g.billing_account_id IS NULL"
            null -> Unit
        }
        return if (filters.isEmpty()) "" else "WHERE ${filters.joinToString(" AND ")}"
    }

    private val summaryMapper = RowMapper<GuardianClientSummaryReadModel> { rs, _ -> mapSummary(rs) }

    private fun mapSummary(rs: ResultSet) = GuardianClientSummaryReadModel(
        guardianUserId = rs.getLong("guardian_user_id"),
        clientNumber = rs.getString("client_number"),
        firstName = rs.getString("first_name"),
        lastName = rs.getString("last_name"),
        email = rs.getString("email"),
        phoneNumber = rs.getString("phone_number"),
        documentNumber = rs.getString("document_number"),
        accountStatus = rs.getString("account_status"),
        accountActive = rs.getBoolean("account_active"),
        preferredContactChannel = rs.getString("preferred_contact_channel"),
        studentCount = rs.getLong("student_count"),
        activeStudentCount = rs.getLong("active_student_count"),
        activeEnrollmentCount = rs.getLong("active_enrollment_count"),
        tuitionApplicationCount = rs.getLong("tuition_application_count"),
        billingAccountId = rs.nullableLong("billing_account_id"),
        billingAccountStatus = rs.getString("billing_account_status"),
        billingProfileStatus = rs.getString("billing_profile_status"),
        openChargeCount = rs.getLong("open_charge_count"),
        overdueChargeCount = rs.getLong("overdue_charge_count"),
        outstandingAmount = rs.decimal("outstanding_amount"),
        lastPaymentDate = rs.localDate("last_payment_date"),
        missingContactData = rs.getBoolean("missing_contact_data"),
        profileVersion = rs.getLong("profile_version")
    )

    private fun ResultSet.nullableLong(column: String): Long? = (getObject(column) as? Number)?.toLong()
    private fun ResultSet.decimal(column: String): BigDecimal = getBigDecimal(column) ?: BigDecimal.ZERO
    private fun ResultSet.localDate(column: String): LocalDate? = getObject(column, LocalDate::class.java)
    private fun ResultSet.localDateTime(column: String): LocalDateTime? = getObject(column, LocalDateTime::class.java)

    companion object {
        private val SORT_COLUMNS = mapOf(
            "clientNumber" to "g.client_number",
            "firstName" to "g.first_name",
            "lastName" to "g.last_name",
            "studentCount" to "g.student_count",
            "outstandingAmount" to "g.outstanding_amount",
            "accountStatus" to "g.account_status"
        )

        private val BASE_QUERY = """
            WITH student_summary AS (
                SELECT sgr.guardian_user_id,
                       count(DISTINCT s.id) student_count,
                       count(DISTINCT s.id) FILTER (WHERE s.active) active_student_count,
                       count(DISTINCT e.id) FILTER (WHERE e.status = 'ACTIVE') active_enrollment_count
                FROM student_guardian_relationships sgr
                JOIN students s ON s.id = sgr.student_id
                LEFT JOIN enrollments e ON e.student_id = s.id
                WHERE sgr.active AND sgr.can_view_academic
                GROUP BY sgr.guardian_user_id
            ), tuition_summary AS (
                SELECT guardian_user_id, count(*) tuition_application_count
                FROM tuition_applications
                GROUP BY guardian_user_id
            ), billing_summary AS (
                SELECT ba.guardian_user_id,
                       ba.id billing_account_id,
                       ba.status billing_account_status,
                       bp.status billing_profile_status,
                       count(bc.id) FILTER (WHERE bc.status IN ('OPEN', 'PARTIALLY_PAID')) open_charge_count,
                       count(bc.id) FILTER (WHERE bc.status IN ('OPEN', 'PARTIALLY_PAID') AND bc.due_date < current_date) overdue_charge_count,
                       coalesce(sum(CASE WHEN bc.status IN ('OPEN', 'PARTIALLY_PAID') THEN bc.amount - bc.paid_amount ELSE 0 END), 0) outstanding_amount
                FROM billing_accounts ba
                LEFT JOIN billing_profiles bp ON bp.account_id = ba.id
                LEFT JOIN billing_charges bc ON bc.account_id = ba.id
                GROUP BY ba.guardian_user_id, ba.id, ba.status, bp.status
            ), payment_summary AS (
                SELECT ba.guardian_user_id,
                       max(coalesce(p.payment_date, p.confirmed_at::date, p.created_at::date)) last_payment_date
                FROM billing_accounts ba
                JOIN billing_charges bc ON bc.account_id = ba.id
                JOIN payment_allocations pa ON pa.charge_id = bc.id
                JOIN payments p ON p.id = pa.payment_id
                GROUP BY ba.guardian_user_id
            ), guardian_base AS (
                SELECT u.id guardian_user_id,
                       coalesce(gcp.client_number, 'CLI-' || lpad(u.id::text, 12, '0')) client_number,
                       u.first_name, u.last_name, u.email, u.phone_number, u.document_number,
                       u.status account_status, u.active account_active,
                       coalesce(gcp.preferred_contact_channel, 'EMAIL') preferred_contact_channel,
                       coalesce(ss.student_count, 0) student_count,
                       coalesce(ss.active_student_count, 0) active_student_count,
                       coalesce(ss.active_enrollment_count, 0) active_enrollment_count,
                       coalesce(ts.tuition_application_count, 0) tuition_application_count,
                       bs.billing_account_id, bs.billing_account_status, bs.billing_profile_status,
                       coalesce(bs.open_charge_count, 0) open_charge_count,
                       coalesce(bs.overdue_charge_count, 0) overdue_charge_count,
                       coalesce(bs.outstanding_amount, 0) outstanding_amount,
                       ps.last_payment_date,
                       (coalesce(btrim(u.phone_number), '') = '' OR coalesce(btrim(u.address), '') = '' OR coalesce(btrim(u.document_number), '') = '') missing_contact_data,
                       coalesce(gcp.version, 0) profile_version,
                       u.address, u.date_of_birth, u.emergency_contact,
                       gcp.administrative_notes, gcp.updated_at profile_updated_at
                FROM users u
                LEFT JOIN guardian_client_profiles gcp ON gcp.guardian_user_id = u.id
                LEFT JOIN student_summary ss ON ss.guardian_user_id = u.id
                LEFT JOIN tuition_summary ts ON ts.guardian_user_id = u.id
                LEFT JOIN billing_summary bs ON bs.guardian_user_id = u.id
                LEFT JOIN payment_summary ps ON ps.guardian_user_id = u.id
                WHERE EXISTS (
                    SELECT 1
                    FROM user_role_assignments ura
                    WHERE ura.user_id = u.id
                      AND ura.role = 'GUARDIAN'
                      AND ura.revoked_at IS NULL
                )
                   OR (
                       u.role = 'GUARDIAN'
                       AND NOT EXISTS (
                           SELECT 1 FROM user_role_assignments any_role WHERE any_role.user_id = u.id
                       )
                   )
            )
        """.trimIndent()

        private val STUDENTS_QUERY = """
            SELECT s.id student_id, s.student_number, s.first_name, s.last_name, s.active, s.current_level,
                   ce.enrollment_id, ce.course_id, ce.course_name, ce.enrollment_status,
                   ta.tuition_application_id, ta.tuition_application_status,
                   coalesce(cs.open_charge_count, 0) open_charge_count,
                   coalesce(cs.outstanding_amount, 0) outstanding_amount
            FROM students s
            LEFT JOIN LATERAL (
                SELECT e.id enrollment_id, c.id course_id, c.name course_name, e.status enrollment_status
                FROM enrollments e JOIN courses c ON c.id = e.course_id
                WHERE e.student_id = s.id AND e.status = 'ACTIVE'
                ORDER BY e.enrollment_date DESC, e.id DESC LIMIT 1
            ) ce ON true
            LEFT JOIN LATERAL (
                SELECT a.id tuition_application_id, a.status tuition_application_status
                FROM tuition_applications a
                WHERE a.student_id = s.id AND a.guardian_user_id = :guardianUserId
                ORDER BY a.created_at DESC, a.id DESC LIMIT 1
            ) ta ON true
            LEFT JOIN LATERAL (
                SELECT count(*) FILTER (WHERE bc.status IN ('OPEN', 'PARTIALLY_PAID')) open_charge_count,
                       coalesce(sum(CASE WHEN bc.status IN ('OPEN', 'PARTIALLY_PAID') THEN bc.amount - bc.paid_amount ELSE 0 END), 0) outstanding_amount
                FROM billing_charges bc JOIN billing_accounts ba ON ba.id = bc.account_id
                WHERE bc.student_id = s.id AND ba.guardian_user_id = :guardianUserId
            ) cs ON true
            WHERE EXISTS (
                SELECT 1
                FROM student_guardian_relationships sgr
                WHERE sgr.student_id = s.id
                  AND sgr.guardian_user_id = :guardianUserId
                  AND sgr.active
                  AND sgr.can_view_academic
            )
            ORDER BY s.last_name, s.first_name, s.id
        """.trimIndent()

        private val TUITION_QUERY = """
            SELECT a.id application_id, a.student_id,
                   coalesce(
                       nullif(btrim(concat_ws(' ', s.first_name, s.last_name)), ''),
                       nullif(btrim(concat_ws(' ', a.student_first_name, a.student_last_name)), ''),
                       'Sin estudiante resuelto'
                   ) student_name,
                   a.application_type, a.status, a.origin, a.submitted_at, a.enrollment_id,
                   a.assigned_course_id, c.name assigned_course_name
            FROM tuition_applications a
            LEFT JOIN students s ON s.id = a.student_id
            LEFT JOIN courses c ON c.id = a.assigned_course_id
            WHERE a.guardian_user_id = :guardianUserId
            ORDER BY a.submitted_at DESC, a.id DESC
        """.trimIndent()

        private val CHARGES_QUERY = """
            SELECT bc.id charge_id, bc.student_id, bc.student_name, bc.concept, bc.description,
                   bc.amount, bc.paid_amount, greatest(bc.amount - bc.paid_amount, 0) outstanding_amount,
                   bc.currency, bc.due_date, bc.status,
                   (bc.status IN ('OPEN', 'PARTIALLY_PAID') AND bc.due_date < current_date) overdue,
                   bc.fiscal_disposition,
                   fi.id fiscal_invoice_id, fi.status fiscal_invoice_status
            FROM billing_charges bc
            JOIN billing_accounts ba ON ba.id = bc.account_id
            LEFT JOIN fiscal_invoices fi ON fi.charge_id = bc.id
            WHERE ba.guardian_user_id = :guardianUserId
            ORDER BY bc.due_date DESC, bc.id DESC
            LIMIT 200
        """.trimIndent()

        private val PAYMENTS_QUERY = """
            SELECT p.id payment_id,
                   coalesce(p.payment_date, p.confirmed_at::date, p.created_at::date) payment_date,
                   p.amount, sum(pa.amount) allocated_amount, p.currency, p.status, p.payment_method,
                   pr.id receipt_id, pr.receipt_number,
                   fi.id invoice_id, fi.status invoice_status
            FROM billing_accounts ba
            JOIN billing_charges bc ON bc.account_id = ba.id
            JOIN payment_allocations pa ON pa.charge_id = bc.id
            JOIN payments p ON p.id = pa.payment_id
            LEFT JOIN payment_receipts pr ON pr.payment_id = p.id
            LEFT JOIN fiscal_invoices fi ON fi.payment_id = p.id
            WHERE ba.guardian_user_id = :guardianUserId
            GROUP BY p.id, p.payment_date, p.confirmed_at, p.created_at, p.amount, p.currency, p.status, p.payment_method,
                     pr.id, pr.receipt_number, fi.id, fi.status
            ORDER BY payment_date DESC, p.id DESC
            LIMIT 200
        """.trimIndent()
    }
}
