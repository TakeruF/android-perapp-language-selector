package dev.takeru.perapplocale.probe

import android.os.LocaleList
import dev.takeru.perapplocale.core.LocaleGateway
import dev.takeru.perapplocale.core.ProcessGateway
import dev.takeru.perapplocale.core.SystemBinder

/**
 * On-device verification harness for [LocaleGateway]. Debug variant only; never shipped.
 *
 * [LocaleGateway] normally routes through Shizuku so that the system sees uid 2000. This runs the
 * *same* class directly under that uid instead, via `app_process` from an adb shell:
 *
 * ```
 * adb push app-debug.apk /data/local/tmp/probe.apk
 * adb shell CLASSPATH=/data/local/tmp/probe.apk app_process /system/bin \
 *     --nice-name=locale-probe dev.takeru.perapplocale.probe.LocaleGatewayProbe <package>
 * ```
 *
 * That exercises the real reflection path *and* the real raw-transaction path against the real
 * `LocaleManagerService`, which is the only way to confirm the hard-coded transaction ids and the
 * parcel layout are right on a given API level. Shizuku is not involved and does not need to run.
 */
object LocaleGatewayProbe {

    @JvmStatic
    fun main(args: Array<String>) {
        val target = args.firstOrNull() ?: "com.android.settings"

        // Talk to the services directly: this process is already uid 2000.
        LocaleGateway.binderProvider = SystemBinder::raw
        ProcessGateway.binderProvider = SystemBinder::raw

        var failures = 0
        fun check(name: String, block: () -> Unit) {
            try {
                block()
                println("PASS  $name")
            } catch (t: Throwable) {
                failures++
                println("FAIL  $name -> ${t::class.java.name}: ${t.message}")
            }
        }

        println("target=$target uid=${android.os.Process.myUid()}")

        for (viaReflection in listOf(true, false)) {
            val path = if (viaReflection) "reflection" else "raw-transaction"
            LocaleGateway.reflectionUnavailable = !viaReflection
            println("--- $path ---")

            check("$path: reset to system default") {
                LocaleGateway.setApplicationLocales(target, LocaleList.getEmptyLocaleList())
                val got = LocaleGateway.getApplicationLocales(target)
                require(got.isEmpty) { "expected empty, got \"$got\"" }
            }
            check("$path: set zh-CN and read it back") {
                LocaleGateway.setApplicationLocales(target, LocaleList.forLanguageTags("zh-CN"))
                val got = LocaleGateway.getApplicationLocales(target)
                require(got.toLanguageTags() == "zh-CN") { "expected zh-CN, got \"${got.toLanguageTags()}\"" }
            }
            check("$path: agrees with `cmd locale get-app-locales`") {
                val shell = ProcessBuilder("sh", "-c", "cmd locale get-app-locales $target")
                    .redirectErrorStream(true).start()
                val out = shell.inputStream.bufferedReader().readText().trim()
                shell.waitFor()
                require(out.contains("zh-CN")) { "cmd locale disagrees: \"$out\"" }
            }
            check("$path: set ja-JP then reset") {
                LocaleGateway.setApplicationLocales(target, LocaleList.forLanguageTags("ja-JP"))
                require(LocaleGateway.getApplicationLocales(target).toLanguageTags() == "ja-JP")
                LocaleGateway.setApplicationLocales(target, LocaleList.getEmptyLocaleList())
                require(LocaleGateway.getApplicationLocales(target).isEmpty)
            }
        }

        check("forceStopPackage reaches IActivityManager") {
            require(ProcessGateway.forceStop(target)) { "forceStop returned false" }
        }

        println(if (failures == 0) "ALL PASS" else "FAILURES=$failures")
        System.exit(if (failures == 0) 0 else 1)
    }
}
