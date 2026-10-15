package com.otli.app

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/**
 * ADR-3 guard: `domain/` packages are pure Kotlin. They must not import Android or Firebase, so the
 * business rules stay testable on the JVM and independent of the platform and the backend.
 */
class DomainPurityTest {

    @Test
    fun domainPackagesImportNeitherAndroidNorFirebase() {
        val sources = domainSources()
        // Guard against passing vacuously if the source layout changes.
        assertThat(sources).isNotEmpty()

        val violations = sources.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                if (isForbiddenImport(line)) "${file.path.replace('\\', '/')}:${index + 1}: ${line.trim()}" else null
            }
        }

        assertThat(violations).isEmpty()
    }

    @Test
    fun theGuardRecognizesForbiddenImportsAndNothingElse() {
        val forbidden = listOf(
            "import android.content.Context",
            "import androidx.compose.runtime.Composable",
            "import com.google.firebase.firestore.FirebaseFirestore",
            "  import android.util.Log",
        )
        val allowed = listOf(
            "import kotlin.math.max",
            "import java.time.Instant",
            "import kotlinx.coroutines.flow.Flow",
            "import com.otli.app.core.money.Money",
            "// import android.util.Log",
            "val android = 1",
        )

        assertThat(forbidden.map(::isForbiddenImport)).containsExactly(true, true, true, true)
        assertThat(allowed.map(::isForbiddenImport)).containsExactly(false, false, false, false, false, false)
    }

    private fun isForbiddenImport(line: String): Boolean {
        val trimmed = line.trim()
        return FORBIDDEN_IMPORT_PREFIXES.any { trimmed.startsWith(it) }
    }

    /** Every Kotlin file under a `domain` directory of the main source set (works from `app/` or the repo root). */
    private fun domainSources(): List<File> {
        val root = listOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }
        return root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && "domain" in it.relativeTo(root).path.split(File.separatorChar).dropLast(1) }
            .toList()
    }

    private companion object {
        val FORBIDDEN_IMPORT_PREFIXES = listOf("import android.", "import androidx.", "import com.google.firebase.")
    }
}
