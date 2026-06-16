package com.warehouseservice.exceptions

class ProductNotFoundException(message: String) : RuntimeException(message)

class ProductWithBarCodeAlreadyExistsException(message: String) : RuntimeException(message)

class InvalidProductStatusException(message: String) : RuntimeException(message)