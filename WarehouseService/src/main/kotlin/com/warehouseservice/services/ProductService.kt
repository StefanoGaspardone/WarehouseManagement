package com.warehouseservice.services

import com.warehouseservice.exceptions.InvalidProductStatusException
import com.warehouseservice.exceptions.ProductNotFoundException
import com.warehouseservice.exceptions.ProductWithBarCodeAlreadyExistsException
import com.warehouseservice.models.dtos.CreateProductDTO
import com.warehouseservice.models.dtos.PageableInfo
import com.warehouseservice.models.dtos.ProductDTO
import com.warehouseservice.models.dtos.ProductPageDTO
import com.warehouseservice.models.dtos.SortInfo
import com.warehouseservice.models.dtos.UpdateProductDTO
import com.warehouseservice.models.dtos.UpdateProductStatusDTO
import com.warehouseservice.models.entities.Product
import com.warehouseservice.models.enums.ProductStatus
import com.warehouseservice.repositories.ProductRepository
import com.warehouseservice.specifications.ProductSpecifications
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ProductService(
    private val productRepository: ProductRepository
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun findAll(page: Int, size: Int, name: String?, barCode: String?, sort: String?): ProductPageDTO {
        logger.debug("\n\t[DEBUG] [product_service][find_all] Retrieving product with filters\n\tpage={}\n\tsize={}\n\tname={}\n\tbarCode={}\n\tsort={}", page, size, name, barCode, sort)

        try {
            val pageSafe = if(page < 0) 0 else page
            val sizeSafe = size.coerceIn(1, 100)
            val sortObj = resolveSort(sort)

            val pageable = PageRequest.of(pageSafe, sizeSafe, sortObj)

            val spec = ProductSpecifications.withFilters(name, barCode)
            val result = productRepository.findAll(spec, pageable)

            logger.info("\n\t[INFO] [product_service][find_all] Retrieved products\n\tpage={}\n\tsize={}\n\tresultSize={}\n\ttotalElements={}", pageSafe, sizeSafe, result.numberOfElements, result.totalElements)
            return toProductPageDTO(result)
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [product_service][find_all] Error retrieving products with filters\n\tpage={}\n\tsize={}\n\terror={}", page, size, e.message, e)
            throw e
        }
    }

    @Transactional
    fun findByID(id: UUID): ProductDTO {
        logger.debug("\n\t[DEBUG] [product_service][find_by_id] Retrieving product with id {}", id)

        try {
            val product = productRepository.findById(id).orElseThrow { ProductNotFoundException("Product with id $id not found") }

            logger.info("\n\t[INFO] [product_service][find_by_id] Retrieved product with id {}", id)
            return product.toDTO()
        } catch(e: ProductNotFoundException) {
            logger.warn("\n\t[ERROR] [product_service][find_by_id] Product with id {} not found", id)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [product_service][find_by_id] Error retrieving product with id {}: {}", id, e.message, e)
            throw e
        }
    }

    @Transactional
    fun create(createProductDTO: CreateProductDTO): ProductDTO {
        logger.debug("\n\t[DEBUG] [product_service][create] Creating product with fields:\n\tname = {}\n\tbarCode = {}", createProductDTO.barCode, createProductDTO.barCode)

        try {
            productRepository.findByBarCode(createProductDTO.barCode).ifPresent { throw ProductWithBarCodeAlreadyExistsException("Product with bar code ${createProductDTO.barCode} already exists") }

            var product = Product(
                name = createProductDTO.name,
                barCode = createProductDTO.barCode,
            )
            product = productRepository.save(product)

            logger.info("\n\t[INFO] [product_service][create] Created product with fields:\n\tname = {}\n\tbarCode = {}", createProductDTO.barCode, createProductDTO.barCode)
            return product.toDTO()
        } catch(e: ProductWithBarCodeAlreadyExistsException) {
            logger.warn("\n\t[WARN] [product_service][create] Product with bar code {} already exists", createProductDTO.barCode)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [product_service][create] Error creating product with fields:\n\tname = {}\n\tbarCode = {}: {}", createProductDTO.barCode, createProductDTO.barCode, e.message, e)
            throw e
        }
    }

    fun update(id: UUID, updateProductDTO: UpdateProductDTO): ProductDTO {
        logger.debug("\n\t[DEBUG] [product_service][update] Updating product with id {} with fields:\n\tname = {}\n\tbarCode = {}", id, updateProductDTO.barCode, updateProductDTO.barCode)

        try {
            var product = productRepository.findById(id).orElseThrow { ProductNotFoundException("Product with id $id not found") }
            productRepository.findByBarCode(updateProductDTO.barCode).ifPresent { throw ProductWithBarCodeAlreadyExistsException("Product with bar code ${updateProductDTO.barCode} already exists") }

            product.name = updateProductDTO.name
            product.barCode = updateProductDTO.barCode

            product = productRepository.save(product)

            logger.info("\n\t[INFO] [product_service][update] Updated product with id {} with fields:\n\tname = {}\n\tbarCode = {}", id, updateProductDTO.barCode, updateProductDTO.barCode)
            return product.toDTO()
        } catch(e: ProductNotFoundException) {
            logger.warn("\n\t[WARN] [product_service][update] Product with id {} not found", id)
            throw e
        } catch(e: ProductWithBarCodeAlreadyExistsException) {
            logger.warn("\n\t[WARN] [product_service][update] Product with bar code {} already exists", updateProductDTO.barCode)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [product_service][update] Error updating product with id {} with fields:\n\tname = {}\n\tbarCode = {}: {}", id, updateProductDTO.barCode, updateProductDTO.barCode, e.message, e)
            throw e
        }
    }

    @Transactional
    fun updateStatus(id: UUID, dto: UpdateProductStatusDTO): ProductDTO {
        logger.debug("\n\t[DEBUG] [product_service][update_status] Updating status of product with id {}", id)

        try {
            var product = productRepository.findById(id).orElseThrow { ProductNotFoundException("Product with id $id not found") }

            if(dto.status == ProductStatus.ASSIGNED && dto.assignedTo.isNullOrBlank()) {
                throw InvalidProductStatusException("assignedTo is required when status is ASSIGNED")
            }

            if(dto.status != ProductStatus.ASSIGNED && dto.assignedTo != null) {
                throw InvalidProductStatusException("assignedTo must be null when status is not ASSIGNED")
            }

            product.status = dto.status
            product.assignedTo = if(dto.status == ProductStatus.ASSIGNED) dto.assignedTo else null

            product = productRepository.saveAndFlush(product)

            logger.info("\n\t[INFO] [product_service][update_status] Updated status of product with id {} to {}", id, dto.status)
            return product.toDTO()
        } catch(e: ProductNotFoundException) {
            logger.warn("\n\t[WARN] [product_service][update_status] Product with id {} not found", id)
            throw e
        } catch(e: InvalidProductStatusException) {
            logger.warn("\n\t[WARN] [product_service][update_status] Invalid status transition: {}", e.message)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [product_service][update_status] Error updating status of product with id {}: {}", id, e.message, e)
            throw e
        }
    }

    @Transactional
    fun deleteByID(id: UUID) {
        logger.debug("\n\t[DEBUG] [product_service][delete_by_id] Retrieving product with id {}", id)

        try {
            productRepository.findById(id).orElseThrow { ProductNotFoundException("Product with id $id not found") }
            productRepository.deleteById(id)

            logger.info("\n\t[INFO] [product_service][delete_by_id] Deleted product with id {}", id)
        } catch(e: ProductNotFoundException) {
            logger.warn("\n\t[ERROR] [product_service][delete_by_id] Product with id {} not found", id)
            throw e
        } catch(e: Exception) {
            logger.error("\n\t[ERROR] [product_service][delete_by_id] Error deleting product with id {}: {}", id, e.message, e)
            throw e
        }
    }

    private fun resolveSort(sort: String?): Sort {
        val supportedFields = mapOf(
            "name" to "name",
            "createdAt" to "createdAt",
        )
        val parts = sort?.split("-") ?: listOf("name", "asc")

        val field = (parts.getOrNull(0) ?: "name").trim().ifBlank { "name" }
        val directionRaw = (parts.getOrNull(1) ?: "asc").trim().uppercase()

        val safeField = supportedFields[field] ?: "name"
        val direction = if(directionRaw == "ASC") Sort.Direction.ASC else Sort.Direction.DESC

        return Sort.by(direction, safeField)
    }

    private fun toProductPageDTO(pageResult: Page<Product>): ProductPageDTO {
        val dtos = pageResult.content.map { it.toDTO() }

        val topSortInfo = SortInfo(
            empty = pageResult.sort.isEmpty,
            sorted = pageResult.sort.isSorted,
            unsorted = pageResult.sort.isUnsorted,
        )

        val pageableSortInfo = SortInfo(
            empty = pageResult.pageable.sort.isEmpty,
            sorted = pageResult.pageable.sort.isSorted,
            unsorted = pageResult.pageable.sort.isUnsorted,
        )

        val pageableInfo = PageableInfo(
            pageNumber = pageResult.pageable.pageNumber,
            pageSize = pageResult.pageable.pageSize,
            sort = pageableSortInfo,
            offset = pageResult.pageable.offset,
            paged = pageResult.pageable.isPaged,
            unpaged = pageResult.pageable.isUnpaged,
        )

        return ProductPageDTO(
            content = dtos,
            pageable = pageableInfo,
            totalElements = pageResult.totalElements,
            totalPages = pageResult.totalPages,
            last = pageResult.isLast,
            size = pageResult.size,
            number = pageResult.number,
            sort = topSortInfo,
            first = pageResult.isFirst,
            numberOfElements = pageResult.numberOfElements,
            empty = pageResult.isEmpty,
        )
    }
}