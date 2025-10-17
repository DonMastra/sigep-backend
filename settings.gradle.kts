rootProject.name = "sigep-backend"

// Common module
include("common")

// Security module
include("security")

// Bounded Context modules
include("students")
include("courses")
include("scheduling")
include("payments")
include("exams")
include("communications")
include("reports")

// Main application module
include("application")
