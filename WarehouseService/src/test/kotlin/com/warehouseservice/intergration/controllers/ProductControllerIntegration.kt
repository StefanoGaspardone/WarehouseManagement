package com.warehouseservice.intergration.controllers

import com.warehouseservice.models.dtos.CreateProductDTO
import com.warehouseservice.models.dtos.UpdateProductDTO
import com.warehouseservice.models.entities.Product
import com.warehouseservice.repositories.ProductRepository
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.*
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import tools.jackson.module.kotlin.jacksonObjectMapper

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class ProductControllerIntegration {

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
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var productRepository: ProductRepository

    private val objectMapper = jacksonObjectMapper()

    // ── setup ──────────────────────────────────────────────────────────────────

    @BeforeEach
    fun cleanup() {
        productRepository.deleteAll()
    }

    // ── fixture ────────────────────────────────────────────────────────────────

    private fun saveProduct(name: String = "Product 1", barCode: String = "8001120896247"): Product =
        productRepository.save(
            Product(name = name, barCode = barCode)
        )

    // ── GET /api/v1/products ───────────────────────────────────────────────────

    @Nested
    inner class FindAll {

        @Test
        fun `returns 200 with paged products`() {
            saveProduct("Product 1", "8001120896247")
            saveProduct("Product 2", "20245918")

            mockMvc.get("/api/v1/products").andExpect {
                status { isOk() }
                content { contentType(MediaType.APPLICATION_JSON) }
                jsonPath("$.totalElements") { value(2) }
            }
        }

        @Test
        fun `filters by name`() {
            saveProduct("Product 1", "8001120896247")
            saveProduct("Warehouse 2", "20245918")

            mockMvc.get("/api/v1/products") {
                param("name", "oduct")
            }.andExpect {
                status { isOk() }
                jsonPath("$.totalElements") { value(1) }
                jsonPath("$.content[0].name") { value("Product 1") }
            }
        }

        @Test
        fun `filters by barCode`() {
            saveProduct("Product 1", "8001120896247")
            saveProduct("Product 2", "20245918")

            mockMvc.get("/api/v1/products") {
                param("barCode", "80011")
            }.andExpect {
                status { isOk() }
                jsonPath("$.totalElements") { value(1) }
                jsonPath("$.content[0].barCode") { value("8001120896247") }
            }
        }

        @Test
        fun `returns empty page when no products match`() {
            saveProduct()

            mockMvc.get("/api/v1/products") {
                param("name", "nonexisting")
            }.andExpect {
                status { isOk() }
                jsonPath("$.totalElements") { value(0) }
                jsonPath("$.empty") { value(true) }
            }
        }

        @Test
        fun `respects pagination`() {
            repeat(5) { i -> saveProduct("Product $i", "800112089624$i") }

            mockMvc.get("/api/v1/products") {
                param("page", "0")
                param("size", "2")
            }.andExpect {
                status { isOk() }
                jsonPath("$.totalElements") { value(5) }
                jsonPath("$.totalPages") { value(3) }
                jsonPath("$.content.length()") { value(2) }
            }
        }
    }

    // ── GET /api/v1/products/{id} ──────────────────────────────────────────────

    @Nested
    inner class FindByID {

        @Test
        fun `returns 200 with product when found`() {
            val saved = saveProduct()

            mockMvc.get("/api/v1/products/${saved.id}").andExpect {
                status { isOk() }
                content { contentType(MediaType.APPLICATION_JSON) }
                jsonPath("$.id") { value(saved.id.toString()) }
                jsonPath("$.name") { value("Product 1") }
                jsonPath("$.barCode") { value("8001120896247") }
            }
        }

        @Test
        fun `returns 404 when product not found`() {
            mockMvc.get("/api/v1/products/${java.util.UUID.randomUUID()}").andExpect {
                status { isNotFound() }
                jsonPath("$.status") { value(404) }
            }
        }
    }

    // ── POST /api/v1/products ──────────────────────────────────────────────────

    @Nested
    inner class Create {

        @Test
        fun `returns 201 and persists product`() {
            mockMvc.post("/api/v1/products") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(
                    CreateProductDTO(name = "Product 1", barCode = "8001120896247")
                )
            }.andExpect {
                status { isCreated() }
                jsonPath("$.id") { exists() }
                jsonPath("$.name") { value("Product 1") }
                jsonPath("$.barCode") { value("8001120896247") }
            }

            productRepository.count() shouldBe 1
        }

        @Test
        fun `returns 400 when name is blank`() {
            mockMvc.post("/api/v1/products") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(
                    mapOf("name" to "", "barCode" to "8001120896247")
                )
            }.andExpect {
                status { isBadRequest() }
            }

            productRepository.count() shouldBe 0
        }

        @Test
        fun `returns 400 when barcode format is invalid`() {
            mockMvc.post("/api/v1/products") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(
                    mapOf("name" to "Product 1", "barCode" to "123")
                )
            }.andExpect {
                status { isBadRequest() }
            }

            productRepository.count() shouldBe 0
        }

        @Test
        fun `returns 409 when barcode already exists`() {
            saveProduct(barCode = "8001120896247")

            mockMvc.post("/api/v1/products") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(
                    CreateProductDTO(name = "Other product", barCode = "8001120896247")
                )
            }.andExpect {
                status { isConflict() }
                jsonPath("$.status") { value(409) }
                jsonPath("$.message") { exists() }
            }

            productRepository.count() shouldBe 1
        }
    }

    // ── PATCH /api/v1/products/{id} ────────────────────────────────────────────

    @Nested
    inner class Update {

        @Test
        fun `returns 200 and updates product in db`() {
            val saved = saveProduct()

            mockMvc.patch("/api/v1/products/${saved.id}") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(
                    UpdateProductDTO(name = "Product 1", barCode = "20245918")
                )
            }.andExpect {
                status { isOk() }
                jsonPath("$.name") { value("Product 1") }
                jsonPath("$.barCode") { value("20245918") }
            }

            val fromDb = productRepository.findById(saved.id!!).get()
            fromDb.name shouldBe "Product 1"
            fromDb.barCode shouldBe "20245918"
        }

        @Test
        fun `returns 404 when product not found`() {
            mockMvc.patch("/api/v1/products/${java.util.UUID.randomUUID()}") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(
                    UpdateProductDTO(name = "Product 1", barCode = "20245918")
                )
            }.andExpect {
                status { isNotFound() }
                jsonPath("$.status") { value(404) }
            }
        }

        @Test
        fun `returns 409 when barcode belongs to another product`() {
            saveProduct(name = "Product 1", barCode = "8001120896247")
            val product2 = saveProduct(name = "Product 2", barCode = "20245918")

            mockMvc.patch("/api/v1/products/${product2.id}") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(
                    UpdateProductDTO(name = "Product 2 updated", barCode = "8001120896247")
                )
            }.andExpect {
                status { isConflict() }
                jsonPath("$.status") { value(409) }
            }
        }
    }

    // ── DELETE /api/v1/products/{id} ───────────────────────────────────────────

    @Nested
    inner class DeleteByID {

        @Test
        fun `returns 204 and removes product from db`() {
            val saved = saveProduct()

            mockMvc.delete("/api/v1/products/${saved.id}").andExpect {
                status { isNoContent() }
            }

            productRepository.findById(saved.id!!).isPresent shouldBe false
        }

        @Test
        fun `returns 404 when product not found`() {
            mockMvc.delete("/api/v1/products/${java.util.UUID.randomUUID()}").andExpect {
                status { isNotFound() }
                jsonPath("$.status") { value(404) }
            }
        }
    }
}