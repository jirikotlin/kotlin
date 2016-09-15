/*
 * Copyright 2010-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kotlin.reflect.jvm.internal

import org.jetbrains.kotlin.load.java.structure.reflect.wrapperByPrimitive
import java.lang.reflect.Proxy
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KotlinReflectionInternalError
import java.lang.reflect.Method as ReflectMethod

internal class AnnotationConstructorCaller(
        private val jClass: Class<*>,
        private val parameterNames: List<String>,
        private val areOptionalArgumentsAllowed: Boolean,
        origin: Origin,
        private val methods: List<ReflectMethod> = parameterNames.map { name -> jClass.getDeclaredMethod(name) }
) : FunctionCaller<Nothing?>(
        null, jClass, null, methods.map { it.genericReturnType }.toTypedArray() // TODO: test javaType for annotation constructor parameter
) {
    enum class Origin { JAVA, KOTLIN }

    // Transform primitive int to java.lang.Integer because actual arguments passed here will be boxed and Class#isInstance should succeed
    private val erasedParameterTypes: List<Class<*>> = methods.map { method -> method.returnType.let { it.wrapperByPrimitive ?: it } }

    // TODO: get rid of transformation Class -> KClass here and back in call
    private val defaultValues: List<Any?> = methods.map { method -> method.defaultValue.let { (it as? Class<*>)?.kotlin ?: it } }

    init {
        // TODO: consider lifting this restriction once KT-8957 is implemented
        if (!areOptionalArgumentsAllowed && origin == Origin.JAVA && (parameterNames - "value").isNotEmpty()) {
            throw UnsupportedOperationException(
                    "Positional call of a Java annotation constructor is allowed only if there are no parameters " +
                    "or one parameter named \"value\". This restriction exists because Java annotations (in contrast to Kotlin)" +
                    "do not impose any order on their arguments. Use KCallable#callBy instead."
            )
        }
    }

    override fun call(args: Array<*>): Any? {
        // TODO: test?
        checkArguments(args)

        val values = args.mapIndexed { index, arg ->
            val value = if (arg == null && areOptionalArgumentsAllowed) defaultValues[index] else arg
            val transformed = value.transformKotlinToJvm(erasedParameterTypes[index])
            transformed ?: throwIllegalArgumentType(index, parameterNames[index], erasedParameterTypes[index])
        }

        return createAnnotationInstance(jClass, methods, parameterNames.zip(values).toMap())
    }
}

/**
 * Transforms a Kotlin value to the one required by the JVM, e.g. KClass<*> -> Class<*> or Array<KClass<*>> -> Array<Class<*>>.
 * Returns `null` in case when no transformation is possible (an argument of an incorrect type was passed).
 */
private fun Any?.transformKotlinToJvm(expectedType: Class<*>): Any? {
    // TODO: Array<KClass<*>> -> Array<Class<*>>, add test
    val result = when (this) {
        is Class<*> -> return null
        is KClass<*> -> this.java
        else -> this
    }

    return if (expectedType.isInstance(result)) result else null
}

private fun throwIllegalArgumentType(index: Int, name: String, expectedJvmType: Class<*>): Nothing {
    // TODO: message should read "... of the required type kotlin.reflect.KClass<...>" when erased type = Class<*>
    throw IllegalArgumentException("Argument #$index $name is not of the required type ${expectedJvmType.kotlin.qualifiedName}")
}

private fun createAnnotationInstance(annotationClass: Class<*>, methods: List<ReflectMethod>, values: Map<String, Any>): Any {
    // TODO: test double NaN, float NaN and arrays of those: both equals and hashCode
    fun equals(other: Any?): Boolean =
            (other as? Annotation)?.annotationClass?.java == annotationClass &&
            methods.all { method ->
                val ours = values[method.name]
                val theirs = method(other)
                @Suppress("UNCHECKED_CAST")
                when (ours) {
                    is BooleanArray -> Arrays.equals(ours, theirs as BooleanArray)
                    is CharArray -> Arrays.equals(ours, theirs as CharArray)
                    is ByteArray -> Arrays.equals(ours, theirs as ByteArray)
                    is ShortArray -> Arrays.equals(ours, theirs as ShortArray)
                    is IntArray -> Arrays.equals(ours, theirs as IntArray)
                    is FloatArray -> Arrays.equals(ours, theirs as FloatArray)
                    is LongArray -> Arrays.equals(ours, theirs as LongArray)
                    is DoubleArray -> Arrays.equals(ours, theirs as DoubleArray)
                    else -> when {
                        ours is Array<*> && ours.isArrayOf<String>() -> Arrays.equals(ours as Array<String>, theirs as Array<String>)
                        else -> ours == theirs
                    }
                }
            }

    val hashCode by lazy {
        values.entries.sumBy { entry ->
            // TODO: use correct hash code for Float, Double and arrays
            127 * entry.key.hashCode() xor entry.value.hashCode()
        }
    }

    // TODO: test toString on many arguments
    val toString by lazy {
        buildString {
            append('@')
            append(annotationClass.canonicalName)
            values.entries.joinTo(this, separator = ", ", prefix = "(", postfix = ")") { entry ->
                // TODO: use correct toString for Float, Double and arrays; test
                "${entry.key}=${entry.value}"
            }
        }
    }

    return Proxy.newProxyInstance(annotationClass.classLoader /* TODO: test */, arrayOf(annotationClass)) { proxy, method, args ->
        val name = method.name
        when (name) {
            "annotationType" -> annotationClass
            "toString" -> toString
            "hashCode" -> hashCode
            else -> when {
                name == "equals" && args?.size == 1 -> equals(args.single())
                values.containsKey(name) -> values[name]
                else -> throw KotlinReflectionInternalError("Method is not supported: $method (args: ${args.orEmpty().toList()})")
            }
        }
    }
}
