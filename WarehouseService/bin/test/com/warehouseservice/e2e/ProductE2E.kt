package com.warehouseservice.e2e

import com.warehouseservice.models.dtos.CreateProductDTO
import com.warehouseservice.models.dtos.ProductDTO
import com.warehouseservice.models.dtos.ProductPageDTO
import com.warehouseservice.models.dtos.UpdateProductDTO
import com.warehouseservice.models.dtos.UpdateProductStatusDTO
import com.warehouseservice.models.entities.Product
import com.warehouseservice.models.enums.ProductStatus
import com.warehouseservice.repositories.ProductRepository
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldHaveSize
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.resttestclient.exchange
import org.springframework.boot.resttestclient.getForEntity
import org.springframework.boot.resttestclient.postForEntity
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
@ActiveProfiles("test")
class ProductE2E {

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
    lateinit var restTemplate: TestRestTemplate

    @Autowired
    lateinit var productRepository: ProductRepository

    // ── setup ──────────────────────────────────────────────────────────────────

    @BeforeEach
    fun cleanup() {
        productRepository.deleteAll()
    }

    // ── fixture ────────────────────────────────────────────────────────────────

    private fun saveProduct(name: String = "Product 1", barCode: String = "8001120896247"): Product =
        productRepository.save(Product(name = name, barCode = barCode))

    // ── flow: create → get → update → delete ──────────────────────────────────

    @Nested
    inner class FullCrudFlow {

        @Test
        fun `full CRUD flow completes successfully`() {
            // CREATE
            val createResponse = restTemplate.postForEntity<ProductDTO>(
                "/api/v1/products",
                CreateProductDTO(name = "Product 1", barCode = "8001120896247")
            )
            createResponse.statusCode shouldBe HttpStatus.CREATED
            val created = createResponse.body!!
            created.id shouldNotBe null
            created.name shouldBe "Product 1"
            created.barCode shouldBe "8001120896247"

            // GET by ID
            val getResponse = restTemplate.getForEntity<ProductDTO>(
                "/api/v1/products/${created.id}"
            )
            getResponse.statusCode shouldBe HttpStatus.OK
            getResponse.body!!.id shouldBe created.id

            // UPDATE
            val updateResponse = restTemplate.exchange<ProductDTO>(
                "/api/v1/products/${created.id}",
                HttpMethod.PATCH,
                HttpEntity(UpdateProductDTO(name = "Product 1 updated", barCode = "20245918"))
            )
            updateResponse.statusCode shouldBe HttpStatus.OK
            updateResponse.body!!.name shouldBe "Product 1 updated"
            updateResponse.body!!.barCode shouldBe "20245918"
            updateResponse.body!!.updatedAt shouldNotBe null

            // GET ALL — verify that only 1 product is updated
            val getAllResponse = restTemplate.getForEntity<ProductPageDTO>(
                "/api/v1/products"
            )
            getAllResponse.statusCode shouldBe HttpStatus.OK
            getAllResponse.body!!.totalElements shouldBe 1
            getAllResponse.body!!.content shouldHaveSize 1
            getAllResponse.body!!.content[0].name shouldBe "Product 1 updated"

            // DELETE
            val deleteResponse = restTemplate.exchange<Void>(
                "/api/v1/products/${created.id}",
                HttpMethod.DELETE,
                HttpEntity.EMPTY
            )
            deleteResponse.statusCode shouldBe HttpStatus.NO_CONTENT

            // verify that is not in the DB anymore
            productRepository.findById(created.id!!).isPresent shouldBe false

            // GET after DELETE → 404
            val getAfterDelete = restTemplate.getForEntity(
                "/api/v1/products/${created.id}",
                Map::class.java
            )
            getAfterDelete.statusCode shouldBe HttpStatus.NOT_FOUND
        }
    }

    // ── flow: status changes ─────────────────────────────────────────────

    @Nested
    inner class StatusFlow {

        @Test
        fun `full status flow completes successfully`() {
            val createResponse = restTemplate.postForEntity<ProductDTO>(
                "/api/v1/products",
                CreateProductDTO(name = "Product 1", barCode = "8001120896247")
            )
            createResponse.statusCode shouldBe HttpStatus.CREATED
            createResponse.body!!.status shouldBe ProductStatus.IN_WAREHOUSE
            createResponse.body!!.assignedTo shouldBe null

            val id = createResponse.body!!.id!!

            val assignResponse = restTemplate.exchange<ProductDTO>(
                "/api/v1/products/$id/status",
                HttpMethod.PATCH,
                HttpEntity(UpdateProductStatusDTO(status = ProductStatus.ASSIGNED, assignedTo = "Mario Rossi"))
            )
            assignResponse.statusCode shouldBe HttpStatus.OK
            assignResponse.body!!.status shouldBe ProductStatus.ASSIGNED
            assignResponse.body!!.assignedTo shouldBe "Mario Rossi"

            val repairResponse = restTemplate.exchange<ProductDTO>(
                "/api/v1/products/$id/status",
                HttpMethod.PATCH,
                HttpEntity(UpdateProductStatusDTO(status = ProductStatus.IN_REPAIR))
            )
            repairResponse.statusCode shouldBe HttpStatus.OK
            repairResponse.body!!.status shouldBe ProductStatus.IN_REPAIR
            repairResponse.body!!.assignedTo shouldBe null

            val warehouseResponse = restTemplate.exchange<ProductDTO>(
                "/api/v1/products/$id/status",
                HttpMethod.PATCH,
                HttpEntity(UpdateProductStatusDTO(status = ProductStatus.IN_WAREHOUSE))
            )
            warehouseResponse.statusCode shouldBe HttpStatus.OK
            warehouseResponse.body!!.status shouldBe ProductStatus.IN_WAREHOUSE
            warehouseResponse.body!!.assignedTo shouldBe null
        }

        @Test
        fun `returns 422 when ASSIGNED without assignedTo`() {
            val created = restTemplate.postForEntity<ProductDTO>(
                "/api/v1/products",
                CreateProductDTO(name = "Product 1", barCode = "8001120896247")
            ).body!!

            val response = restTemplate.exchange<Map<*, *>>(
                "/api/v1/products/${created.id}/status",
                HttpMethod.PATCH,
                HttpEntity(UpdateProductStatusDTO(status = ProductStatus.ASSIGNED))
            )

            response.statusCode.value() shouldBe HttpStatus.UNPROCESSABLE_ENTITY.value()
        }
    }

    // ── flow: errors and edge cases ─────────────────────────────────────────────

    @Nested
    inner class ErrorFlows {

        @Test
        fun `create with duplicate barcode returns 409`() {
            saveProduct(barCode = "8001120896247")

            val response = restTemplate.postForEntity(
                "/api/v1/products",
                CreateProductDTO(name = "Other product", barCode = "8001120896247"),
                Map::class.java
            )

            response.statusCode shouldBe HttpStatus.CONFLICT
            productRepository.count() shouldBe 1
        }

        @Test
        fun `create with invalid barcode returns 400`() {
            val response = restTemplate.postForEntity(
                "/api/v1/products",
                CreateProductDTO(name = "Product 1", barCode = "123"),
                Map::class.java
            )

            response.statusCode shouldBe HttpStatus.BAD_REQUEST
            productRepository.count() shouldBe 0
        }

        @Test
        fun `get nonexistent product returns 404`() {
            val response = restTemplate.getForEntity(
                "/api/v1/products/${java.util.UUID.randomUUID()}",
                Map::class.java
            )

            response.statusCode shouldBe HttpStatus.NOT_FOUND
        }

        @Test
        fun `update nonexistent product returns 404`() {
            val response = restTemplate.exchange(
                "/api/v1/products/${java.util.UUID.randomUUID()}",
                HttpMethod.PATCH,
                HttpEntity(UpdateProductDTO(name = "Product 1", barCode = "20245918")),
                Map::class.java
            )

            response.statusCode shouldBe HttpStatus.NOT_FOUND
        }

        @Test
        fun `delete nonexistent product returns 404`() {
            val response = restTemplate.exchange(
                "/api/v1/products/${java.util.UUID.randomUUID()}",
                HttpMethod.DELETE,
                HttpEntity.EMPTY,
                Map::class.java
            )

            response.statusCode shouldBe HttpStatus.NOT_FOUND
        }

        @Test
        fun `update with barcode of another product returns 409`() {
            saveProduct(name = "Product A", barCode = "8001120896247")
            val productB = saveProduct(name = "Product B", barCode = "20245918")

            val response = restTemplate.exchange(
                "/api/v1/products/${productB.id}",
                HttpMethod.PATCH,
                HttpEntity(UpdateProductDTO(name = "Product B updated", barCode = "8001120896247")),
                Map::class.java
            )

            response.statusCode shouldBe HttpStatus.CONFLICT
        }
    }

    // ── flow: paginazione e filtri ─────────────────────────────────────────────

    @Nested
    inner class PaginationAndFilters {

        @Test
        fun `pagination returns correct page`() {
            repeat(5) { i -> saveProduct("Product $i", "800112089624$i") }

            val response = restTemplate.getForEntity<ProductPageDTO>(
                "/api/v1/products?page=0&size=2"
            )

            response.statusCode shouldBe HttpStatus.OK
            response.body!!.content shouldHaveSize 2
            response.body!!.totalElements shouldBe 5
            response.body!!.totalPages shouldBe 3
        }

        @Test
        fun `filter by name returns matching products`() {
            saveProduct("Product 1", "8001120896247")
            saveProduct("Warehouse 1", "20245918")

            val response = restTemplate.getForEntity<ProductPageDTO>(
                "/api/v1/products?name=roduct"
            )

            response.statusCode shouldBe HttpStatus.OK
            response.body!!.totalElements shouldBe 1
            response.body!!.content[0].name shouldBe "Product 1"
        }

        @Test
        fun `filter by barCode returns matching products`() {
            saveProduct("Product 1", "8001120896247")
            saveProduct("Product 2", "20245918")

            val response = restTemplate.getForEntity<ProductPageDTO>(
                "/api/v1/products?barCode=80011"
            )

            response.statusCode shouldBe HttpStatus.OK
            response.body!!.totalElements shouldBe 1
            response.body!!.content[0].barCode shouldBe "8001120896247"
        }
    }
}