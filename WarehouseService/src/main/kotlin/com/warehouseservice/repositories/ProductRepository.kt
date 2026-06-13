package com.warehouseservice.repositories

import com.warehouseservice.models.entities.Product
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface ProductRepository: JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

    fun findByBarCode(barCode: String): Optional<Product>
}