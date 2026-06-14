package com.warehouseservice.unit.services

import com.warehouseservice.exceptions.InvalidProductStatusException
import com.warehouseservice.exceptions.ProductNotFoundException
import com.warehouseservice.exceptions.ProductWithBarCodeAlreadyExistsException
import com.warehouseservice.models.dtos.CreateProductDTO
import com.warehouseservice.models.dtos.UpdateProductDTO
import com.warehouseservice.models.dtos.UpdateProductStatusDTO
import com.warehouseservice.models.entities.Product
import com.warehouseservice.models.enums.ProductStatus
import com.warehouseservice.repositories.ProductRepository
import com.warehouseservice.services.ProductService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import java.time.Instant
import java.util.*

class ProductServiceUnit {

    private val productRepository = mockk<ProductRepository>()
    private val productService = ProductService(productRepository)

    // ── fixture ────────────────────────────────────────────────────────────────

    private val fixedId = UUID.randomUUID()
    private val fixedNow = Instant.now()

    private fun makeProduct(id: UUID = fixedId, name: String = "Product 1", barCode: String = "8001120896247") =
        Product(
            id = id,
            name = name,
            barCode = barCode,
        ).also {
            it.createdAt = fixedNow
            it.updatedAt = fixedNow
        }

    // ── findAll ────────────────────────────────────────────────────────────────

    @Nested
    inner class FindAll {

        @Test
        fun `returns paged products when no filters are applied`() {
            val product = makeProduct()
            val pageRequest = PageRequest.of(0, 20)
            val pageResult = PageImpl(listOf(product), pageRequest, 1)

            every { productRepository.findAll(any<Specification<Product>>(), any<org.springframework.data.domain.Pageable>()) } returns pageResult

            val result = productService.findAll(0, 20, null, null, "name-asc")

            result.totalElements shouldBe 1
            result.content.size shouldBe 1
            result.content[0].name shouldBe "Product 1"
        }

        @Test
        fun `returns empty page when no products match filters`() {
            val pageRequest = PageRequest.of(0, 20)
            val emptyPage = PageImpl(emptyList<Product>(), pageRequest, 0)

            every { productRepository.findAll(any<Specification<Product>>(), any<org.springframework.data.domain.Pageable>()) } returns emptyPage

            val result = productService.findAll(0, 20, "Non existing product", null, null)

            result.totalElements shouldBe 0
            result.content shouldBe emptyList()
            result.empty shouldBe true
        }

        @Test
        fun `clamps negative page to 0`() {
            val pageRequest = PageRequest.of(0, 20)
            val pageResult = PageImpl(emptyList<Product>(), pageRequest, 0)

            every { productRepository.findAll(any<Specification<Product>>(), any<org.springframework.data.domain.Pageable>()) } returns pageResult

            productService.findAll(-5, 20, null, null, null)

            verify {
                productRepository.findAll(
                    any<Specification<Product>>(),
                    match<org.springframework.data.domain.Pageable> { it.pageNumber == 0 }
                )
            }
        }

        @Test
        fun `clamps size above 100 to 100`() {
            val pageRequest = PageRequest.of(0, 100)
            val pageResult = PageImpl(emptyList<Product>(), pageRequest, 0)

            every { productRepository.findAll(any<Specification<Product>>(), any<org.springframework.data.domain.Pageable>()) } returns pageResult

            productService.findAll(0, 999, null, null, null)

            verify {
                productRepository.findAll(
                    any<Specification<Product>>(),
                    match<org.springframework.data.domain.Pageable> { it.pageSize == 100 }
                )
            }
        }

        @Test
        fun `falls back to name-asc when sort is invalid`() {
            val pageRequest = PageRequest.of(0, 20)
            val pageResult = PageImpl(emptyList<Product>(), pageRequest, 0)

            every { productRepository.findAll(any<Specification<Product>>(), any<org.springframework.data.domain.Pageable>()) } returns pageResult

            productService.findAll(0, 20, null, null, "non_existing_field-asc")

            verify {
                productRepository.findAll(
                    any<Specification<Product>>(),
                    match<org.springframework.data.domain.Pageable> {
                        it.sort.getOrderFor("name") != null
                    }
                )
            }
        }

        @Test
        fun `throws exception when repository fails unexpectedly`() {
            every {
                productRepository.findAll(any<Specification<Product>>(), any<org.springframework.data.domain.Pageable>())
            } throws RuntimeException("DB connection lost")

            shouldThrow<RuntimeException> {
                productService.findAll(0, 20, null, null, null)
            }
        }
    }

    // ── findByID ───────────────────────────────────────────────────────────────

    @Nested
    inner class FindByID {

        @Test
        fun `returns product DTO when product exists`() {
            val product = makeProduct()
            every { productRepository.findById(fixedId) } returns Optional.of(product)

            val result = productService.findByID(fixedId)

            result.id shouldBe fixedId
            result.name shouldBe "Product 1"
            result.barCode shouldBe "8001120896247"
            result.createdAt shouldNotBe null
        }

        @Test
        fun `throws ProductNotFoundException when product does not exist`() {
            val randomId = UUID.randomUUID()
            every { productRepository.findById(randomId) } returns Optional.empty()

            shouldThrow<ProductNotFoundException> {
                productService.findByID(randomId)
            }
        }

        @Test
        fun `throws exception when repository fails unexpectedly`() {
            every { productRepository.findById(any()) } throws RuntimeException("DB connection lost")

            shouldThrow<RuntimeException> {
                productService.findByID(fixedId)
            }
        }
    }

    // ── create ─────────────────────────────────────────────────────────────────

    @Nested
    inner class Create {

        @Test
        fun `creates and returns product DTO when barcode is unique`() {
            val dto = CreateProductDTO(name = "Product 1", barCode = "8001120896247")
            val savedProduct = makeProduct()

            every { productRepository.findByBarCode(dto.barCode) } returns Optional.empty()
            every { productRepository.save(any()) } returns savedProduct

            val result = productService.create(dto)

            result.id shouldNotBe null
            result.name shouldBe "Product 1"
            result.barCode shouldBe "8001120896247"

            verify(exactly = 1) { productRepository.save(any()) }
        }

        @Test
        fun `throws ProductWithBarCodeAlreadyExistsException when barcode already exists`() {
            val dto = CreateProductDTO(name = "Product 1", barCode = "8001120896247")
            val existingProduct = makeProduct()

            every { productRepository.findByBarCode(dto.barCode) } returns Optional.of(existingProduct)

            shouldThrow<ProductWithBarCodeAlreadyExistsException> {
                productService.create(dto)
            }

            verify(exactly = 0) { productRepository.save(any()) }
        }

        @Test
        fun `throws exception when repository fails unexpectedly`() {
            val dto = CreateProductDTO(name = "Product 1", barCode = "8001120896247")

            every { productRepository.findByBarCode(any()) } returns Optional.empty()
            every { productRepository.save(any()) } throws RuntimeException("DB connection lost")

            shouldThrow<RuntimeException> {
                productService.create(dto)
            }
        }
    }

    // ── update ─────────────────────────────────────────────────────────────────

    @Nested
    inner class Update {

        @Test
        fun `updates and returns product DTO when product exists and barcode is unique`() {
            val dto = UpdateProductDTO(name = "Product 1", barCode = "20245918")
            val existingProduct = makeProduct()
            val updatedProduct = makeProduct(name = "Product 1", barCode = "20245918")

            every { productRepository.findById(fixedId) } returns Optional.of(existingProduct)
            every { productRepository.findByBarCode(dto.barCode) } returns Optional.empty()
            every { productRepository.save(any()) } returns updatedProduct

            val result = productService.update(fixedId, dto)

            result.name shouldBe "Product 1"
            result.barCode shouldBe "20245918"
        }

        @Test
        fun `throws ProductNotFoundException when product does not exist`() {
            val randomId = UUID.randomUUID()
            val dto = UpdateProductDTO(name = "Product 1", barCode = "20245918")

            every { productRepository.findById(randomId) } returns Optional.empty()

            shouldThrow<ProductNotFoundException> {
                productService.update(randomId, dto)
            }

            verify(exactly = 0) { productRepository.save(any()) }
        }

        @Test
        fun `throws ProductWithBarCodeAlreadyExistsException when barcode belongs to another product`() {
            val dto = UpdateProductDTO(name = "Product 1", barCode = "20245918")
            val existingProduct = makeProduct()
            val anotherProduct = makeProduct(id = UUID.randomUUID(), barCode = "20245918")

            every { productRepository.findById(fixedId) } returns Optional.of(existingProduct)
            every { productRepository.findByBarCode(dto.barCode) } returns Optional.of(anotherProduct)

            shouldThrow<ProductWithBarCodeAlreadyExistsException> {
                productService.update(fixedId, dto)
            }

            verify(exactly = 0) { productRepository.save(any()) }
        }

        @Test
        fun `throws exception when repository fails unexpectedly`() {
            val dto = UpdateProductDTO(name = "Product 1", barCode = "20245918")
            val existingProduct = makeProduct()

            every { productRepository.findById(fixedId) } returns Optional.of(existingProduct)
            every { productRepository.findByBarCode(any()) } returns Optional.empty()
            every { productRepository.save(any()) } throws RuntimeException("DB connection lost")

            shouldThrow<RuntimeException> {
                productService.update(fixedId, dto)
            }
        }
    }

    // ── updateStatus ─────────────────────────────────────────────────────────────

    @Nested
    inner class UpdateStatus {

        @Test
        fun `updates status to IN_WAREHOUSE successfully`() {
            val product = makeProduct()
            val dto = UpdateProductStatusDTO(status = ProductStatus.IN_WAREHOUSE)

            every { productRepository.findById(fixedId) } returns Optional.of(product)
            every { productRepository.saveAndFlush(any()) } returns product.apply { status = ProductStatus.IN_WAREHOUSE }

            val result = productService.updateStatus(fixedId, dto)

            result.status shouldBe ProductStatus.IN_WAREHOUSE
            result.assignedTo shouldBe null
        }

        @Test
        fun `updates status to DAMAGED successfully`() {
            val product = makeProduct()
            val dto = UpdateProductStatusDTO(status = ProductStatus.DAMAGED)

            every { productRepository.findById(fixedId) } returns Optional.of(product)
            every { productRepository.saveAndFlush(any()) } returns product.apply { status = ProductStatus.DAMAGED }

            val result = productService.updateStatus(fixedId, dto)

            result.status shouldBe ProductStatus.DAMAGED
            result.assignedTo shouldBe null
        }

        @Test
        fun `updates status to IN_REPAIR successfully`() {
            val product = makeProduct()
            val dto = UpdateProductStatusDTO(status = ProductStatus.IN_REPAIR)

            every { productRepository.findById(fixedId) } returns Optional.of(product)
            every { productRepository.saveAndFlush(any()) } returns product.apply { status = ProductStatus.IN_REPAIR }

            val result = productService.updateStatus(fixedId, dto)

            result.status shouldBe ProductStatus.IN_REPAIR
            result.assignedTo shouldBe null
        }

        @Test
        fun `updates status to ASSIGNED with assignedTo successfully`() {
            val product = makeProduct()
            val dto = UpdateProductStatusDTO(status = ProductStatus.ASSIGNED, assignedTo = "Mario Rossi")

            every { productRepository.findById(fixedId) } returns Optional.of(product)
            every { productRepository.saveAndFlush(any()) } returns product.apply {
                status = ProductStatus.ASSIGNED
                assignedTo = "Mario Rossi"
            }

            val result = productService.updateStatus(fixedId, dto)

            result.status shouldBe ProductStatus.ASSIGNED
            result.assignedTo shouldBe "Mario Rossi"
        }

        @Test
        fun `throws InvalidProductStatusException when ASSIGNED without assignedTo`() {
            val product = makeProduct()
            val dto = UpdateProductStatusDTO(status = ProductStatus.ASSIGNED, assignedTo = null)

            every { productRepository.findById(fixedId) } returns Optional.of(product)

            shouldThrow<InvalidProductStatusException> {
                productService.updateStatus(fixedId, dto)
            }

            verify(exactly = 0) { productRepository.saveAndFlush(any()) }
        }

        @Test
        fun `throws InvalidProductStatusException when ASSIGNED with blank assignedTo`() {
            val product = makeProduct()
            val dto = UpdateProductStatusDTO(status = ProductStatus.ASSIGNED, assignedTo = "  ")

            every { productRepository.findById(fixedId) } returns Optional.of(product)

            shouldThrow<InvalidProductStatusException> {
                productService.updateStatus(fixedId, dto)
            }

            verify(exactly = 0) { productRepository.saveAndFlush(any()) }
        }

        @Test
        fun `throws InvalidProductStatusException when non-ASSIGNED status has assignedTo`() {
            val product = makeProduct()
            val dto = UpdateProductStatusDTO(status = ProductStatus.IN_WAREHOUSE, assignedTo = "Mario Rossi")

            every { productRepository.findById(fixedId) } returns Optional.of(product)

            shouldThrow<InvalidProductStatusException> {
                productService.updateStatus(fixedId, dto)
            }

            verify(exactly = 0) { productRepository.saveAndFlush(any()) }
        }

        @Test
        fun `throws ProductNotFoundException when product does not exist`() {
            val randomId = UUID.randomUUID()
            val dto = UpdateProductStatusDTO(status = ProductStatus.IN_WAREHOUSE)

            every { productRepository.findById(randomId) } returns Optional.empty()

            shouldThrow<ProductNotFoundException> {
                productService.updateStatus(randomId, dto)
            }

            verify(exactly = 0) { productRepository.saveAndFlush(any()) }
        }

        @Test
        fun `throws exception when repository fails unexpectedly`() {
            val dto = UpdateProductStatusDTO(status = ProductStatus.IN_WAREHOUSE)

            every { productRepository.findById(fixedId) } throws RuntimeException("DB connection lost")

            shouldThrow<RuntimeException> {
                productService.updateStatus(fixedId, dto)
            }
        }
    }

    // ── deleteByID ─────────────────────────────────────────────────────────────

    @Nested
    inner class DeleteByID {

        @Test
        fun `deletes product when it exists`() {
            val product = makeProduct()

            every { productRepository.findById(fixedId) } returns Optional.of(product)
            every { productRepository.deleteById(fixedId) } returns Unit

            productService.deleteByID(fixedId)

            verify(exactly = 1) { productRepository.deleteById(fixedId) }
        }

        @Test
        fun `throws ProductNotFoundException when product does not exist`() {
            val randomId = UUID.randomUUID()

            every { productRepository.findById(randomId) } returns Optional.empty()

            shouldThrow<ProductNotFoundException> {
                productService.deleteByID(randomId)
            }

            verify(exactly = 0) { productRepository.deleteById(any()) }
        }

        @Test
        fun `throws exception when repository fails unexpectedly`() {
            val existingProduct = makeProduct()

            every { productRepository.findById(fixedId) } returns Optional.of(existingProduct)
            every { productRepository.deleteById(fixedId) } throws RuntimeException("DB connection lost")

            shouldThrow<RuntimeException> {
                productService.deleteByID(fixedId)
            }
        }
    }

    @Nested
    inner class ResolveSort {

        @Test
        fun `sort null falls back to name asc`() {
            val pageResult = PageImpl(emptyList<Product>(), PageRequest.of(0, 20), 0)
            every { productRepository.findAll(any<Specification<Product>>(), any<org.springframework.data.domain.Pageable>()) } returns pageResult

            productService.findAll(0, 20, null, null, null)

            verify {
                productRepository.findAll(
                    any<Specification<Product>>(),
                    match<org.springframework.data.domain.Pageable> {
                        it.sort.getOrderFor("name")?.direction == Sort.Direction.ASC
                    }
                )
            }
        }

        @Test
        fun `sort with valid field desc`() {
            val pageResult = PageImpl(emptyList<Product>(), PageRequest.of(0, 20), 0)
            every { productRepository.findAll(any<Specification<Product>>(), any<org.springframework.data.domain.Pageable>()) } returns pageResult

            productService.findAll(0, 20, null, null, "name-desc")

            verify {
                productRepository.findAll(
                    any<Specification<Product>>(),
                    match<org.springframework.data.domain.Pageable> {
                        it.sort.getOrderFor("name")?.direction == Sort.Direction.DESC
                    }
                )
            }
        }

        @Test
        fun `sort with unsupported field falls back to name`() {
            val pageResult = PageImpl(emptyList<Product>(), PageRequest.of(0, 20), 0)
            every { productRepository.findAll(any<Specification<Product>>(), any<org.springframework.data.domain.Pageable>()) } returns pageResult

            productService.findAll(0, 20, null, null, "non_existing_field-asc")

            verify {
                productRepository.findAll(
                    any<Specification<Product>>(),
                    match<org.springframework.data.domain.Pageable> {
                        it.sort.getOrderFor("name") != null
                    }
                )
            }
        }

        @Test
        fun `sort with blank field falls back to name`() {
            val pageResult = PageImpl(emptyList<Product>(), PageRequest.of(0, 20), 0)
            every { productRepository.findAll(any<Specification<Product>>(), any<org.springframework.data.domain.Pageable>()) } returns pageResult

            productService.findAll(0, 20, null, null, "  -asc")

            verify {
                productRepository.findAll(
                    any<Specification<Product>>(),
                    match<org.springframework.data.domain.Pageable> {
                        it.sort.getOrderFor("name") != null
                    }
                )
            }
        }

        @Test
        fun `sort with createdAt field`() {
            val pageResult = PageImpl(emptyList<Product>(), PageRequest.of(0, 20), 0)
            every { productRepository.findAll(any<Specification<Product>>(), any<org.springframework.data.domain.Pageable>()) } returns pageResult

            productService.findAll(0, 20, null, null, "createdAt-asc")

            verify {
                productRepository.findAll(
                    any<Specification<Product>>(),
                    match<org.springframework.data.domain.Pageable> {
                        it.sort.getOrderFor("createdAt") != null
                    }
                )
            }
        }

        @Test
        fun `sort without dash uses field as name and direction as asc`() {
            val pageResult = PageImpl(emptyList<Product>(), PageRequest.of(0, 20), 0)
            every {
                productRepository.findAll(any<Specification<Product>>(), any<org.springframework.data.domain.Pageable>())
            } returns pageResult

            productService.findAll(0, 20, null, null, "name")

            verify {
                productRepository.findAll(
                    any<Specification<Product>>(),
                    match<org.springframework.data.domain.Pageable> {
                        it.sort.getOrderFor("name")?.direction == Sort.Direction.ASC
                    }
                )
            }
        }
    }
}