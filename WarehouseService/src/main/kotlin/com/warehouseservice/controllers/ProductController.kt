package com.warehouseservice.controllers

import com.warehouseservice.exceptions.ErrorResponse
import com.warehouseservice.models.dtos.CreateProductDTO
import com.warehouseservice.models.dtos.ProductDTO
import com.warehouseservice.models.dtos.ProductPageDTO
import com.warehouseservice.models.dtos.UpdateProductDTO
import com.warehouseservice.models.dtos.UpdateProductStatusDTO
import com.warehouseservice.services.ProductService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import java.util.UUID

@Tag(name = "Products", description = "Rest API for product management")
@RestController
@RequestMapping("/api/v1/products")
class ProductController(
    private val productService: ProductService
) {

    @Operation(summary = "Get filtered and paginated list of products", description = "Retrieve a paginated list of products with optional filtering parameters.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Ok - Successfully retrieved list of products",
            content = [Content(schema = Schema(implementation = ProductPageDTO::class))]
        )
    ])
    @GetMapping
    fun findAll(
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "20") size: Int,
        @RequestParam(required = false) name: String?,
        @RequestParam(required = false) barCode: String?,
        @RequestParam(required = false, defaultValue = "name-asc") sort: String?,
    ): ResponseEntity<ProductPageDTO> =
        ResponseEntity.ok(productService.findAll(page, size, name, barCode, sort))

    @Operation(summary = "Get a product")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Ok - Successfully retrieved product",
                content = [Content(schema = Schema(implementation = ProductDTO::class))],
            ),
            ApiResponse(responseCode = "404", description = "Not Found - Product with specified ID does not exist",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "ProductNotFoundException",
                        summary = "Product not found",
                        value = "{\"timestamp\":\"2026-03-31T19:00:00Z\",\"status\":404,\"error\":\"Not Found\",\"message\":\"Product with id 3fa85f64-5717-4562-b3fc-2c963f66afa6 not found\"}"
                    )]
                )],
            ),
        ],
    )
    @GetMapping("/{id}")
    fun findByID(@PathVariable id: UUID): ResponseEntity<ProductDTO> =
        ResponseEntity.ok(productService.findByID(id))

    @Operation(summary = "Create a product")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "Created - Successfully created product",
                content = [Content(schema = Schema(implementation = ProductDTO::class))],
            ),
            ApiResponse(responseCode = "409", description = "Conflict - Product with bar code already exists",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "ProductWithBarCodeAlreadyExistsException",
                        summary = "Product with bar code already exists",
                        value = "{\"timestamp\":\"2026-03-31T19:00:00Z\",\"status\":409,\"error\":\"Conflict\",\"message\":\"Product with bar code 8001120896247 already exists\"}"
                    )]
                )],
            ),
        ],
    )
    @PostMapping
    fun create(@Valid @RequestBody createProductDTO: CreateProductDTO): ResponseEntity<ProductDTO> =
        ResponseEntity.status(HttpStatus.CREATED).body(productService.create(createProductDTO))

    @Operation(summary = "Update a product")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Ok - Successfully updated product",
                content = [Content(schema = Schema(implementation = ProductDTO::class))],
            ),
            ApiResponse(responseCode = "409", description = "Conflict - Product with bar code already exists",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "ProductWithBarCodeAlreadyExistsException",
                        summary = "Product with bar code already exists",
                        value = "{\"timestamp\":\"2026-03-31T19:00:00Z\",\"status\":409,\"error\":\"Conflict\",\"message\":\"Product with bar code 8001120896247 already exists\"}"
                    )]
                )],
            ),
            ApiResponse(responseCode = "404", description = "Not Found - Product with specified ID does not exist",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "ProductNotFoundException",
                        summary = "Product not found",
                        value = "{\"timestamp\":\"2026-03-31T19:00:00Z\",\"status\":404,\"error\":\"Not Found\",\"message\":\"Product with id 3fa85f64-5717-4562-b3fc-2c963f66afa6 not found\"}"
                    )]
                )],
            ),
        ],
    )
    @PatchMapping("/{id}")
    fun update(@PathVariable id: UUID, @Valid @RequestBody updateProductDTO: UpdateProductDTO): ResponseEntity<ProductDTO> =
        ResponseEntity.ok(productService.update(id, updateProductDTO))

    @Operation(summary = "Update product status")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Ok - Successfully updated product status",
            content = [Content(schema = Schema(implementation = ProductDTO::class))],
        ),
        ApiResponse(responseCode = "404", description = "Not Found - Product not found",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    name = "ProductNotFoundException",
                    summary = "Product not found",
                    value = "{\"timestamp\":\"2026-03-31T19:00:00Z\",\"status\":404,\"error\":\"Not Found\",\"message\":\"Product with id 3fa85f64-5717-4562-b3fc-2c963f66afa6 not found\"}"
                )]
            )],
        ),
        ApiResponse(responseCode = "422", description = "Unprocessable Entity - Invalid status transition",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    name = "InvalidProductStatusException",
                    summary = "Invalid status",
                    value = "{\"timestamp\":\"2026-03-31T19:00:00Z\",\"status\":422,\"error\":\"Unprocessable Entity\",\"message\":\"assignedTo is required when status is ASSIGNED\"}"
                )]
            )],
        ),
    ])
    @PatchMapping("/{id}/status")
    fun updateStatus(@PathVariable id: UUID, @Valid @RequestBody dto: UpdateProductStatusDTO): ResponseEntity<ProductDTO> =
        ResponseEntity.ok(productService.updateStatus(id, dto))

    @Operation(summary = "Delete a product")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "No Content - Successfully deleted product",
                content = [Content(schema = Schema(implementation = ProductDTO::class))],
            ),
            ApiResponse(responseCode = "404", description = "Not Found - Product with specified ID does not exist",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class),
                    examples = [ExampleObject(
                        name = "ProductNotFoundException",
                        summary = "Product not found",
                        value = "{\"timestamp\":\"2026-03-31T19:00:00Z\",\"status\":404,\"error\":\"Not Found\",\"message\":\"Product with id 3fa85f64-5717-4562-b3fc-2c963f66afa6 not found\"}"
                    )]
                )],
            ),
        ],
    )
    @DeleteMapping("/{id}")
    fun deleteByID(@PathVariable id: UUID): ResponseEntity<Void> {
        productService.deleteByID(id)
        return ResponseEntity.noContent().build()
    }
}