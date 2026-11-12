package cloud.mallne.dicentra.aviator.orchestration.arazzo

import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Compile real DSL call sites, rather than approximating receiver resolution at runtime. */
class OrchestrationDslScopeCompileTest {
    @Test
    fun explicitOuterReceiversCompile() {
        val result = compile("""
            fun configure(converter: APIToServiceConverter, api: OpenApiDoc) = orchestrator {
                plugins {
                    this@orchestrator.logger = null
                    install("example") { this@orchestrator.logger = null }
                }
                aviator(converter, api) { this@orchestrator.logger = null }
                arazzo { this@orchestrator.logger = null }
            }
        """)
        assertEquals(ExitCode.OK, result.first, result.second)
    }

    @Test
    fun implicitOuterReceiversAreRejectedInEveryNestedScope() {
        val result = compile("""
            fun configure(converter: APIToServiceConverter, api: OpenApiDoc) = orchestrator {
                plugins {
                    logger = null
                    install("example") { plugins {} }
                }
                aviator(converter, api) { logger = null }
                arazzo { logger = null }
            }
        """)
        assertEquals(ExitCode.COMPILATION_ERROR, result.first, result.second)
        val violations = result.second.lineSequence().count { "[DSL_SCOPE_VIOLATION]" in it }
        assertEquals(4, violations, result.second)
        assertTrue("UNRESOLVED_REFERENCE" !in result.second, result.second)
    }

    private fun compile(body: String): Pair<ExitCode, String> {
        val directory = Files.createTempDirectory("orchestration-dsl-compile-")
        try {
            val source = directory.resolve("Scope.kt")
            Files.writeString(source, """
                import cloud.mallne.dicentra.aviator.core.APIToServiceConverter
                import cloud.mallne.dicentra.aviator.orchestration.execution.orchestrator
                import cloud.mallne.dicentra.aviator.orchestration.arazzo.execution.arazzo
                import io.ktor.openapi.OpenApiDoc
                $body
            """.trimIndent())
            val output = ByteArrayOutputStream()
            val result = PrintStream(output).use { stream ->
                K2JVMCompiler().exec(stream,
                    "-no-stdlib", "-no-reflect", "-Xrender-internal-diagnostic-names",
                    "-classpath", checkNotNull(System.getProperty("orchestration.compileTest.classpath")),
                    "-d", directory.resolve("classes").toString(), source.toString())
            }
            return result to output.toString(Charsets.UTF_8.name())
        } finally {
            Files.walk(directory).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }
}
