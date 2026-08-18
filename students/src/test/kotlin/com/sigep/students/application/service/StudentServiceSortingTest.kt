package com.sigep.students.application.service

import com.sigep.common.application.service.EnrollmentServiceProvider
import com.sigep.security.domain.repository.UserRepository
import com.sigep.students.domain.model.Student
import com.sigep.students.domain.repository.StudentGuardianLinkEventRepository
import com.sigep.students.domain.repository.StudentRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import kotlin.test.assertEquals

class StudentServiceSortingTest {
    private val studentRepository = mockk<StudentRepository>()
    private val enrollmentServiceProvider = mockk<EnrollmentServiceProvider>()
    private val userRepository = mockk<UserRepository>()
    private val guardianLinkEventRepository = mockk<StudentGuardianLinkEventRepository>()
    private val identityNormalizer = mockk<StudentIdentityNormalizer>()
    private lateinit var service: StudentService

    @BeforeEach
    fun setUp() {
        service = StudentService(
            studentRepository,
            enrollmentServiceProvider,
            userRepository,
            guardianLinkEventRepository,
            identityNormalizer
        )
    }

    @Test
    fun `search applies requested order before pagination with stable tie breakers`() {
        val pageable = slot<Pageable>()
        every { studentRepository.searchStudents("ana", capture(pageable)) } returns Page.empty<Student>()

        service.searchStudents("ana", 1, 25, "lastName", "DESC")

        assertEquals(1, pageable.captured.pageNumber)
        assertEquals(25, pageable.captured.pageSize)
        assertEquals(Sort.Direction.DESC, pageable.captured.sort.getOrderFor("lastName")?.direction)
        assertEquals(Sort.Direction.DESC, pageable.captured.sort.getOrderFor("firstName")?.direction)
        assertEquals(Sort.Direction.ASC, pageable.captured.sort.getOrderFor("id")?.direction)
    }

    @Test
    fun `unknown sort field falls back to id instead of reaching the repository`() {
        val pageable = slot<Pageable>()
        every { studentRepository.findAll(capture(pageable)) } returns Page.empty<Student>()

        service.getAllStudents(0, 10, "unknownProperty", "DESC")

        assertEquals(listOf("id"), pageable.captured.sort.map { it.property }.toList())
        assertEquals(Sort.Direction.DESC, pageable.captured.sort.getOrderFor("id")?.direction)
    }
}
