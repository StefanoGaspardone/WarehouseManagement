package com.warehouseservice.models.dtos

import com.warehouseservice.models.enums.ProductStatus
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

@Schema(description = "DTO representing product details")
data class ProductDTO(
    @field:Schema(description = "Unique identifier of the product", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    val id: UUID?,

    @field:Schema(description = "Name of the product", example = "Product 1")
    val name: String,

    @field:Schema(description = "Unique bar code of the product", example = "8001120896247")
    val barCode: String,

    @field:Schema(description = "Status of the product", example = "IN_WAREHOUSE")
    val status: ProductStatus,

    @field:Schema(description = "Name of the employee the product is assigned to", example = "Mario Rossi")
    val assignedTo: String?,

    @field:Schema(description = "Creation timestamp", type = "string", format = "date-time", example = "2026-03-31T19:00:00Z")
    val createdAt: Instant?,

    @field:Schema(description = "Last update timestamp", type = "string", format = "date-time", example = "2026-03-31T19:00:00Z")
    val updatedAt: Instant?,
)

@Schema(description = "DTO for creating a product")
data class CreateProductDTO(
    @field:NotBlank
    @field:Size(max = 255)
    @field:Schema(description = "Name of the product", example = "Product 1")
    val name: String,

    @field:NotBlank
    @field:Pattern(
        regexp = "^[0-9]{8,13}$",
        message = "The bar code must contains between 8 e 13 digits"
    )
    @field:Schema(description = "Unique bar code of the product", example = "8001120896247")
    val barCode: String
)

@Schema(description = "DTO for updating a product")
data class UpdateProductDTO(
    @field:NotBlank
    @field:Size(max = 255)
    @field:Schema(description = "Name of the product", example = "Product 1")
    val name: String,

    @field:NotBlank
    @field:Pattern(
        regexp = "^[0-9]{8,13}$",
        message = "The bar code must contains between 8 e 13 digits"
    )
    @field:Schema(description = "Unique bar code of the product", example = "8001120896247")
    val barCode: String
)

@Schema(description = "DTO for updating product status")
data class UpdateProductStatusDTO(
    @field:NotNull
    @field:Schema(description = "New status of the product", example = "ASSIGNED")
    var status: ProductStatus,

    @field:Size(max = 255)
    @field:Schema(description = "Name of the employee the product is assigned to", example = "Mario Rossi")
    val assignedTo: String? = null,
)

@Schema(description = "Sort metadata used in pageable responses")
data class SortInfo(
    val empty: Boolean,
    val sorted: Boolean,
    val unsorted: Boolean,
)

@Schema(description = "Pageable metadata")
data class PageableInfo(
    val pageNumber: Int,
    val pageSize: Int,
    val sort: SortInfo,
    val offset: Long,
    val paged: Boolean,
    val unpaged: Boolean,
)

@Schema(description = "Paged response for products")
data class ProductPageDTO(
    val content: List<ProductDTO>,
    val pageable: PageableInfo,
    val totalElements: Long,
    val totalPages: Int,
    val last: Boolean,
    val size: Int,
    val number: Int,
    val sort: SortInfo,
    val first: Boolean,
    val numberOfElements: Int,
    val empty: Boolean,
)