package com.sigep.application.controller

import com.sigep.application.config.CacheManagementService
import com.sigep.common.application.dto.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin/cache")
@Tag(name = "Cache Management", description = "Endpoints para gestionar el cache de la aplicación")
class CacheManagementController(
    private val cacheManagementService: CacheManagementService
) {

    @DeleteMapping("/clear")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Limpiar todo el cache",
        description = "Elimina todos los datos cacheados en Redis. Solo accesible para administradores."
    )
    fun clearAllCache(): ResponseEntity<ApiResponse<String>> {
        cacheManagementService.clearAllCache()
        return ResponseEntity.ok(
            ApiResponse(
                success = true,
                message = "Cache limpiado exitosamente",
                data = "Todos los datos del cache han sido eliminados"
            )
        )
    }
}

