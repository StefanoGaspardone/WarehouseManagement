package com.warehouseservice.intergration.services

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
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class ProductServiceIntegration {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:17").apply {
            withDatabaseName("warehouse_test")
            withUsername("test")
            withPassword("test")
        }

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }

    @Autowired
    lateinit var productService: ProductService

    @Autowired
    lateinit var productRepository: ProductRepository

    // ── setup ──────────────────────────────────────────────────────────────────

    @BeforeEach
    fun cleanup() {
        productRepository.deleteAll()
    }

    // ── fixture ────────────────────────────────────────────────────────────────

    private fun saveProduct(name: String = "Product 1", barCode: String = "8001120896247", status: ProductStatus = ProductStatus.IN_WAREHOUSE): Product =
        productRepository.save(
            Product(name = name, barCode = barCode, status = status)
        )

    // ── findAll ────────────────────────────────────────────────────────────────

    @Nested
    inner class FindAll {

        @Test
        fun `returns all products when no filters applied`() {
            saveProduct("Product 1", "8001120896247")
            saveProduct("Product 2", "20245918")

            val result = productService.findAll(0, 20, null, null, null, "name-asc")

            result.totalElements shouldBe 2
            result.content shouldHaveSize 2
        }

        @Test
        fun `filters by name with multi token`() {
            saveProduct("Product 1", "8001120896247")
            saveProduct("Warehouse 1", "20245918")

            val result = productService.findAll(0, 20, "produ", null, null, null)

            result.totalElements shouldBe 1
            result.content[0].name shouldBe "Product 1"
        }

        @Test
        fun `filters by barCode containing substring`() {
            saveProduct("Product 1", "8001120896247")
            saveProduct("Product 2", "20245918")

            val result = productService.findAll(0, 20, null, "80011", null, null)

            result.totalElements shouldBe 1
            result.content[0].barCode shouldBe "8001120896247"
        }

        @Test
        fun `filters by status`() {
            saveProduct("Product 1", "8001120896247", ProductStatus.IN_WAREHOUSE)
            saveProduct("Product 2", "20245918", ProductStatus.DAMAGED)

            val result = productService.findAll(0, 20, null, null, ProductStatus.DAMAGED, null)

            result.totalElements shouldBe 1
            result.content[0].name shouldBe "Product 2"
            result.content[0].status shouldBe ProductStatus.DAMAGED
        }

        @Test
        fun `returns empty page when no products match filters`() {
            saveProduct("Product 1", "8001120896247")

            val result = productService.findAll(0, 20, "Non existing product", null, null, null)

            result.totalElements shouldBe 0
            result.empty shouldBe true
        }

        @Test
        fun `respects pagination`() {
            repeat(5) { i -> saveProduct("Product $i", "800112089624$i") }

            val result = productService.findAll(0, 2, null, null, null, null)

            result.content shouldHaveSize 2
            result.totalElements shouldBe 5
            result.totalPages shouldBe 3
        }

        @Test
        fun `clamps negative page to 0`() {
            saveProduct()

            val result = productService.findAll(-5, 20, null, null, null, null)

            result.number shouldBe 0
        }

        @Test
        fun `clamps size above 100 to 100`() {
            saveProduct()

            val result = productService.findAll(0, 999, null, null, null, null)

            result.size shouldBe 100
        }
    }

    // ── findByID ───────────────────────────────────────────────────────────────

    @Nested
    inner class FindByID {

        @Test
        fun `returns product when found`() {
            val saved = saveProduct()

            val result = productService.findByID(saved.id!!)

            result.id shouldBe saved.id
            result.name shouldBe "Product 1"
            result.barCode shouldBe "8001120896247"
            result.createdAt shouldNotBe null
            result.updatedAt shouldNotBe null
        }

        @Test
        fun `throws ProductNotFoundException when product does not exist`() {
            val ex = shouldThrow<ProductNotFoundException> {
                productService.findByID(UUID.randomUUID())
            }

            ex.message shouldContain "not found"
        }
    }

    // ── create ─────────────────────────────────────────────────────────────────

    @Nested
    inner class Create {

        @Test
        fun `creates product and persists to db`() {
            val dto = CreateProductDTO(name = "Product 1", barCode = "8001120896247")

            val result = productService.create(dto)

            result.id shouldNotBe null
            result.name shouldBe "Product 1"
            result.barCode shouldBe "8001120896247"

            productRepository.findById(result.id!!).isPresent shouldBe true
        }

        @Test
        fun `throws ProductWithBarCodeAlreadyExistsException when barcode is duplicate`() {
            saveProduct(barCode = "8001120896247")

            shouldThrow<ProductWithBarCodeAlreadyExistsException> {
                productService.create(CreateProductDTO(name = "Other product", barCode = "8001120896247"))
            }

            productRepository.count() shouldBe 1
        }
    }

    // ── update ─────────────────────────────────────────────────────────────────

    @Nested
    inner class Update {

        @Test
        fun `updates product and persists to db`() {
            val saved = saveProduct()
            val dto = UpdateProductDTO(name = "Product 1", barCode = "20245918")

            val result = productService.update(saved.id!!, dto)

            result.name shouldBe "Product 1"
            result.barCode shouldBe "20245918"

            val fromDb = productRepository.findById(saved.id!!).get()
            fromDb.name shouldBe "Product 1"
            fromDb.barCode shouldBe "20245918"
        }

        @Test
        fun `throws ProductNotFoundException when product does not exist`() {
            shouldThrow<ProductNotFoundException> {
                productService.update(
                    UUID.randomUUID(),
                    UpdateProductDTO(name = "Product 1", barCode = "20245918")
                )
            }
        }

        @Test
        fun `throws ProductWithBarCodeAlreadyExistsException when barcode belongs to another product`() {
            saveProduct(name = "Product 1", barCode = "8001120896247")
            val product2 = saveProduct(name = "Product 2", barCode = "20245918")

            shouldThrow<ProductWithBarCodeAlreadyExistsException> {
                productService.update(
                    product2.id!!,
                    UpdateProductDTO(name = "Product 2 updated", barCode = "8001120896247")
                )
            }
        }
    }

    // ── updateStatus ─────────────────────────────────────────────────────────────

    @Nested
    inner class UpdateStatus {

        @Test
        fun `updates status to ASSIGNED with assignedTo`() {
            val saved = saveProduct()
            val dto = UpdateProductStatusDTO(status = ProductStatus.ASSIGNED, assignedTo = "Mario Rossi")

            val result = productService.updateStatus(saved.id!!, dto)

            result.status shouldBe ProductStatus.ASSIGNED
            result.assignedTo shouldBe "Mario Rossi"

            val fromDb = productRepository.findById(saved.id!!).get()
            fromDb.status shouldBe ProductStatus.ASSIGNED
            fromDb.assignedTo shouldBe "Mario Rossi"
        }

        @Test
        fun `updates status from ASSIGNED back to IN_WAREHOUSE clears assignedTo`() {
            val saved = saveProduct()
            productService.updateStatus(saved.id!!, UpdateProductStatusDTO(status = ProductStatus.ASSIGNED, assignedTo = "Mario Rossi"))

            val result = productService.updateStatus(saved.id!!, UpdateProductStatusDTO(status = ProductStatus.IN_WAREHOUSE))

            result.status shouldBe ProductStatus.IN_WAREHOUSE
            result.assignedTo shouldBe null

            val fromDb = productRepository.findById(saved.id!!).get()
            fromDb.assignedTo shouldBe null
        }

        @Test
        fun `throws InvalidProductStatusException when ASSIGNED without assignedTo`() {
            val saved = saveProduct()

            shouldThrow<InvalidProductStatusException> {
                productService.updateStatus(saved.id!!, UpdateProductStatusDTO(status = ProductStatus.ASSIGNED))
            }
        }

        @Test
        fun `throws InvalidProductStatusException when non-ASSIGNED with assignedTo`() {
            val saved = saveProduct()

            shouldThrow<InvalidProductStatusException> {
                productService.updateStatus(saved.id!!, UpdateProductStatusDTO(status = ProductStatus.DAMAGED, assignedTo = "Mario Rossi"))
            }
        }

        @Test
        fun `throws ProductNotFoundException when product does not exist`() {
            shouldThrow<ProductNotFoundException> {
                productService.updateStatus(UUID.randomUUID(), UpdateProductStatusDTO(status = ProductStatus.IN_WAREHOUSE))
            }
        }
    }

    // ── deleteByID ─────────────────────────────────────────────────────────────

    @Nested
    inner class DeleteByID {

        @Test
        fun `deletes product from db`() {
            val saved = saveProduct()

            productService.deleteByID(saved.id!!)
            productRepository.findById(saved.id!!).isPresent shouldBe false
        }

        @Test
        fun `throws ProductNotFoundException when product does not exist`() {
            shouldThrow<ProductNotFoundException> {
                productService.deleteByID(UUID.randomUUID())
            }
        }
    }
}