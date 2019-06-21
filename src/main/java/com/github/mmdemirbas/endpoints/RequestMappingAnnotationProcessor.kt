package com.github.mmdemirbas.endpoints

import org.springframework.web.bind.annotation.RequestMapping
import spoon.Launcher
import spoon.compiler.SpoonFolder
import spoon.compiler.SpoonResource
import spoon.processing.AbstractAnnotationProcessor
import spoon.reflect.declaration.CtClass
import spoon.reflect.declaration.CtMethod
import spoon.support.compiler.FileSystemFile
import spoon.support.compiler.FileSystemFolder
import java.io.File

/**
 * @author Muhammed Demirbaş
 * @since 2019-06-18 10:46
 */
class RequestMappingAnnotationProcessor(private val endpoints: MutableList<Endpoint>, private val filePath: String) :
        AbstractAnnotationProcessor<RequestMapping, CtMethod<*>>() {

    override fun process(methodAnnotation: RequestMapping, javaMethod: CtMethod<*>) {
        val classAnnotation = javaMethod.getParent(CtClass::class.java)?.getAnnotation(RequestMapping::class.java)
        val annotations = listOf(classAnnotation, methodAnnotation).filterNotNull()

        val httpPaths = annotations.map { (it.path + it.value).toList() }.fold(listOf(""), { acc, paths ->
            when {
                paths.isEmpty() -> acc
                else            -> cartesianProduct(acc, paths, String::plus)
            }
        })
        val httpMethods = annotations.map { it.method.toList() }.reduce { acc, methods ->
            when {
                methods.isEmpty() -> acc
                else              -> methods
            }
        }

        val className = javaMethod.declaringType.qualifiedName
        val methodName = javaMethod.simpleName
        val javaMethodString = "$className#$methodName"

        endpoints += cartesianProduct(httpMethods, httpPaths) { method, path ->
            Endpoint(httpMethod = method.toString(),
                     httpPath = path,
                     filePath = filePath,
                     javaMethod = javaMethodString)
        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            reportEndpoints(args.map { path ->
                val file = File(path)
                when {
                    file.isDirectory -> FileSystemFolder(file)
                    else             -> FileSystemFile(file)
                }
            })
        }

        fun reportEndpoints(resources: List<SpoonResource>) {
            val endpoints = findEndpoints(resources)

            val maxIndexLen = endpoints.size.toString().length
            val maxPathLen = endpoints.map { it.httpPath.length }.max() ?: 0
            val maxMethodLen = endpoints.map { it.httpMethod.length }.max() ?: 0
            val maxJavaMethodLen = endpoints.map { it.javaMethod.length }.max() ?: 0

            endpoints.sortedBy { it.httpMethod }.sortedBy { it.httpPath }
                .forEachIndexed { index, (httpMethod, httpPath, filePath, javaMethod) ->
                    val indexPadded = (index + 1).toString().padStart(maxIndexLen)
                    val pathPadded = httpPath.padEnd(maxPathLen)
                    val methodPadded = httpMethod.padEnd(maxMethodLen)
                    val javaMethodPadded = javaMethod.padEnd(maxJavaMethodLen)
                    println("[$indexPadded] $pathPadded $methodPadded = $javaMethodPadded ($filePath)")
                }
        }

        private fun findEndpoints(resources: List<SpoonResource>): List<Endpoint> {
            // process resources one-by-one for performance reasons, also to allow classes with duplicate names.
            val endpoints = mutableListOf<Endpoint>()
            resources.forEach { resource ->
                val individualResources = when (resource) {
                    is SpoonFolder -> resource.allJavaFiles
                    else           -> listOf(resource)
                }
                individualResources.forEach { res ->
                    val resourcePath = res.path
                    println("${endpoints.size} endpoints so far. Processing $resourcePath")
                    Launcher().run {
                        addInputResource(res)
                        buildModel().processWith(RequestMappingAnnotationProcessor(endpoints, resourcePath))
                    }
                }
            }
            return endpoints
        }
    }
}

data class Endpoint(val httpMethod: String, val httpPath: String, val filePath: String, val javaMethod: String)

fun <X, Y, R> cartesianProduct(xs: List<X>, ys: List<Y>, combine: (X, Y) -> R): List<R> =
        xs.flatMap { x -> ys.map { y -> combine(x, y) } }