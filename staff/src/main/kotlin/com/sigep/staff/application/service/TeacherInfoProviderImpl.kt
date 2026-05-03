package com.sigep.staff.application.service

import com.sigep.common.application.service.TeacherInfoProvider
import com.sigep.staff.infrastructure.repository.TeachingStaffRepository
import org.springframework.stereotype.Service

@Service
class TeacherInfoProviderImpl(
    private val teachingStaffRepository: TeachingStaffRepository
) : TeacherInfoProvider {

    override fun getTeacherNamesByIds(teacherIds: Collection<Long>): Map<Long, String> {
        if (teacherIds.isEmpty()) {
            return emptyMap()
        }

        return teachingStaffRepository.findAllByIdInAndIsActiveTrue(teacherIds)
            .associate { staff -> staff.id!! to staff.fullName }
    }

    override fun getTeacherNameById(teacherId: Long): String? {
        return teachingStaffRepository.findByIdAndIsActiveTrue(teacherId)?.fullName
    }
}

