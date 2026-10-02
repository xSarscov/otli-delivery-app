package com.otli.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class GenerateDebugNetworkSecurityConfig : DefaultTask() {
    @get:Input
    abstract val emulatorHost: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val dir = outputDir.get().asFile.resolve("xml").apply { mkdirs() }
        dir.resolve("network_security_config.xml").writeText(DebugNetworkSecurityConfig.xml(emulatorHost.get()))
    }
}
