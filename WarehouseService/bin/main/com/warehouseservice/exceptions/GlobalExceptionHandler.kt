package com.warehouseservice.exceptions

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant

@Schema(description = "Standard error response")
data class ErrorResponse(
    @field:Schema(description = "Timestamp when the error occurred")
    val timestamp: Instant = Instant.now(),

    @field:Schema(description = "HTTP status code")
    val status: Int,

    @field:Schema(description = "HTTP reason phrase")
    val error: String,

    @field:Schema(description = "Human-readable error message")
    val message: String,
)

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(ProductNotFoundException::class)
    fun handleProductNotFound(ex: ProductNotFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity(
            ErrorResponse(
                status = HttpStatus.NOT_FOUND.value(),
                error = HttpStatus.NOT_FOUND.reasonPhrase,
                message = ex.message ?: "Product not found",
            ),
            HttpStatus.NOT_FOUND,
        )

    @ExceptionHandler(ProductWithBarCodeAlreadyExistsException::class)
    fun handleProductWithBarCodeAlreadyExists(ex: ProductWithBarCodeAlreadyExistsException): ResponseEntity<ErrorResponse> =
        ResponseEntity(
            ErrorResponse(
                status = HttpStatus.CONFLICT.value(),
                error = HttpStatus.CONFLICT.reasonPhrase,
                message = ex.message ?: "Product with bar code already exists",
            ),
            HttpStatus.CONFLICT,
        )

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationError(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> =
        ResponseEntity(
            ErrorResponse(
                status = HttpStatus.BAD_REQUEST.value(),
                error = HttpStatus.BAD_REQUEST.reasonPhrase,
                message = ex.bindingResult.fieldErrors.joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
            ),
            HttpStatus.BAD_REQUEST,
        )

    @ExceptionHandler(InvalidProductStatusException::class)
    fun handleInvalidProductStatus(ex: InvalidProductStatusException): ResponseEntity<ErrorResponse> =
        ResponseEntity(
            ErrorResponse(
                status = HttpStatus.UNPROCESSABLE_ENTITY.value(),
                error = HttpStatus.UNPROCESSABLE_ENTITY.reasonPhrase,
                message = ex.message ?: "Invalid product status",
            ),
            HttpStatus.UNPROCESSABLE_ENTITY,
        )
}