package com.warehouseservice.unit.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.warehouseservice.controllers.ProductController
import com.warehouseservice.exceptions.ProductNotFoundException
import com.warehouseservice.exceptions.ProductWithBarCodeAlreadyExistsException
import com.warehouseservice.models.dtos.*
import com.warehouseservice.services.ProductService
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.*
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.Instant
import java.util.*

@WebMvcTest(ProductController::class)
class ProductControllerUnit {

    @Autowired
    lateinit var mockMvc: MockMvc

    private val objectMapper = jacksonObjectMapper()

    @MockkBean
    lateinit var productService: ProductService

    // ── fixture ────────────────────────────────────────────────────────────────

    private val fixedId = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private val fixedNow = Instant.parse("2026-03-31T19:00:00Z")

    private fun makeProductDTO(id: UUID = fixedId, name: String = "Product 1", barCode: String = "8001120896247") =
        ProductDTO(
            id = id,
            name = name,
            barCode = barCode,
            createdAt = fixedNow,
            updatedAt = fixedNow,
        )

    private fun makeProductPageDTO(products: List<ProductDTO> = listOf(makeProductDTO())): ProductPageDTO {
        val sortInfo = SortInfo(empty = false, sorted = true, unsorted = false)
        val pageableInfo = PageableInfo(
            pageNumber = 0,
            pageSize = 20,
            sort = sortInfo,
            offset = 0,
            paged = true,
            unpaged = false,
        )

        return ProductPageDTO(
            content = products,
            pageable = pageableInfo,
            totalElements = products.size.toLong(),
            totalPages = 1,
            last = true,
            size = 20,
            number = 0,
            sort = sortInfo,
            first = true,
            numberOfElements = products.size,
            empty = products.isEmpty(),
        )
    }

    // ── GET /api/v1/products ───────────────────────────────────────────────────

    @Nested
    inner class FindAll {

        @Test
        fun `returns 200 with paged products`() {
            val pageDTO = makeProductPageDTO()
            every { productService.findAll(any(), any(), any(), any(), any()) } returns pageDTO

            mockMvc.get("/api/v1/products").andExpect {
                status { isOk() }
                content { contentType(MediaType.APPLICATION_JSON) }
                jsonPath("$.totalElements") { value(1) }
                jsonPath("$.content[0].name") { value("Product 1") }
                jsonPath("$.content[0].barCode") { value("8001120896247") }
            }
        }

        @Test
        fun `returns 200 with empty page when no products match`() {
            val emptyPage = makeProductPageDTO(emptyList())
            every { productService.findAll(any(), any(), any(), any(), any()) } returns emptyPage

            mockMvc.get("/api/v1/products") {
                param("name", "nonexistent")
            }.andExpect {
                status { isOk() }
                jsonPath("$.totalElements") { value(0) }
                jsonPath("$.empty") { value(true) }
            }
        }

        @Test
        fun `passes query params correctly to service`() {
            val pageDTO = makeProductPageDTO()
            every { productService.findAll(1, 10, "product", "800112", "name-desc") } returns pageDTO

            mockMvc.get("/api/v1/products") {
                param("page", "1")
                param("size", "10")
                param("name", "product")
                param("barCode", "800112")
                param("sort", "name-desc")
            }.andExpect {
                status { isOk() }
            }

            verify { productService.findAll(1, 10, "product", "800112", "name-desc") }
        }
    }

    // ── GET /api/v1/products/{id} ──────────────────────────────────────────────

    @Nested
    inner class FindByID {

        @Test
        fun `returns 200 with product when found`() {
            val dto = makeProductDTO()
            every { productService.findByID(fixedId) } returns dto

            mockMvc.get("/api/v1/products/$fixedId").andExpect {
                status { isOk() }
                content { contentType(MediaType.APPLICATION_JSON) }
                jsonPath("$.id") { value(fixedId.toString()) }
                jsonPath("$.name") { value("Product 1") }
                jsonPath("$.barCode") { value("8001120896247") }
            }
        }

        @Test
        fun `returns 404 when product not found`() {
            val randomId = UUID.randomUUID()
            every { productService.findByID(randomId) } throws ProductNotFoundException("Product with id $randomId not found")

            mockMvc.get("/api/v1/products/$randomId").andExpect {
                status { isNotFound() }
                jsonPath("$.status") { value(404) }
                jsonPath("$.message") { value("Product with id $randomId not found") }
            }
        }
    }

    // ── POST /api/v1/products ──────────────────────────────────────────────────

    @Nested
    inner class Create {

        @Test
        fun `returns 201 with created product`() {
            val dto = makeProductDTO()
            every { productService.create(any()) } returns dto

            mockMvc.post("/api/v1/products") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(
                    CreateProductDTO(name = "Product 1i", barCode = "8001120896247")
                )
            }.andExpect {
                status { isCreated() }
                jsonPath("$.id") { value(fixedId.toString()) }
                jsonPath("$.name") { value("Product 1") }
            }
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
        }

        @Test
        fun `returns 400 when barcode has invalid format`() {
            mockMvc.post("/api/v1/products") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(
                    mapOf("name" to "Product 1", "barCode" to "123")
                )
            }.andExpect {
                status { isBadRequest() }
            }
        }

        @Test
        fun `returns 409 when barcode already exists`() {
            every { productService.create(any()) } throws
                    ProductWithBarCodeAlreadyExistsException("Product with bar code 8001120896247 already exists")

            mockMvc.post("/api/v1/products") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(
                    CreateProductDTO(name = "Product 1", barCode = "8001120896247")
                )
            }.andExpect {
                status { isConflict() }
                jsonPath("$.status") { value(409) }
                jsonPath("$.message") { value("Product with bar code 8001120896247 already exists") }
            }
        }
    }

    // ── PATCH /api/v1/products/{id} ────────────────────────────────────────────

    @Nested
    inner class Update {

        @Test
        fun `returns 200 with updated product`() {
            val dto = makeProductDTO(name = "Product 1", barCode = "20245918")
            every { productService.update(fixedId, any()) } returns dto

            mockMvc.patch("/api/v1/products/$fixedId") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(
                    UpdateProductDTO(name = "Product 1", barCode = "20245918")
                )
            }.andExpect {
                status { isOk() }
                jsonPath("$.name") { value("Product 1") }
                jsonPath("$.barCode") { value("20245918") }
            }
        }

        @Test
        fun `returns 404 when product not found`() {
            val randomId = UUID.randomUUID()
            every { productService.update(randomId, any()) } throws
                    ProductNotFoundException("Product with id $randomId not found")

            mockMvc.patch("/api/v1/products/$randomId") {
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
        fun `returns 409 when barcode already exists`() {
            every { productService.update(fixedId, any()) } throws
                    ProductWithBarCodeAlreadyExistsException("Product with bar code 20245918 already exists")

            mockMvc.patch("/api/v1/products/$fixedId") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(
                    UpdateProductDTO(name = "Product 1", barCode = "20245918")
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
        fun `returns 204 when product deleted successfully`() {
            every { productService.deleteByID(fixedId) } returns Unit

            mockMvc.delete("/api/v1/products/$fixedId").andExpect {
                status { isNoContent() }
            }

            verify { productService.deleteByID(fixedId) }
        }

        @Test
        fun `returns 404 when product not found`() {
            val randomId = UUID.randomUUID()
            every { productService.deleteByID(randomId) } throws
                    ProductNotFoundException("Product with id $randomId not found")

            mockMvc.delete("/api/v1/products/$randomId").andExpect {
                status { isNotFound() }
                jsonPath("$.status") { value(404) }
            }
        }
    }
}