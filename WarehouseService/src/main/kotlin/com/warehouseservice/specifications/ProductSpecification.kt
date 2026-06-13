package com.warehouseservice.specifications

import com.warehouseservice.models.entities.Product
import jakarta.persistence.criteria.Predicate
import org.springframework.data.jpa.domain.Specification

object ProductSpecifications {
    fun withFilters(name: String?, barCode: String?): Specification<Product> {
        return Specification { root, _, cb ->
            val predicates = mutableListOf<Predicate>()

            barCode?.takeIf { it.isNotBlank() }?.let {
                predicates.add(cb.like(root.get("barCode"), "%${it.trim()}%"))
            }

            name?.takeIf { it.isNotBlank() }?.let {
                val tokens = it.trim().split("\\s+".toRegex())
                val namePredicates = tokens.map { token ->
                    cb.like(
                        cb.lower(root.get("name")),
                        "%${token.lowercase()}%"
                    )
                }

                predicates.add(cb.and(*namePredicates.toTypedArray()))
            }

            // maybe add Elasticsearch

            if(predicates.isEmpty()) cb.conjunction() else cb.and(*predicates.toTypedArray())
        }
    }
}