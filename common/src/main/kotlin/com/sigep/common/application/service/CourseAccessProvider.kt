package com.sigep.common.application.service

interface CourseAccessProvider {
    fun getCourseInfo(courseId: Long): CourseAccessInfo?

    fun getCourseInfo(courseIds: Collection<Long>): Map<Long, CourseAccessInfo>

    fun getCourseIdsAssignedToTeacher(teacherUserId: Long): Set<Long>
    fun getActiveStudentIds(courseId: Long): Set<Long>

    fun isTeacherAssignedToCourse(courseId: Long, teacherUserId: Long): Boolean =
        courseId in getCourseIdsAssignedToTeacher(teacherUserId)
}

data class CourseAccessInfo(
    val id: Long,
    val code: String,
    val name: String,
    val teacherUserId: Long?
)
