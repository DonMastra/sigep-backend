-- Read-only verification after the teacher-account linkage repair.
SELECT
    staff.id AS staff_id,
    account.id AS account_id,
    account.username,
    account.role,
    account.status,
    account.active,
    account.must_change_password,
    count(course.id) AS assigned_courses
FROM teaching_staff staff
LEFT JOIN users account ON account.id = staff.linked_user_id
LEFT JOIN courses course ON course.teacher_id = account.id
WHERE staff.document_number LIKE 'LEGACY-%'
  AND staff.is_active = true
GROUP BY staff.id, account.id, account.username, account.role, account.status,
         account.active, account.must_change_password
ORDER BY account.username;

SELECT count(*) AS invalid_teacher_references
FROM courses course
LEFT JOIN users account ON account.id = course.teacher_id
LEFT JOIN teaching_staff staff ON staff.linked_user_id = account.id AND staff.is_active = true
WHERE course.teacher_id IS NOT NULL
  AND (
    account.id IS NULL OR staff.id IS NULL
    OR account.role NOT IN ('TEACHER', 'ADMIN')
    OR account.status <> 'ACTIVE' OR account.active = false
  );

SELECT account.username, count(course.id) AS assigned_courses
FROM users account
JOIN courses course ON course.teacher_id = account.id
WHERE lower(account.username) IN (
  'arosado','amastracchio','cescobar','mdiaz','denriquez',
  'gferreyra','haltamirano','omonzon','pcordova','rmainero'
)
GROUP BY account.username
ORDER BY account.username;
