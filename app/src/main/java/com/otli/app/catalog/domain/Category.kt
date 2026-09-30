package com.otli.app.catalog.domain

/** A grouping of products inside one merchant's catalog. */
data class Category(val id: String, val name: String, val sortOrder: Int)
