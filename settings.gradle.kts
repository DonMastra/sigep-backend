rootProject.name = "sigep-backend"

// Common module
include("common")

// Security module
include("security")

// Bounded Context modules
include("students")
include("courses")
include("staff")
include("scheduling")
include("payments")
include("tuition")
include("guardians")
include("exams")
include("communications")
include("reports")

// Main application module
include("application")
