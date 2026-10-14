plugins {
    kotlin("jvm") version "2.0.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0"
    id("org.jetbrains.compose") version "1.6.11"
}

group = "com.ncr.app"
version = "1.0-SNAPSHOT"


repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    
    // Exposed and SQLite
    val exposedVersion = "0.51.1"
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")
    implementation("org.xerial:sqlite-jdbc:3.46.0.0")
    
    // MS Access Driver
    implementation("net.sf.ucanaccess:ucanaccess:5.0.1")
    
    // MS Word / Apache POI
    implementation("org.apache.poi:poi-ooxml:5.2.3")
    

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    
    // JavaMail API for SMTP
    implementation("com.sun.mail:jakarta.mail:2.0.1")
}

compose.desktop {
    application {
        mainClass = "com.ncr.app.MainKt"
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb)
            packageName = "NCR_App"
            packageVersion = "1.0.0"
            windows {
                iconFile.set(project.file("src/main/resources/icon.ico"))
            }
            buildTypes.release.proguard {
                version.set("7.5.0")
                isEnabled.set(false)
            }
            includeAllModules = true
        }
    }
}

tasks.register<JavaExec>("runExcelScript") {
    mainClass.set("com.ncr.app.ReadExcelKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.matching { it.name == "createReleaseDistributable" }.configureEach {
    doLast {
        val appDir = layout.buildDirectory.dir("compose/binaries/main-release/app/NCR_App").get().asFile
        if (appDir.exists()) {
            copy {
                from(layout.projectDirectory.file("ncr_database.db"))
                into(appDir)
            }
            copy {
                from(layout.projectDirectory.dir("templates"))
                into(File(appDir, "templates"))
            }
            println("Copied database and templates to: ${appDir.absolutePath}")
        }
    }
}
