package com.spoof.cosa.hook

import android.util.Log
import com.spoof.cosa.common.SpoofConfig
import com.spoof.cosa.data.SpoofSettings
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.lang.reflect.Executable
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

class CosaModule : XposedModule() {

    private val hooksInstalled = AtomicBoolean(false)

    @Volatile
    private var cachedFakePrjname: String = SpoofConfig.defaultFakePrjname

    override fun onPackageReady(param: PackageReadyParam) {
        if (param.packageName != SpoofConfig.targetPackageName || !param.isFirstPackage) return
        if (!hooksInstalled.compareAndSet(false, true)) return

        runCatching {
            initializeSettingsCache()
            installHooks(param.classLoader)
            log(Log.INFO, TAG, "Hooks installed for ${param.packageName}")
        }.onFailure {
            hooksInstalled.set(false)
            log(Log.ERROR, TAG, "Failed to install hooks for ${param.packageName}", it)
        }
    }

    private fun installHooks(classLoader: ClassLoader) {
        val systemPropertiesClass = Class.forName(
            SpoofConfig.systemPropertiesClassName,
            false,
            classLoader
        )
        systemPropertiesClass.declaredMethods
            .asSequence()
            .filter(::isSystemPropertiesCandidate)
            .forEach { hookPrjnameStringMethod(it) }
    }

    private fun initializeSettingsCache() {
        runCatching {
            cachedFakePrjname = SpoofSettings(
                getRemotePreferences(SpoofConfig.remotePrefsGroup)
            ).getFakePrjname()
        }.onFailure {
            cachedFakePrjname = SpoofConfig.defaultFakePrjname
            log(Log.WARN, TAG, "Fallback to default fake prjname", it)
        }
    }

    private fun hookPrjnameStringMethod(method: Method) {
        hook(method).intercept { chain ->
            if (chain.getArg(0) as? String == SpoofConfig.prjnamePropertyKey) {
                cachedFakePrjname
            } else {
                chain.proceed()
            }
        }
        deoptimizeIfPossible(method)
    }

    private fun deoptimizeIfPossible(executable: Executable) {
        runCatching {
            if (!deoptimize(executable)) {
                log(Log.WARN, TAG, "Deoptimize returned false for $executable")
            }
        }.onFailure {
            log(Log.WARN, TAG, "Failed to deoptimize $executable", it)
        }
    }

    private fun isSystemPropertiesCandidate(method: Method): Boolean {
        if (method.name !in SYSTEM_PROPERTIES_METHOD_NAMES) return false
        if (method.returnType != String::class.java) return false
        return hasSupportedStringSignature(method)
    }

    private fun hasSupportedStringSignature(method: Method): Boolean {
        val parameterTypes = method.parameterTypes
        if (parameterTypes.isEmpty() || parameterTypes[0] != String::class.java) return false
        return parameterTypes.size == 1 || (
            parameterTypes.size == 2 && parameterTypes[1] == String::class.java
        )
    }

    companion object {
        private const val TAG = "CosaSpoof"
        private val SYSTEM_PROPERTIES_METHOD_NAMES = setOf("get", "native_get")
    }
}
