package com.questmodder.app

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageInstaller
import android.graphics.Color
import android.view.View
import android.widget.*
import java.io.File
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry

class MainActivity : Activity() {
    private data class ModFile(val uri: Uri, val name: String, val destination: String, val detail: String)

    private var apkUri: Uri? = null
    private var apkName = "No APK selected"
    private val mods = mutableListOf<ModFile>()
    private var signedApk: File? = null
    private lateinit var status: TextView
    private lateinit var apkLabel: TextView
    private lateinit var modList: TextView
    private lateinit var entryList: LinearLayout

    private val pickApk = 10
    private val pickMods = 11
    private val savePatched = 12
    private val saveExtracted = 13
    private var pendingEntry = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(18, 18, 18)
        window.navigationBarColor = Color.rgb(18, 18, 18)
        buildUi()
    }

    private fun buildUi() {
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(18, 18, 18)) }
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(22, 28, 22, 28) }
        scroll.addView(page); setContentView(scroll)
        page.addView(text("QUEST MODDER", 28, Color.WHITE, true))
        page.addView(text("APK PATCHER  •  SIGNER  •  EXTRACTOR", 11, 0xFFBBA5FF.toInt()))
        page.addView(space(18))

        section(page, "1  •  GAME APK")
        apkLabel = text(apkName, 14, 0xFFDDDDDD.toInt()); page.addView(apkLabel)
        page.addView(button("SELECT APK", 0xFF51436F.toInt()) { pick("application/vnd.android.package-archive", pickApk, false) })
        page.addView(space(12))

        section(page, "2  •  MOD FILE ADDER")
        page.addView(text("Choose multiple mods. Destination paths are prepared for LemonLoader's copyToBase convention.", 12, 0xFFAAAAAA.toInt()))
        page.addView(button("UPLOAD MOD FILES", 0xFF51436F.toInt()) { pick("*/*", pickMods, true) })
        modList = text("No mod files selected.", 12, 0xFFDDDDDD.toInt()); page.addView(modList)
        page.addView(button("PATCH + SIGN APK", 0xFF666666.toInt()) { patchAndSign() })
        page.addView(button("INSTALL SIGNED APK", 0xFF51436F.toInt()) { installSignedApk() })
        page.addView(space(12))

        section(page, "3  •  APK EXTRACTOR")
        page.addView(button("SCAN APK CONTENTS", 0xFF51436F.toInt()) { scanApk() })
        entryList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; page.addView(entryList)
        page.addView(space(12))

        section(page, "4  •  LOADER STATUS")
        page.addView(text(
            "Managed .dll files are packaged to assets/copyToBase/Mods/. Native .so files are staged to assets/copyToBase/Plugins/. LemonLoader copies these into its per-game folders when the game is already patched with LemonLoader. Staging a .so does not inject or load it automatically.",
            12, 0xFFBBBBBB.toInt()
        ))
        page.addView(space(10))
        status = text("Ready. Select an APK.", 13, 0xFFBBA5FF.toInt()); page.addView(status)
        page.addView(space(14))
        page.addView(text("QUEST MODDER 0.3.0 • ON-DEVICE BUILD", 10, 0xFF777777.toInt()))
    }

    private fun section(parent: LinearLayout, title: String) { parent.addView(text(title, 13, 0xFFBBA5FF.toInt(), true)); parent.addView(space(6)) }
    private fun text(value: String, size: Int, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size.toFloat(); setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, 4, 0, 4)
    }
    private fun space(h: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, h) }
    private fun button(label: String, color: Int, action: () -> Unit) = Button(this).apply {
        text = label; setTextColor(Color.WHITE); setBackgroundColor(color); isAllCaps = false
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(-1, 52).apply { topMargin = 4; bottomMargin = 4 }
    }
    private fun pick(mime: String, code: Int, multi: Boolean) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = mime
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multi)
        }
        startActivityForResult(intent, code)
    }

    @Deprecated("Legacy activity result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        when (requestCode) {
            pickApk -> data.data?.let { apkUri = it; apkName = displayName(it); apkLabel.text = apkName; scanApk() }
            pickMods -> {
                val uris = mutableListOf<Uri>()
                data.clipData?.let { clip -> for (i in 0 until clip.itemCount) uris.add(clip.getItemAt(i).uri) }
                if (uris.isEmpty()) data.data?.let { uris.add(it) }
                uris.forEach { uri ->
                    val name = displayName(uri); val (dest, detail) = destinationFor(name)
                    mods.removeAll { it.name == name }; mods.add(ModFile(uri, name, dest, detail))
                }
                refreshMods(); status.text = "${mods.size} mod(s) queued."
            }
            saveExtracted -> data.data?.let { exportEntry(it, pendingEntry) }
            14 -> data.data?.let { outputUri ->
                val source = pendingSignedForSave
                if (source == null) {
                    status.text = "No signed APK is waiting to be saved."
                } else {
                    try {
                        contentResolver.openOutputStream(outputUri, "w")!!.use { out ->
                            source.inputStream().use { input -> input.copyTo(out) }
                        }
                        status.text = "Signed APK exported successfully. You can now install it from your file manager."
                    } catch (e: Exception) {
                        status.text = "Export failed: ${e.message}"
                    } finally {
                        pendingSignedForSave = null
                    }
                }
            }
        }
    }

    private fun destinationFor(name: String): Pair<String, String> {
        val file = name.substringAfterLast('/')
        return when {
            name.endsWith(".dll", true) -> "assets/copyToBase/Mods/$file" to "LemonLoader managed mod staging"
            name.endsWith(".so", true) -> "assets/copyToBase/Plugins/$file" to "Native plugin staging; loader support is game-specific"
            else -> "assets/copyToBase/QuestModder/$file" to "Generic copyToBase data file"
        }
    }

    private fun refreshMods() {
        modList.text = if (mods.isEmpty()) "No mod files selected." else mods.joinToString("\n\n") {
            "• ${it.name}\n  → ${it.destination}\n  ${it.detail}"
        }
    }

    private fun displayName(uri: Uri): String {
        contentResolver.query(uri, null, null, null, null)?.use {
            val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && it.moveToFirst()) return it.getString(index) ?: "file"
        }
        return uri.lastPathSegment ?: "file"
    }

    private fun scanApk() {
        val source = apkUri ?: run { status.text = "Select an APK first."; return }
        try {
            val names = mutableListOf<String>()
            ZipInputStream(contentResolver.openInputStream(source)).use { zin ->
                while (true) { val e = zin.nextEntry ?: break; if (!e.isDirectory) names.add(e.name); zin.closeEntry() }
            }
            entryList.removeAllViews()
            names.take(250).forEach { name ->
                val row = button(name, 0xFF303030.toInt()) {
                    pendingEntry = name
                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE); type = "application/octet-stream"
                        putExtra(Intent.EXTRA_TITLE, name.substringAfterLast('/'))
                    }
                    startActivityForResult(intent, saveExtracted)
                }
                row.textSize = 11f; entryList.addView(row)
            }
            status.text = "Found ${names.size} entries. Tap one to export it."
        } catch (e: Exception) { status.text = "Scan failed: ${e.message}" }
    }

    private fun patchAndSign() {
        val source = apkUri ?: run { status.text = "Select an APK first."; return }
        if (mods.isEmpty()) { status.text = "Upload one or more mod files first."; return }
        try {
            val unsigned = File(cacheDir, "questmodder-unsigned.apk")
            val signed = File(cacheDir, "questmodder-signed.apk")
            val payloads = mods.associate { it.destination to readBytes(it.uri) }
            val replaced = payloads.keys
            val seen = HashSet<String>()
            ZipOutputStream(unsigned.outputStream().buffered()).use { out ->
                ZipInputStream(contentResolver.openInputStream(source)).use { zin ->
                    while (true) {
                        val entry = zin.nextEntry ?: break
                        val name = entry.name
                        val sig = name.startsWith("META-INF/", true) &&
                            (name.endsWith(".SF", true) || name.endsWith(".RSA", true) ||
                             name.endsWith(".DSA", true) || name.equals("META-INF/MANIFEST.MF", true))
                        if (!entry.isDirectory && name !in replaced && !sig && seen.add(name)) {
                            out.putNextEntry(ZipEntry(name)); zin.copyTo(out); out.closeEntry()
                        }
                        zin.closeEntry()
                    }
                }
                payloads.forEach { (path, bytes) ->
                    if (seen.add(path)) { out.putNextEntry(ZipEntry(path)); out.write(bytes); out.closeEntry() }
                }
            }
            SigningManager(this).sign(unsigned, signed)
            signedApk = signed
            val export = File(cacheDir, "questmodder-signed-export.apk")
            signed.copyTo(export, overwrite = true)
            // Ask user to choose a destination for the signed APK using SAF.
            val save = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_TITLE, "questmodder_${apkName.substringAfterLast('/')}")
            }
            startActivityForResult(save, 14)
            pendingSignedForSave = signed
            status.text = "APK patched and locally signed. Choose a location to export it."
        } catch (e: Exception) { status.text = "Patch/sign failed: ${e.message}" }
    }

    private var pendingSignedForSave: File? = null

    override fun onResume() { super.onResume() }

    private fun readBytes(uri: Uri): ByteArray =
        contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Cannot read file")

    private fun exportEntry(output: Uri, name: String) {
        val source = apkUri ?: return
        try {
            var found = false
            ZipInputStream(contentResolver.openInputStream(source)).use { zin ->
                while (true) {
                    val e = zin.nextEntry ?: break
                    if (e.name == name) {
                        contentResolver.openOutputStream(output, "w")!!.use { zin.copyTo(it) }
                        found = true; break
                    }
                    zin.closeEntry()
                }
            }
            status.text = if (found) "Exported $name" else "Entry not found."
        } catch (e: Exception) { status.text = "Export failed: ${e.message}" }
    }

    private fun installSignedApk() {
        val apk = signedApk ?: run { status.text = "Patch and sign an APK first."; return }
        try {
            val installer = packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                session.openWrite("base.apk", 0, apk.length()).use { out -> apk.inputStream().use { it.copyTo(out) } }
                val intent = Intent(this, InstallResultReceiver::class.java)
                val pending = android.app.PendingIntent.getBroadcast(
                    this, sessionId, intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE
                )
                session.commit(pending.intentSender)
            }
            status.text = "Install request sent. Confirm the Android installation prompt."
        } catch (e: Exception) { status.text = "Install failed: ${e.message}" }
    }
}
