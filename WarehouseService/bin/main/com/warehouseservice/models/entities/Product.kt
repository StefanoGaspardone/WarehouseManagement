package com.warehouseservice.models.entities

import com.warehouseservice.models.dtos.ProductDTO
import com.warehouseservice.models.enums.ProductStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "products")
class Product(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @field:NotBlank
    @field:Size(max = 255)
    @Column(name = "name", nullable = false, length = 255)
    var name: String,

    @field:NotBlank
    @field:Pattern(
        regexp = "^[0-9]{8,13}$",
        message = "The bar code must contains between 8 e 13 digits"
    )
    @Column(name = "bar_code", nullable = false, unique = true, length = 13)
    var barCode: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: ProductStatus = ProductStatus.IN_WAREHOUSE,

    @field:Size(max = 255)
    @Column(name = "assigned_to", nullable = true, length = 255)
    var assignedTo: String? = null,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null,
) {
    fun toDTO(): ProductDTO =
        ProductDTO(
            id = this.id,
            name = this.name,
            barCode = this.barCode,
            status = this.status,
            assignedTo = this.assignedTo,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt
        )
}