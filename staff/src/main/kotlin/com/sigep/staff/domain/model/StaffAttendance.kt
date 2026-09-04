package com.sigep.staff.domain.model

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

@Entity
@Table(
    name = "staff_attendance",
    indexes = [
        //Index(name = "idx_attendance_date", columnList = "attendance_date"),
        Index(name = "idx_teaching_staff_id", columnList = "teaching_staff_id"),
        Index(name = "idx_non_teaching_staff_id", columnList = "non_teaching_staff_id")
    ]
)
data class StaffAttendance(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teaching_staff_id")
    val teachingStaff: TeachingStaff? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "non_teaching_staff_id")
    val nonTeachingStaff: NonTeachingStaff? = null,

    @Column(name = "attendance_date", nullable = false)
    val attendanceDate: LocalDate,

    @Column(name = "check_in_time")
    val checkInTime: LocalTime? = null,

    @Column(name = "check_out_time")
    val checkOutTime: LocalTime? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: AttendanceStatus,

    @Column(columnDefinition = "TEXT")
    val notes: String? = null,

    @Column(name = "hours_worked")
    val hoursWorked: Double? = null,

    @Column(name = "hourly_rate_snapshot", precision = 19, scale = 2)
    val hourlyRateSnapshot: BigDecimal? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "currency_snapshot", length = 3)
    val currencySnapshot: StaffCurrency? = null
) {
    init {
        require(teachingStaff != null || nonTeachingStaff != null) {
            "Either teachingStaff or nonTeachingStaff must be set"
        }
        require(!(teachingStaff != null && nonTeachingStaff != null)) {
            "Cannot set both teachingStaff and nonTeachingStaff"
        }
    }
}

enum class AttendanceStatus {
    PRESENT,
    ABSENT,
    LATE,
    EXCUSED,
    SICK_LEAVE,
    VACATION
}

